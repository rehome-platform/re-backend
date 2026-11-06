package com.rehome.rehome.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 1 tin nhắn (F-089/F-090/F-091). createdAt lấy từ Message.sentAt.
 * isMine so senderId với chính người đang xem, tính sẵn ở server vì tổ chức hiện không có
 * account id riêng trong GET /api/organizations/me để tự so sánh.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageResponse {
    private String id;
    private String senderId;
    private String content;
    private List<String> imageUrls;
    private Instant createdAt;

    @JsonProperty("isMine")
    private boolean isMine;
}
