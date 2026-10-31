package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.CreateConversationRequest;
import com.rehome.rehome.dto.request.SendMessageRequest;
import com.rehome.rehome.dto.response.ConversationResponse;
import com.rehome.rehome.dto.response.ConversationSummaryResponse;
import com.rehome.rehome.dto.response.MessageResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.auth.Account;

import java.util.List;
import java.util.UUID;

public interface ConversationService {

    // F-089/F-090/F-091: hộp thư theo vai trò của người đang xem.
    List<ConversationSummaryResponse> listConversations(Account viewer);

    // F-089/F-090: Member mở hội thoại; đã có thì trả về hội thoại cũ.
    ConversationResponse createConversation(UUID memberId, CreateConversationRequest request);

    // F-089/F-090: lịch sử tin nhắn, mới nhất trước. Mở hội thoại cũng đánh dấu đã đọc.
    PageResponse<MessageResponse> listMessages(Account viewer, UUID conversationId, int page, int size);

    // F-089/F-090/F-091: gửi tin nhắn.
    MessageResponse sendMessage(Account viewer, UUID conversationId, SendMessageRequest request);
}
