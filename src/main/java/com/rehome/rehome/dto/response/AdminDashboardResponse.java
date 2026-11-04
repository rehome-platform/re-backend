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
    // Hoa hồng + phí rút hàng nền tảng đã thu
    private final long platformRevenue;
    // Số dư quỹ nền tảng
    private final long fundBalance;
    // Số việc trong hàng đợi Moderator đã quá hạn
    private final long overdueTaskCount;
}
