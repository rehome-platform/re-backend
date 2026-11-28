package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.component.DashboardExcelWriter;
import com.rehome.rehome.constant.SystemConfigKeys;
import com.rehome.rehome.dto.response.AdminDashboardResponse;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.AdminDashboardQueryRepository;
import com.rehome.rehome.repository.AdminReportQueryRepository;
import com.rehome.rehome.service.AdminDashboardService;
import com.rehome.rehome.service.SystemConfigReader;
import com.rehome.rehome.util.AdminDateRange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final AdminDashboardQueryRepository queryRepository;
    private final AdminReportQueryRepository reportRepository;
    private final AccountRepository accountRepository;
    private final SystemConfigReader systemConfigReader;
    private final DashboardExcelWriter excelWriter;

    @Override
    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboard(String from, String to) {
        AdminDateRange range = AdminDateRange.optional(from, to);
        return snapshot(range.from(), range.to());
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardExport export(String from, String to, String sections) {
        AdminDateRange range = AdminDateRange.required(from, to);
        ExportSections selected = ExportSections.parse(sections);

        UsersExportData usersData = selected.users() ? usersExportData(range.from(), range.to()) : null;
        List<AdminReportQueryRepository.SoldItemRow> soldItems =
                reportRepository.findAllSoldItems(range.from(), range.to());

        byte[] content = excelWriter.write(snapshot(range.from(), range.to()), selected, usersData, soldItems,
                range.fromDate(), range.toDate());
        return new DashboardExport("dashboard_report_" + range.fromDate().format(MONTH_FORMAT) + ".xlsx", content);
    }

    private UsersExportData usersExportData(Instant from, Instant to) {
        long newAccounts = reportRepository.countAccountsCreated(from, to, null);
        return new UsersExportData(
                accountRepository.countByRole(Role.MEMBER),
                accountRepository.countByRole(Role.ORGANIZATION),
                accountRepository.countByRole(Role.MODERATOR),
                newAccounts);
    }

    private AdminDashboardResponse snapshot(Instant from, Instant to) {
        int overdueHours = systemConfigReader.getInt(
                SystemConfigKeys.OVERDUE_TASK_HOURS, SystemConfigKeys.DEFAULT_OVERDUE_TASK_HOURS);
        Instant overdueBefore = Instant.now().minus(overdueHours, ChronoUnit.HOURS);

        Double rating = queryRepository.avgServiceRating(from, to);

        List<AdminDashboardQueryRepository.TermSales> termSales = queryRepository.sumCompletedSalesByTerm(from, to);
        Map<Short, Long> stockByTerm = queryRepository.countStockedProductsByTerm();

        BigDecimal totalSalesRevenue = termSales.stream()
                .map(AdminDashboardQueryRepository.TermSales::salesRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalSoldItemsCount = termSales.stream()
                .mapToLong(AdminDashboardQueryRepository.TermSales::itemCount)
                .sum();

        long commissionRevenue = toLong(queryRepository.sumCompletedCommission(from, to));
        long withdrawalFeeRevenue = toLong(queryRepository.sumWithdrawalFeeRevenue(from, to));
        long returnShipBackFeeRevenue = toLong(queryRepository.sumReturnShipBackFeeRevenue(from, to));
        long membershipRevenue = toLong(queryRepository.sumMembershipRevenue(from, to));
        long compensationExpense = toLong(queryRepository.sumDegradedCompensation(from, to));
        long platformRevenue = commissionRevenue + withdrawalFeeRevenue + returnShipBackFeeRevenue + membershipRevenue;

        return AdminDashboardResponse.builder()
                .revenue(toLong(queryRepository.sumCompletedOrderAmount(from, to)))
                .ordersCount(queryRepository.countPaidOrders(from, to))
                .inventoryCount(queryRepository.countStockedProducts())
                .avgServiceRating(rating == null ? 0.0 : Math.round(rating * 10.0) / 10.0)
                .walletBalanceHeld(toLong(queryRepository.sumMemberWalletBalance()))
                .platformRevenue(platformRevenue)
                .commissionRevenue(commissionRevenue)
                .withdrawalFeeRevenue(withdrawalFeeRevenue)
                .returnShipBackFeeRevenue(returnShipBackFeeRevenue)
                .membershipRevenue(membershipRevenue)
                .compensationExpense(compensationExpense)
                .fundBalance(toLong(queryRepository.sumPlatformFundBalance()))
                .heldOrderAmount(toLong(queryRepository.sumHeldOrderAmount()))
                .heldOrderCount(queryRepository.countHeldOrders())
                .stockByStatus(stockByStatusName(queryRepository.countStockedProductsByStatus()))
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
                .netProfitMarginPercent(netProfitMarginPercent(BigDecimal.valueOf(platformRevenue), totalSalesRevenue))
                .build();
    }

    private static Map<String, Long> stockByStatusName(Map<ProductStatus, Long> byStatus) {
        Map<String, Long> result = new LinkedHashMap<>();
        byStatus.forEach((status, count) -> result.put(status.name(), count));
        return result;
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

    static double netProfitMarginPercent(BigDecimal platformRevenue, BigDecimal totalSalesRevenue) {
        if (totalSalesRevenue.signum() <= 0) {
            return 0.0;
        }
        return platformRevenue.multiply(BigDecimal.valueOf(100))
                .divide(totalSalesRevenue, 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static long toLong(BigDecimal value) {
        return value == null ? 0L : value.setScale(0, RoundingMode.HALF_UP).longValue();
    }
}
