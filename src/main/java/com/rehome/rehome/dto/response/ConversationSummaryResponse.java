package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

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
