package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** API 147 - F-158. Tiền tính bằng đồng (VND), làm tròn về số nguyên. */
@Getter
@Builder
@AllArgsConstructor
public class AdminDashboardResponse {
    // Tổng giá trị các đơn đã hoàn thành
    private final long revenue;
    // Số đơn đã thanh toán (không tính chờ thanh toán / đã hủy)
    private final long ordersCount;
    // Số món đang nằm trong kho
    private final long inventoryCount;
    // Điểm đánh giá dịch vụ trung bình, 1 chữ số thập phân; 0 nếu chưa có đánh giá
    private final double avgServiceRating;
    // Tổng số dư các ví Member
    private final long walletBalanceHeld;
    // Hoa hồng + phí rút hàng nền tảng đã thu (= commissionRevenue + withdrawalFeeRevenue)
    private final long platformRevenue;
    // Cơ cấu nguồn thu của nền tảng, lọc theo [from, to) giống platformRevenue.
    // Hoa hồng = tổng giá chốt * tỷ lệ hoa hồng của các món thuộc đơn COMPLETED hoàn thành trong khoảng
    private final long commissionRevenue;
    // Phí rút hàng Member đã trả (bút toán WITHDRAWAL_FEE thành công trong khoảng)
    private final long withdrawalFeeRevenue;
    // Chi đền bù hàng xuống cấp (bút toán DEGRADED_COMPENSATION thành công trong khoảng); là khoản chi,
    // KHÔNG trừ vào platformRevenue
    private final long compensationExpense;
    // Số dư quỹ nền tảng
    private final long fundBalance;
    // Số việc trong hàng đợi Moderator đã quá hạn
    private final long overdueTaskCount;

    // Doanh thu bán (tổng giá các món trong đơn COMPLETED) và hoa hồng thu được, theo kỳ ký gửi
    // áp dụng lúc đặt đơn (OrderItem.termApplied); lọc theo [from, to) giống revenue
    private final long term1SalesRevenue;
    private final long term1Commission;
    private final long term2SalesRevenue;
    private final long term2Commission;
    private final long term3SalesRevenue;
    private final long term3Commission;

    // Số món đang nằm trong kho theo kỳ hiện tại của sản phẩm (Product.currentTerm); luôn là giá trị hiện tại
    private final long term1StockCount;
    private final long term2StockCount;
    private final long term3StockCount;

    // Tổng số món đã bán (trong các đơn COMPLETED thuộc khoảng)
    private final long totalSoldItemsCount;
    // platformRevenue / tổng doanh thu bán * 100, làm tròn 2 chữ số; 0 nếu chưa có doanh thu bán
    private final double netProfitMarginPercent;
}
