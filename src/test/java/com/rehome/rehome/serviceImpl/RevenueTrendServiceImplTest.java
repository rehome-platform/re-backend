package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.response.RevenueTrendResponse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.AdminDashboardQueryRepository;
import com.rehome.rehome.repository.AdminDashboardQueryRepository.RevenueEntry;
import com.rehome.rehome.serviceImpl.AccountGrowthServiceImpl.Granularity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Doanh thu nền tảng theo ngày/tuần/tháng cho biểu đồ ở tab Doanh thu của dashboard Admin. */
@ExtendWith(MockitoExtension.class)
class RevenueTrendServiceImplTest {

    @Mock AdminDashboardQueryRepository queryRepository;
    @InjectMocks RevenueTrendServiceImpl service;

    // 2026-11-02 là thứ Hai
    private static final LocalDate FROM = LocalDate.of(2026, 11, 2);

    private static RevenueEntry entry(String instant, String amount) {
        return new RevenueEntry(Instant.parse(instant), new BigDecimal(amount));
    }

    @Test
    @DisplayName("day: đủ mọi ngày, ngày không có doanh thu = 0, cộng dồn nhiều bút toán, ngày tính theo giờ VN")
    void day_fillsEmptyDaysSumsAndUsesVietnamTime() {
        List<RevenueEntry> entries = List.of(
                entry("2026-11-02T03:00:00Z", "10000"),
                entry("2026-11-02T05:00:00Z", "5000"),
                entry("2026-11-02T17:30:00Z", "7000"),   // 00:30 ngày 03 giờ VN
                entry("2026-11-04T01:00:00Z", "2000"));

        RevenueTrendResponse r = RevenueTrendServiceImpl.buildTrend(
                Granularity.DAY, FROM, FROM.plusDays(3), entries);

        assertThat(r.getGranularity()).isEqualTo("day");
        assertThat(r.getPoints()).extracting(RevenueTrendResponse.Point::getLabel)
                .containsExactly("2026-11-02", "2026-11-03", "2026-11-04", "2026-11-05");
        assertThat(r.getPoints()).extracting(RevenueTrendResponse.Point::getAmount)
                .containsExactly(15000L, 7000L, 2000L, 0L);
        assertThat(r.getTotal()).isEqualTo(24000);
    }

    @Test
    @DisplayName("week: tuần ISO bắt đầu thứ Hai, kỳ đầu/cuối được cắt theo [from, to]")
    void week_groupsByIsoWeekAndClipsEdges() {
        List<RevenueEntry> entries = List.of(
                entry("2026-11-04T03:00:00Z", "1000"),
                entry("2026-11-08T03:00:00Z", "2000"),
                entry("2026-11-09T03:00:00Z", "4000"));

        RevenueTrendResponse r = RevenueTrendServiceImpl.buildTrend(
                Granularity.WEEK, LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 10), entries);

        assertThat(r.getPoints()).hasSize(2);
        RevenueTrendResponse.Point first = r.getPoints().get(0);
        assertThat(first.getLabel()).isEqualTo("2026-W45");
        assertThat(first.getPeriodStart()).isEqualTo(LocalDate.of(2026, 11, 4));
        assertThat(first.getPeriodEnd()).isEqualTo(LocalDate.of(2026, 11, 8));
        assertThat(first.getAmount()).isEqualTo(3000);
        RevenueTrendResponse.Point second = r.getPoints().get(1);
        assertThat(second.getPeriodEnd()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(second.getAmount()).isEqualTo(4000);
    }

    @Test
    @DisplayName("month: gom theo tháng, tháng không có doanh thu vẫn có điểm amount = 0")
    void month_includesEmptyMonths() {
        List<RevenueEntry> entries = List.of(
                entry("2026-09-15T03:00:00Z", "8000"),
                entry("2026-11-20T03:00:00Z", "9000"));

        RevenueTrendResponse r = RevenueTrendServiceImpl.buildTrend(
                Granularity.MONTH, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 30), entries);

        assertThat(r.getPoints()).extracting(RevenueTrendResponse.Point::getLabel)
                .containsExactly("2026-09", "2026-10", "2026-11");
        assertThat(r.getPoints()).extracting(RevenueTrendResponse.Point::getAmount)
                .containsExactly(8000L, 0L, 9000L);
        assertThat(r.getTotal()).isEqualTo(17000);
    }

    @Test
    @DisplayName("khoảng quá dài (quá 366 kỳ) -> INVALID_DATE_RANGE")
    void tooManyPoints() {
        assertThatThrownBy(() -> RevenueTrendServiceImpl.buildTrend(
                Granularity.DAY, FROM, FROM.plusDays(400), List.of()))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_DATE_RANGE);
    }

    @Test
    @DisplayName("granularity sai -> 400")
    void invalidGranularity() {
        assertThatThrownBy(() -> service.trend("2026-11-01", "2026-11-02", "year"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("trend: có from/to -> lấy đúng khoảng đó, kể cả ngày to")
    void trend_withRange() {
        when(queryRepository.findPlatformRevenueEntries(any(), any()))
                .thenReturn(List.of(entry("2026-11-02T03:00:00Z", "6000")));

        RevenueTrendResponse r = service.trend("2026-11-02", "2026-11-03", "day");

        assertThat(r.getFrom()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(r.getTo()).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(r.getPoints()).hasSize(2);
        assertThat(r.getTotal()).isEqualTo(6000);
    }

    @Test
    @DisplayName("trend: bỏ from (toàn thời gian) -> bắt đầu từ ngày có doanh thu đầu tiên theo giờ VN")
    void trend_withoutFromStartsAtFirstRevenueDay() {
        when(queryRepository.findPlatformRevenueEntries(any(), any())).thenReturn(List.of(
                entry("2026-10-31T17:30:00Z", "1000"),   // 00:30 ngày 01/11 giờ VN
                entry("2026-11-03T03:00:00Z", "2000")));

        RevenueTrendResponse r = service.trend(null, "2026-11-05", "day");

        assertThat(r.getFrom()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(r.getTo()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(r.getPoints()).hasSize(5);
        assertThat(r.getTotal()).isEqualTo(3000);
    }

    @Test
    @DisplayName("trend: bỏ from và chưa có doanh thu nào -> 30 ngày gần nhất tính tới to, điểm nào cũng 0")
    void trend_withoutFromAndNoRevenue() {
        when(queryRepository.findPlatformRevenueEntries(any(), any())).thenReturn(List.of());

        RevenueTrendResponse r = service.trend(null, "2026-11-30", "day");

        assertThat(r.getFrom()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(r.getPoints()).hasSize(30);
        assertThat(r.getTotal()).isZero();
    }
}
