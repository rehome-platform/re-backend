package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.response.RevenueTrendResponse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.AdminDashboardQueryRepository;
import com.rehome.rehome.repository.AdminDashboardQueryRepository.RevenueEntry;
import com.rehome.rehome.service.RevenueTrendService;
import com.rehome.rehome.serviceImpl.AccountGrowthServiceImpl.Granularity;
import com.rehome.rehome.util.AdminDateRange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Gom doanh thu nền tảng theo kỳ. Dùng chung cách chia kỳ (ngày/tuần ISO/tháng, giờ VN) và giới hạn số kỳ
 * của AccountGrowthServiceImpl để hai biểu đồ của dashboard hành xử giống nhau.
 */
@Service
@RequiredArgsConstructor
public class RevenueTrendServiceImpl implements RevenueTrendService {

    private static final int DEFAULT_DAYS = 30;

    private final AdminDashboardQueryRepository queryRepository;

    @Override
    @Transactional(readOnly = true)
    public RevenueTrendResponse trend(String from, String to, String granularity) {
        Granularity g = Granularity.parse(granularity);

        AdminDateRange parsed = AdminDateRange.optional(from, to);
        LocalDate toDate = parsed.toDate() != null ? parsed.toDate() : LocalDate.now(AdminDateRange.VN_ZONE);

        List<RevenueEntry> entries = queryRepository.findPlatformRevenueEntries(parsed.from(), parsed.to());

        // Bỏ from (toàn thời gian): bắt đầu từ ngày có doanh thu đầu tiên thay vì 1970 để không vượt giới hạn số kỳ
        // (bút toán đã được lọc < cận trên nên ngày đầu tiên không bao giờ vượt toDate)
        LocalDate fromDate = parsed.fromDate() != null
                ? parsed.fromDate()
                : entries.stream()
                        .map(e -> e.createdAt().atZone(AdminDateRange.VN_ZONE).toLocalDate())
                        .min(LocalDate::compareTo)
                        .orElse(toDate.minusDays(DEFAULT_DAYS - 1L));

        return buildTrend(g, fromDate, toDate, entries);
    }

    static RevenueTrendResponse buildTrend(Granularity g, LocalDate fromDate, LocalDate toDate,
                                           List<RevenueEntry> entries) {
        // Đủ mọi kỳ từ kỳ chứa fromDate tới kỳ chứa toDate, kể cả kỳ không có doanh thu
        Map<LocalDate, BigDecimal> sums = new TreeMap<>();
        for (LocalDate start = g.startOf(fromDate); !start.isAfter(toDate); start = g.next(start)) {
            if (sums.size() >= AccountGrowthServiceImpl.MAX_POINTS) {
                throw new AppException(ErrorCode.INVALID_DATE_RANGE);
            }
            sums.put(start, BigDecimal.ZERO);
        }

        for (RevenueEntry entry : entries) {
            LocalDate day = entry.createdAt().atZone(AdminDateRange.VN_ZONE).toLocalDate();
            if (day.isBefore(fromDate) || day.isAfter(toDate)) {
                continue;
            }
            sums.merge(g.startOf(day), entry.amount(), BigDecimal::add);
        }

        List<RevenueTrendResponse.Point> points = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<LocalDate, BigDecimal> e : sums.entrySet()) {
            LocalDate start = e.getKey();
            LocalDate end = g.next(start).minusDays(1);
            points.add(RevenueTrendResponse.Point.builder()
                    .label(g.label(start))
                    .periodStart(start.isBefore(fromDate) ? fromDate : start)
                    .periodEnd(end.isAfter(toDate) ? toDate : end)
                    .amount(toLong(e.getValue()))
                    .build());
            total = total.add(e.getValue());
        }

        return RevenueTrendResponse.builder()
                .granularity(g.name().toLowerCase(Locale.ROOT))
                .from(fromDate)
                .to(toDate)
                .total(toLong(total))
                .points(points)
                .build();
    }

    private static long toLong(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).longValue();
    }
}
