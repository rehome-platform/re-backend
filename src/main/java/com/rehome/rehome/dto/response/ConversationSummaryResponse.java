package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 1 dòng trong danh sách hội thoại (F-089/F-090/F-091).
 * counterpart là phía đối diện của người đang xem: Member thấy tổ chức/kho,
 * còn Organization và Moderator thấy Member.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationSummaryResponse {
    private String id;
    private String type;
    private CounterpartInfo counterpart;
    private String lastMessage;
    private long unreadCount;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CounterpartInfo {
        private String id;
        private String displayName;
    }
}
