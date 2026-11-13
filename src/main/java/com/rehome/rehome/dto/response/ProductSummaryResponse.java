package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductSummaryResponse {
    private String id;
    private String name;
    private String thumbnailUrl;
    private String brandName;
    private String categoryName;
    private String size;
    private String condition;
    private BigDecimal originalPrice;
    private BigDecimal currentPrice;
    // true = món này do chính người đang xem ký gửi, FE ẩn nút mua/thêm giỏ.
    // Kiểu Boolean (không phải boolean) để Lombok sinh getIsMine() -> JSON "isMine";
    // boolean nguyên thủy sẽ sinh isMine() và Jackson đặt tên field thành "mine".
    private Boolean isMine;

    // true = chủ món đang có gói VIP bật "ưu tiên hiển thị" -> FE hiện badge "Nổi bật" ở góc ảnh.
    // Badge dùng màu trung tính (cam/vàng nhạt) và KHÔNG được ghi chữ gợi ý chất lượng kiểu
    // "Chính hãng"/"Uy tín": mọi món trên sàn đều đã qua kiểm định như nhau.
    private boolean featured;
}
