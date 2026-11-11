package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.component.DashboardExcelWriter;
import com.rehome.rehome.dto.response.AdminDashboardResponse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.AdminDashboardQueryRepository;
import com.rehome.rehome.repository.AdminDashboardQueryRepository.TermSales;
import com.rehome.rehome.service.SystemConfigReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** API 147 - số liệu doanh thu / hoa hồng / tồn kho theo kỳ. */
@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceImplTest {

    @Mock AdminDashboardQueryRepository queryRepository;
    @Mock SystemConfigReader systemConfigReader;
    @Mock DashboardExcelWriter excelWriter;

    @InjectMocks AdminDashboardServiceImpl service;

    private void stubScalars(String commission, String withdrawalFee, String compensation) {
        when(queryRepository.sumCompletedOrderAmount(any(), any())).thenReturn(new BigDecimal("1000000"));
        when(queryRepository.sumMemberWalletBalance()).thenReturn(BigDecimal.ZERO);
        when(queryRepository.sumCompletedCommission(any(), any())).thenReturn(new BigDecimal(commission));
        when(queryRepository.sumWithdrawalFeeRevenue(any(), any())).thenReturn(new BigDecimal(withdrawalFee));
        when(queryRepository.sumDegradedCompensation(any(), any())).thenReturn(new BigDecimal(compensation));
        when(queryRepository.sumPlatformFundBalance()).thenReturn(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("getDashboard: doanh thu bán, hoa hồng, tồn kho tách theo kỳ 1/2/3; tổng món đã bán là tổng các kỳ")
    void getDashboard_breaksDownByTerm() {
        stubScalars("130000", "20000", "7000");
        when(queryRepository.sumCompletedSalesByTerm(any(), any())).thenReturn(List.of(
                new TermSales((short) 1, new BigDecimal("600000"), new BigDecimal("90000.4"), 4),
                new TermSales((short) 3, new BigDecimal("400000"), new BigDecimal("40000"), 2)));
        when(queryRepository.countStockedProductsByTerm()).thenReturn(Map.of((short) 1, 7L, (short) 2, 3L));

        AdminDashboardResponse r = service.getDashboard(null, null);

        assertThat(r.getTerm1SalesRevenue()).isEqualTo(600000);
        assertThat(r.getTerm1Commission()).isEqualTo(90000);
        assertThat(r.getTerm2SalesRevenue()).isZero();
        assertThat(r.getTerm2Commission()).isZero();
        assertThat(r.getTerm3SalesRevenue()).isEqualTo(400000);
        assertThat(r.getTerm3Commission()).isEqualTo(40000);
        assertThat(r.getTerm1StockCount()).isEqualTo(7);
        assertThat(r.getTerm2StockCount()).isEqualTo(3);
        assertThat(r.getTerm3StockCount()).isZero();
        assertThat(r.getTotalSoldItemsCount()).isEqualTo(6);
        // Cơ cấu nguồn thu: doanh thu nền tảng = hoa hồng + phí rút hàng; đền bù là khoản chi riêng
        assertThat(r.getCommissionRevenue()).isEqualTo(130000);
        assertThat(r.getWithdrawalFeeRevenue()).isEqualTo(20000);
        assertThat(r.getCompensationExpense()).isEqualTo(7000);
        assertThat(r.getPlatformRevenue()).isEqualTo(150000);
        // 150000 / 1000000 * 100
        assertThat(r.getNetProfitMarginPercent()).isEqualTo(15.0);
    }

    @Test
    @DisplayName("getDashboard: chưa có đơn hoàn thành -> mọi số theo kỳ bằng 0, tỷ suất lợi nhuận 0 (không chia cho 0)")
    void getDashboard_noSales() {
        stubScalars("5000", "0", "0");
        when(queryRepository.sumCompletedSalesByTerm(any(), any())).thenReturn(List.of());
        when(queryRepository.countStockedProductsByTerm()).thenReturn(Map.of());

        AdminDashboardResponse r = service.getDashboard(null, null);

        assertThat(r.getTotalSoldItemsCount()).isZero();
        assertThat(r.getTerm1SalesRevenue()).isZero();
        assertThat(r.getNetProfitMarginPercent()).isZero();
    }

    @Test
    @DisplayName("netProfitMarginPercent: làm tròn 2 chữ số thập phân")
    void netProfitMargin_roundsToTwoDecimals() {
        assertThat(AdminDashboardServiceImpl.netProfitMarginPercent(new BigDecimal("1"), new BigDecimal("3")))
                .isEqualTo(33.33);
    }

    @Test
    @DisplayName("getDashboard: from/to -> lọc [00:00 ngày from, 00:00 ngày sau to) theo giờ VN")
    void getDashboard_filtersByDateRange() {
        stubScalars("0", "0", "0");
        when(queryRepository.sumCompletedSalesByTerm(any(), any())).thenReturn(List.of());
        when(queryRepository.countStockedProductsByTerm()).thenReturn(Map.of());

        service.getDashboard("2026-11-01", "2026-11-30");

        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
        verify(queryRepository).sumCompletedCommission(from.capture(), to.capture());
        assertThat(from.getValue()).isEqualTo(Instant.parse("2026-10-31T17:00:00Z"));
        assertThat(to.getValue()).isEqualTo(Instant.parse("2026-11-30T17:00:00Z"));
    }

    @Test
    @DisplayName("getDashboard: ngày sai định dạng hoặc from > to -> INVALID_DATE_RANGE")
    void getDashboard_invalidRange() {
        assertThatThrownBy(() -> service.getDashboard("abc", null))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_DATE_RANGE);
        assertThatThrownBy(() -> service.getDashboard("2026-12-01", "2026-11-01"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_DATE_RANGE);
    }
}
