package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.response.AccountGrowthResponse;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.AdminReportQueryRepository;
import com.rehome.rehome.service.AccountGrowthService;
import com.rehome.rehome.util.AdminDateRange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class AccountGrowthServiceImpl implements AccountGrowthService {

    // Số kỳ tối đa trong một response, chặn khoảng quá dài làm nặng query và biểu đồ
    static final int MAX_POINTS = 366;
    private static final int DEFAULT_DAYS = 30;
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("yyyy-MM");

    enum Granularity {
        DAY, WEEK, MONTH;

        static Granularity parse(String text) {
            if (text == null || text.isBlank()) {
                return DAY;
            }
            try {
                return valueOf(text.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("granularity phải là day, week hoặc month.");
            }
        }

        // Ngày đầu của kỳ chứa d: chính ngày đó / thứ Hai của tuần / mùng 1 của tháng
        LocalDate startOf(LocalDate d) {
            return switch (this) {
                case DAY -> d;
                case WEEK -> d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                case MONTH -> d.withDayOfMonth(1);
            };
        }

        LocalDate next(LocalDate periodStart) {
            return switch (this) {
                case DAY -> periodStart.plusDays(1);
                case WEEK -> periodStart.plusWeeks(1);
                case MONTH -> periodStart.plusMonths(1);
            };
        }

        String label(LocalDate periodStart) {
            return switch (this) {
                case DAY -> periodStart.toString();
                case WEEK -> periodStart.get(IsoFields.WEEK_BASED_YEAR) + "-W"
                        + String.format("%02d", periodStart.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
                case MONTH -> periodStart.format(MONTH_LABEL);
            };
        }
    }

    private final AdminReportQueryRepository reportRepository;

    @Override
    @Transactional(readOnly = true)
    public AccountGrowthResponse growth(String from, String to, String granularity, Role role) {
        Granularity g = Granularity.parse(granularity);

        AdminDateRange parsed = AdminDateRange.optional(from, to);
        // Bỏ trống thì lấy 30 ngày gần nhất tính tới hết hôm nay (giờ VN)
        LocalDate toDate = parsed.toDate() != null ? parsed.toDate() : LocalDate.now(AdminDateRange.VN_ZONE);
        LocalDate fromDate = parsed.fromDate() != null ? parsed.fromDate() : toDate.minusDays(DEFAULT_DAYS - 1L);
        AdminDateRange range = AdminDateRange.of(fromDate, toDate);

        List<Instant> createdAts = reportRepository.findAccountCreatedAt(range.from(), range.to(), role);
        return buildGrowth(g, fromDate, toDate, createdAts);
    }

    static AccountGrowthResponse buildGrowth(Granularity g, LocalDate fromDate, LocalDate toDate,
                                             List<Instant> createdAts) {
        // Đủ mọi kỳ từ kỳ chứa fromDate tới kỳ chứa toDate, kể cả kỳ 0 đăng ký
        Map<LocalDate, Long> counts = new TreeMap<>();
        for (LocalDate start = g.startOf(fromDate); !start.isAfter(toDate); start = g.next(start)) {
            if (counts.size() >= MAX_POINTS) {
                throw new AppException(ErrorCode.INVALID_DATE_RANGE);
            }
            counts.put(start, 0L);
        }

        for (Instant createdAt : createdAts) {
            LocalDate day = createdAt.atZone(AdminDateRange.VN_ZONE).toLocalDate();
            counts.merge(g.startOf(day), 1L, Long::sum);
        }

        List<AccountGrowthResponse.Point> points = new ArrayList<>();
        long total = 0;
        for (Map.Entry<LocalDate, Long> e : counts.entrySet()) {
            LocalDate start = e.getKey();
            LocalDate end = g.next(start).minusDays(1);
            points.add(AccountGrowthResponse.Point.builder()
                    .label(g.label(start))
                    .periodStart(start.isBefore(fromDate) ? fromDate : start)
                    .periodEnd(end.isAfter(toDate) ? toDate : end)
                    .count(e.getValue())
                    .build());
            total += e.getValue();
        }

        return AccountGrowthResponse.builder()
                .granularity(g.name().toLowerCase(Locale.ROOT))
                .from(fromDate)
                .to(toDate)
                .total(total)
                .points(points)
                .build();
    }
}
