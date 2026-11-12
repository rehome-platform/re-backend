package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Món ký gửi gốc của lượt quyên góp. Chỉ được trả trong DonationResponse khi
 * source = CONSIGNED_ITEM; lượt ghi nhận trực tiếp (DIRECT) thì product = null.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DonationProductResponse {
    private String id;
    private String name;
    private String sku;
    // Ảnh đầu tiên (displayOrder nhỏ nhất); null nếu món chưa có ảnh
    private String photoUrl;
}
