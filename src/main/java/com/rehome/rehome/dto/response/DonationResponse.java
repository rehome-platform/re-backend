package com.rehome.rehome.dto.response;

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
    private String campaignId;
    private String donorName;

    @JsonProperty("isAnonymous") // Jackson mặc định bỏ tiền tố "is" của getter boolean -> phải ép tên field tường minh
    private boolean isAnonymous;

    private String note;
    private String status;

    // Mã nhân viên Moderator đã ghi nhận lượt quyên góp này (ModeratorProfile.moderatorCode)
    private String recordedBy;

    private Instant handedOverAt;
    private Instant createdAt;
}
