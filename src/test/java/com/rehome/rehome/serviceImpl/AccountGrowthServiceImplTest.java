package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.response.AccountGrowthResponse;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.AdminReportQueryRepository;
import com.rehome.rehome.serviceImpl.AccountGrowthServiceImpl.Granularity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountGrowthServiceImplTest {

    @Mock AdminReportQueryRepository reportRepository;
    @InjectMocks AccountGrowthServiceImpl service;

    // 2026-11-02 là thứ Hai
    private static final LocalDate FROM = LocalDate.of(2026, 11, 2);

    @Test
    @DisplayName("day: đủ mọi ngày, ngày không đăng ký = 0, ngày tính theo giờ VN (17:30Z đã sang ngày hôm sau)")
    void day_fillsEmptyDaysAndUsesVietnamTime() {
        List<Instant> created = List.of(
                Instant.parse("2026-11-02T03:00:00Z"),
                Instant.parse("2026-11-02T17:30:00Z"),   // 00:30 ngày 03 giờ VN
                Instant.parse("2026-11-04T01:00:00Z"));

        AccountGrowthResponse r = AccountGrowthServiceImpl.buildGrowth(
                Granularity.DAY, FROM, FROM.plusDays(3), created);

        assertThat(r.getTotal()).isEqualTo(3);
        assertThat(r.getPoints()).extracting(AccountGrowthResponse.Point::getLabel)
                .containsExactly("2026-11-02", "2026-11-03", "2026-11-04", "2026-11-05");
        assertThat(r.getPoints()).extracting(AccountGrowthResponse.Point::getCount)
                .containsExactly(1L, 1L, 1L, 0L);
    }

    @Test
    @DisplayName("week: tuần ISO bắt đầu thứ Hai, kỳ đầu/cuối được cắt theo [from, to]")
    void week_groupsByIsoWeekAndClipsEdges() {
        // from là thứ Tư 04/11, to là thứ Ba 10/11 -> hai tuần: 02/11-08/11 và 09/11-15/11
        List<Instant> created = List.of(
                Instant.parse("2026-11-04T03:00:00Z"),
                Instant.parse("2026-11-08T03:00:00Z"),
                Instant.parse("2026-11-09T03:00:00Z"));

        AccountGrowthResponse r = AccountGrowthServiceImpl.buildGrowth(
                Granularity.WEEK, LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 10), created);

        assertThat(r.getPoints()).hasSize(2);
        AccountGrowthResponse.Point first = r.getPoints().get(0);
        assertThat(first.getLabel()).isEqualTo("2026-W45");
        assertThat(first.getPeriodStart()).isEqualTo(LocalDate.of(2026, 11, 4));
        assertThat(first.getPeriodEnd()).isEqualTo(LocalDate.of(2026, 11, 8));
        assertThat(first.getCount()).isEqualTo(2);
        AccountGrowthResponse.Point second = r.getPoints().get(1);
        assertThat(second.getLabel()).isEqualTo("2026-W46");
        assertThat(second.getPeriodEnd()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(second.getCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("month: gom theo tháng, tháng không có đăng ký vẫn có điểm count = 0")
    void month_includesEmptyMonths() {
        List<Instant> created = List.of(
                Instant.parse("2026-09-15T03:00:00Z"),
                Instant.parse("2026-11-20T03:00:00Z"));

        AccountGrowthResponse r = AccountGrowthServiceImpl.buildGrowth(
                Granularity.MONTH, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 30), created);

        assertThat(r.getPoints()).extracting(AccountGrowthResponse.Point::getLabel)
                .containsExactly("2026-09", "2026-10", "2026-11");
        assertThat(r.getPoints()).extracting(AccountGrowthResponse.Point::getCount)
                .containsExactly(1L, 0L, 1L);
        assertThat(r.getGranularity()).isEqualTo("month");
    }

    @Test
    @DisplayName("khoảng quá dài (quá 366 kỳ) -> INVALID_DATE_RANGE")
    void tooManyPoints() {
        assertThatThrownBy(() -> AccountGrowthServiceImpl.buildGrowth(
                Granularity.DAY, FROM, FROM.plusDays(400), List.of()))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_DATE_RANGE);
    }

    @Test
    @DisplayName("granularity sai -> 400; không truyền -> day")
    void granularityValidation() {
        assertThatThrownBy(() -> service.growth("2026-11-01", "2026-11-02", "year", null))
                .isInstanceOf(BadRequestException.class);
        assertThat(Granularity.parse(null)).isEqualTo(Granularity.DAY);
        assertThat(Granularity.parse(" Week ")).isEqualTo(Granularity.WEEK);
    }

    @Test
    @DisplayName("growth: truyền khoảng giờ VN [from, to+1) và role xuống repository")
    void growth_passesRangeAndRole() {
        when(reportRepository.findAccountCreatedAt(any(), any(), eq(Role.MEMBER)))
                .thenReturn(List.of(Instant.parse("2026-11-02T03:00:00Z")));

        AccountGrowthResponse r = service.growth("2026-11-02", "2026-11-03", "day", Role.MEMBER);

        assertThat(r.getTotal()).isEqualTo(1);
        assertThat(r.getPoints()).hasSize(2);
    }
}
