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

    // Mở thẳng 1 hội thoại theo id, ví dụ từ thông báo đẩy. 404 nếu không tồn tại, 403 nếu
    // người xem không thuộc hội thoại này.
    ConversationSummaryResponse getConversation(Account viewer, UUID conversationId);

    // F-089/F-090: Member mở hội thoại; đã có thì trả về hội thoại cũ.
    ConversationResponse createConversation(UUID memberId, CreateConversationRequest request);

    // F-089/F-090: lịch sử tin nhắn, mới nhất trước. Mở hội thoại cũng đánh dấu đã đọc.
    PageResponse<MessageResponse> listMessages(Account viewer, UUID conversationId, int page, int size);

    // F-089/F-090/F-091: gửi tin nhắn.
    MessageResponse sendMessage(Account viewer, UUID conversationId, SendMessageRequest request);

    // Dùng để chặn STOMP SUBSCRIBE vào /topic/conversations.{id} của người không thuộc hội thoại
    // đó - không throw như requireParticipant (gọi từ REST) vì nơi gọi nằm trong ChannelInterceptor,
    // chỉ cần true/false để quyết định cho phép subscribe hay không. false cả khi conversationId
    // không tồn tại.
    boolean isParticipant(Account viewer, UUID conversationId);
}
