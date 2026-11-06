package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.component.DashboardExcelWriter;
import com.rehome.rehome.constant.SystemConfigKeys;
import com.rehome.rehome.dto.response.AdminDashboardResponse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.AdminDashboardQueryRepository;
import com.rehome.rehome.service.AdminDashboardService;
import com.rehome.rehome.service.SystemConfigReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    // Mốc "toàn thời gian": tránh tham số null trong JPQL
    private static final Instant BEGINNING = Instant.EPOCH;
    private static final Instant FOREVER = Instant.parse("3000-01-01T00:00:00Z");

    private final AdminDashboardQueryRepository queryRepository;
    private final SystemConfigReader systemConfigReader;
    private final DashboardExcelWriter excelWriter;

    @Override
    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboard() {
        return snapshot(BEGINNING, FOREVER);
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardExport export(String from, String to) {
        LocalDate fromDate = parseDate(from);
        LocalDate toDate = parseDate(to);
        if (fromDate.isAfter(toDate)) {
            throw new AppException(ErrorCode.INVALID_DATE_RANGE);
        }

        // Gồm cả ngày "to": cận trên là 00:00 ngày kế tiếp (giờ VN)
        Instant start = fromDate.atStartOfDay(VN_ZONE).toInstant();
        Instant end = toDate.plusDays(1).atStartOfDay(VN_ZONE).toInstant();

        byte[] content = excelWriter.write(snapshot(start, end), fromDate, toDate);
        return new DashboardExport("dashboard_report_" + fromDate.format(MONTH_FORMAT) + ".xlsx", content);
    }

    // Doanh thu, số đơn, điểm đánh giá, doanh thu nền tảng lọc theo [from, to);
    // tồn kho, số dư ví, quỹ và việc quá hạn luôn là giá trị hiện tại
    private AdminDashboardResponse snapshot(Instant from, Instant to) {
        int overdueHours = systemConfigReader.getInt(
                SystemConfigKeys.OVERDUE_TASK_HOURS, SystemConfigKeys.DEFAULT_OVERDUE_TASK_HOURS);
        Instant overdueBefore = Instant.now().minus(overdueHours, ChronoUnit.HOURS);

        Double rating = queryRepository.avgServiceRating(from, to);

        // Doanh thu bán / hoa hồng theo kỳ 1-3 (món không thuộc kỳ 1-3 vẫn được tính vào tổng)
        List<AdminDashboardQueryRepository.TermSales> termSales = queryRepository.sumCompletedSalesByTerm(from, to);
        Map<Short, Long> stockByTerm = queryRepository.countStockedProductsByTerm();

        BigDecimal totalSalesRevenue = termSales.stream()
                .map(AdminDashboardQueryRepository.TermSales::salesRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalSoldItemsCount = termSales.stream()
                .mapToLong(AdminDashboardQueryRepository.TermSales::itemCount)
                .sum();

        BigDecimal platformRevenue = queryRepository.sumPlatformRevenue(from, to);

        return AdminDashboardResponse.builder()
                .revenue(toLong(queryRepository.sumCompletedOrderAmount(from, to)))
                .ordersCount(queryRepository.countPaidOrders(from, to))
                .inventoryCount(queryRepository.countStockedProducts())
                .avgServiceRating(rating == null ? 0.0 : Math.round(rating * 10.0) / 10.0)
                .walletBalanceHeld(toLong(queryRepository.sumMemberWalletBalance()))
                .platformRevenue(toLong(platformRevenue))
                .fundBalance(toLong(queryRepository.sumPlatformFundBalance()))
                .overdueTaskCount(queryRepository.countQueueItemsCreatedBefore(overdueBefore))
                .term1SalesRevenue(toLong(salesOfTerm(termSales, 1)))
                .term1Commission(toLong(commissionOfTerm(termSales, 1)))
                .term2SalesRevenue(toLong(salesOfTerm(termSales, 2)))
                .term2Commission(toLong(commissionOfTerm(termSales, 2)))
                .term3SalesRevenue(toLong(salesOfTerm(termSales, 3)))
                .term3Commission(toLong(commissionOfTerm(termSales, 3)))
                .term1StockCount(stockByTerm.getOrDefault((short) 1, 0L))
                .term2StockCount(stockByTerm.getOrDefault((short) 2, 0L))
                .term3StockCount(stockByTerm.getOrDefault((short) 3, 0L))
                .totalSoldItemsCount(totalSoldItemsCount)
                .netProfitMarginPercent(netProfitMarginPercent(platformRevenue, totalSalesRevenue))
                .build();
    }

    private static BigDecimal salesOfTerm(List<AdminDashboardQueryRepository.TermSales> rows, int term) {
        return rows.stream().filter(r -> r.term() == term)
                .map(AdminDashboardQueryRepository.TermSales::salesRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal commissionOfTerm(List<AdminDashboardQueryRepository.TermSales> rows, int term) {
        return rows.stream().filter(r -> r.term() == term)
                .map(AdminDashboardQueryRepository.TermSales::commission)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // (platformRevenue / totalSalesRevenue) * 100; chưa có doanh thu bán thì 0 để tránh chia cho 0
    static double netProfitMarginPercent(BigDecimal platformRevenue, BigDecimal totalSalesRevenue) {
        if (totalSalesRevenue.signum() <= 0) {
            return 0.0;
        }
        return platformRevenue.multiply(BigDecimal.valueOf(100))
                .divide(totalSalesRevenue, 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static long toLong(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).longValue();
    }

    private static LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            throw new AppException(ErrorCode.INVALID_DATE_RANGE);
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new AppException(ErrorCode.INVALID_DATE_RANGE);
        }
    }
}
