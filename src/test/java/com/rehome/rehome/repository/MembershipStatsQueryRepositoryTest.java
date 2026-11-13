package com.rehome.rehome.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.rehome.rehome.entity.enums.MembershipTier;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Các câu thống kê của W-05 được viết tay bằng {@code em.createQuery(...)}, nên Hibernate chỉ phân
 * tích chúng <b>lúc gọi</b> - khác với {@code @Query} trên repository (được validate ngay lúc khởi
 * tạo bean). Nghĩa là một câu JPQL sai cú pháp ở đây sẽ lọt qua cả {@code contextLoads} và chỉ nổ
 * khi Admin bấm vào màn thống kê.
 *
 * <p>Test này chạy THẬT từng câu trên H2 với DB rỗng. Nó không kiểm tra con số nghiệp vụ (phần đó
 * ở {@code MembershipStatsServiceImpl}), chỉ bảo đảm mọi câu đều parse và chạy được - gồm cả các
 * {@code join} giữa hai entity không có quan hệ khai báo
 * ({@code join MemberVoucher v on v.id = o.voucher.id}), vốn là chỗ dễ sai nhất.
 */
@SpringBootTest
@Transactional
class MembershipStatsQueryRepositoryTest {

    @Autowired
    private MembershipStatsQueryRepository repository;

    private final Instant from = Instant.now().minus(90, ChronoUnit.DAYS);
    private final Instant to = Instant.now();

    @Test
    @DisplayName("Mọi câu thống kê gói đều parse và chạy được trên DB rỗng")
    void membershipQueries_areValidJpql() {
        assertThatCode(() -> {
            assertThat(repository.successfulPayments(from, to)).isEmpty();
            assertThat(repository.activeCountByTier(to)).isEmpty();
            assertThat(repository.notRenewedCount(from, to)).isZero();
            assertThat(repository.voucherCostByTier(from, to)).isEmpty();
            assertThat(repository.liveTryOnUsageByTier(from, to, to)).isEmpty();
            assertThat(repository.aiTryOnUsageByTier(from, to, to)).isEmpty();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Mọi câu thống kê voucher đều parse và chạy được trên DB rỗng")
    void voucherQueries_areValidJpql() {
        assertThatCode(() -> {
            assertThat(repository.voucherCountsByTemplate(from, to)).isEmpty();
            // sum() trên tập rỗng trả null - phải được quy về 0, không được NPE
            assertThat(repository.totalVoucherSpent(from, to)).isEqualByComparingTo(BigDecimal.ZERO);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Gom doanh thu theo tháng dùng giờ VN: mốc 2026-01-01T00:30 giờ VN (17:30 UTC hôm "
            + "trước) phải thuộc tháng 2026-01, không phải 2025-12")
    void monthKey_usesVietnamTimezone() {
        // 2025-12-31T17:30Z = 2026-01-01T00:30 giờ VN
        Instant lateNewYearEve = Instant.parse("2025-12-31T17:30:00Z");
        assertThat(MembershipStatsQueryRepository.monthKey(lateNewYearEve)).isEqualTo("2026-01");
    }

    @Test
    @DisplayName("revenueByMonthAndTier gom đúng theo (tháng, hạng) và cộng dồn nhiều lần mua")
    void revenueByMonthAndTier_groupsAndSums() {
        var rows = java.util.List.of(
                new MembershipStatsQueryRepository.PaymentRow(
                        Instant.parse("2026-03-05T03:00:00Z"), MembershipTier.GOLD, new BigDecimal("59000")),
                new MembershipStatsQueryRepository.PaymentRow(
                        Instant.parse("2026-03-20T03:00:00Z"), MembershipTier.GOLD, new BigDecimal("59000")),
                new MembershipStatsQueryRepository.PaymentRow(
                        Instant.parse("2026-03-21T03:00:00Z"), MembershipTier.SILVER, new BigDecimal("29000")),
                new MembershipStatsQueryRepository.PaymentRow(
                        Instant.parse("2026-04-02T03:00:00Z"), MembershipTier.GOLD, new BigDecimal("59000")));

        var grouped = repository.revenueByMonthAndTier(rows);

        assertThat(grouped.get("2026-03").get(MembershipTier.GOLD)).isEqualByComparingTo("118000");
        assertThat(grouped.get("2026-03").get(MembershipTier.SILVER)).isEqualByComparingTo("29000");
        assertThat(grouped.get("2026-04").get(MembershipTier.GOLD)).isEqualByComparingTo("59000");
        // Thứ tự tháng tăng dần để FE dựng trục thời gian không phải sort lại
        assertThat(grouped.keySet()).containsExactly("2026-03", "2026-04");
    }
}
