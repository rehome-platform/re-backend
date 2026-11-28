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

    List<ConversationSummaryResponse> listConversations(Account viewer);

    ConversationSummaryResponse getConversation(Account viewer, UUID conversationId);

    ConversationResponse createConversation(UUID memberId, CreateConversationRequest request);

    PageResponse<MessageResponse> listMessages(Account viewer, UUID conversationId, int page, int size);

    MessageResponse sendMessage(Account viewer, UUID conversationId, SendMessageRequest request);

    boolean isParticipant(Account viewer, UUID conversationId);
}
