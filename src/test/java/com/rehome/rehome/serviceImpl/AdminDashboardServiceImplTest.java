package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.component.DashboardExcelWriter;
import com.rehome.rehome.dto.response.AdminDashboardResponse;
import com.rehome.rehome.repository.AdminDashboardQueryRepository;
import com.rehome.rehome.repository.AdminDashboardQueryRepository.TermSales;
import com.rehome.rehome.service.SystemConfigReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** API 147 - số liệu doanh thu / hoa hồng / tồn kho theo kỳ. */
@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceImplTest {

    @Mock AdminDashboardQueryRepository queryRepository;
    @Mock SystemConfigReader systemConfigReader;
    @Mock DashboardExcelWriter excelWriter;

    @InjectMocks AdminDashboardServiceImpl service;

    private void stubScalars(BigDecimal platformRevenue) {
        when(queryRepository.sumCompletedOrderAmount(any(), any())).thenReturn(new BigDecimal("1000000"));
        when(queryRepository.sumMemberWalletBalance()).thenReturn(BigDecimal.ZERO);
        when(queryRepository.sumPlatformRevenue(any(), any())).thenReturn(platformRevenue);
        when(queryRepository.sumPlatformFundBalance()).thenReturn(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("getDashboard: doanh thu bán, hoa hồng, tồn kho tách theo kỳ 1/2/3; tổng món đã bán là tổng các kỳ")
    void getDashboard_breaksDownByTerm() {
        stubScalars(new BigDecimal("150000"));
        when(queryRepository.sumCompletedSalesByTerm(any(), any())).thenReturn(List.of(
                new TermSales((short) 1, new BigDecimal("600000"), new BigDecimal("90000.4"), 4),
                new TermSales((short) 3, new BigDecimal("400000"), new BigDecimal("40000"), 2)));
        when(queryRepository.countStockedProductsByTerm()).thenReturn(Map.of((short) 1, 7L, (short) 2, 3L));

        AdminDashboardResponse r = service.getDashboard();

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
        // 150000 / 1000000 * 100
        assertThat(r.getNetProfitMarginPercent()).isEqualTo(15.0);
    }

    @Test
    @DisplayName("getDashboard: chưa có đơn hoàn thành -> mọi số theo kỳ bằng 0, tỷ suất lợi nhuận 0 (không chia cho 0)")
    void getDashboard_noSales() {
        stubScalars(new BigDecimal("5000"));
        when(queryRepository.sumCompletedSalesByTerm(any(), any())).thenReturn(List.of());
        when(queryRepository.countStockedProductsByTerm()).thenReturn(Map.of());

        AdminDashboardResponse r = service.getDashboard();

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
}
