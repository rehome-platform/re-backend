package com.rehome.rehome.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Note quyên góp tổ chức tự ghi khi nhận đồ tại địa chỉ riêng (F-167).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DonationNoteResponse {
    private String id;
    private String campaignId;
    private String donorName;

    @JsonProperty("isAnonymous") // Jackson mặc định bỏ tiền tố "is" của getter boolean -> phải ép tên field tường minh
    private boolean isAnonymous;

    private String note;
    private Instant createdAt;
}
