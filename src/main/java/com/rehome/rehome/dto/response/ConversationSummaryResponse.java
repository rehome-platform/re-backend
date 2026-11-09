package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 1 dòng trong danh sách hội thoại (F-089/F-090/F-091).
 * counterpart là phía đối diện của người đang xem: Member thấy tổ chức/kho,
 * còn Organization và Moderator thấy Member.
 * Danh sách hội thoại (GET /conversations) sắp theo thời điểm tin cuối, mới nhất trước;
 * hội thoại chưa có tin nhắn nào thì dùng thời điểm tạo hội thoại.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationSummaryResponse {
    private String id;
    private String type;
    private CounterpartInfo counterpart;
    private LastMessageInfo lastMessage;
    private long unreadCount;

    // Thống kê của Member đối diện, chỉ gắn khi người xem là Moderator (hộp thư kho F-089/F-091);
    // null với Member/Organization xem hội thoại của chính mình.
    private String phone;
    private Long consignCount;
    private Long shippingCount;
    private Long returnCount;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CounterpartInfo {
        private String id;
        private String displayName;
        private String avatarUrl;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LastMessageInfo {
        private String content;
        private Instant createdAt;
        private String senderId;
    }
}
