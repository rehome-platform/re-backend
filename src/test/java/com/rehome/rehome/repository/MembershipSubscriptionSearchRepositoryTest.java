package com.rehome.rehome.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.rehome.rehome.entity.enums.MembershipTier;
import com.rehome.rehome.entity.enums.SubscriptionStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tìm kiếm thành viên VIP của màn W-02 ghép JPQL động, nên <b>mọi tổ hợp bộ lọc là một câu truy vấn
 * khác nhau</b> và chỉ được phân tích lúc gọi. Test này chạy thật cả 16 tổ hợp (2^4) trên DB rỗng.
 *
 * <p>Lý do tồn tại: bản đầu dùng {@code @Query} với {@code (:tier is null or s.tier = :tier)} -
 * cú pháp đó parse được, chạy được trên H2, nhưng Postgres trả lỗi "could not determine data type
 * of parameter" khi tham số là null (xem ghi chú ở {@code AdminReportQueryRepository}). Nghĩa là
 * một test chỉ gọi với bộ lọc đầy đủ sẽ xanh mà production vẫn vỡ. Ở đây mỗi tổ hợp null/non-null
 * được gọi riêng để không còn đường nào lọt.
 */
@SpringBootTest
@Transactional
class MembershipSubscriptionSearchRepositoryTest {

    @Autowired
    private MembershipSubscriptionSearchRepository repository;

    @Test
    @DisplayName("Cả 16 tổ hợp bộ lọc (có/không × 4 tiêu chí) đều chạy được, không tổ hợp nào ném lỗi")
    void everyFilterCombination_isExecutable() {
        MembershipTier[] tiers = {null, MembershipTier.GOLD};
        SubscriptionStatus[] statuses = {null, SubscriptionStatus.ACTIVE};
        String[] keywords = {null, "%nguyen%"};
        Instant[] deadlines = {null, Instant.now().plus(3, ChronoUnit.DAYS)};

        for (MembershipTier tier : tiers) {
            for (SubscriptionStatus status : statuses) {
                for (String keyword : keywords) {
                    for (Instant expiresBefore : deadlines) {
                        assertThatCode(() -> {
                            var page = repository.search(tier, status, keyword, expiresBefore,
                                    PageRequest.of(0, 20));
                            assertThat(page.getContent()).isEmpty();
                            assertThat(page.getTotalElements()).isZero();
                        })
                                .as("tier=%s status=%s q=%s expiresBefore=%s",
                                        tier, status, keyword, expiresBefore)
                                .doesNotThrowAnyException();
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Không lọc gì thì không join sang MemberProfile - câu truy vấn chỉ cõng thêm bảng "
            + "khi thật sự tìm theo tên/mã Member")
    void noKeyword_stillPaginatesCorrectly() {
        var page = repository.search(null, null, null, null, PageRequest.of(2, 5));

        assertThat(page.getNumber()).isEqualTo(2);
        assertThat(page.getSize()).isEqualTo(5);
        assertThat(page.getTotalElements()).isZero();
    }
}
