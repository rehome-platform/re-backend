package com.rehome.rehome.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Lượt quyên góp vừa được ghi nhận tại điểm ký gửi (F-163).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DonationResponse {
    private String id;
    private String warehouseId;
    private String warehouseName;
    private String campaignId;
    private String campaignTitle;
    private String donorName;

    @JsonProperty("isAnonymous") // Jackson mặc định bỏ tiền tố "is" của getter boolean -> phải ép tên field tường minh
    private boolean isAnonymous;

    private String note;
    private String status;

    // DIRECT | CONSIGNED_ITEM (xem DonationSource)
    private String source;

    // Mã nhân viên Moderator đã ghi nhận lượt quyên góp này (ModeratorProfile.moderatorCode)
    private String recordedBy;

    // Tổ chức sở hữu chiến dịch
    private String organizationId;
    private String organizationName;
    private String handedOverToOrganizationName;

    // Sản phẩm ký gửi gốc nếu lượt quyên góp này đến từ POST /products/{id}/donate; null với
    // lượt ghi nhận trực tiếp tại quầy (F-163)
    private String productId;
    private String productName;

    // Chỉ trả khi source = CONSIGNED_ITEM; null với lượt DIRECT
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private DonationProductResponse product;

    private Instant handedOverAt;
    private Instant createdAt;
}
