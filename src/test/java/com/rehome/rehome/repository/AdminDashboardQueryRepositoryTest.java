package com.rehome.rehome.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.rehome.rehome.entity.enums.ProductStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Các câu của dashboard Admin viết tay bằng {@code em.createQuery(...)} nên Hibernate chỉ phân tích chúng
 * lúc gọi (xem {@code MembershipStatsQueryRepositoryTest}). Test này chạy thật các câu mới trên H2 rỗng để
 * bảo đảm không sai cú pháp và các giá trị rỗng được quy về 0 thay vì null.
 */
@SpringBootTest
@Transactional
class AdminDashboardQueryRepositoryTest {

    @Autowired
    private AdminDashboardQueryRepository repository;

    private final Instant from = Instant.now().minus(90, ChronoUnit.DAYS);
    private final Instant to = Instant.now();

    @Test
    @DisplayName("Tiền đang giữ hộ trên DB rỗng: sum() null được quy về 0, không NPE")
    void heldOrders_emptyDatabase() {
        assertThat(repository.sumHeldOrderAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(repository.countHeldOrders()).isZero();
    }

    @Test
    @DisplayName("Tồn kho theo trạng thái trên DB rỗng: đủ 6 trạng thái tồn kho, đều bằng 0, đúng thứ tự")
    void stockByStatus_emptyDatabase() {
        var byStatus = repository.countStockedProductsByStatus();

        assertThat(byStatus.keySet()).containsExactly(
                ProductStatus.ON_SALE, ProductStatus.RESERVED, ProductStatus.UNCLAIMED,
                ProductStatus.PLATFORM_OWNED, ProductStatus.RETURNED, ProductStatus.WAREHOUSE);
        assertThat(byStatus.values()).containsOnly(0L);
        assertThat(byStatus.values().stream().mapToLong(Long::longValue).sum())
                .isEqualTo(repository.countStockedProducts());
    }

    @Test
    @DisplayName("Bút toán doanh thu nền tảng trên DB rỗng: câu JPQL parse và chạy được")
    void platformRevenueEntries_isValidJpql() {
        assertThatCode(() -> assertThat(repository.findPlatformRevenueEntries(from, to)).isEmpty())
                .doesNotThrowAnyException();
    }
}
