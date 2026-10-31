package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.CreateConversationRequest;
import com.rehome.rehome.dto.request.SendMessageRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.ConversationResponse;
import com.rehome.rehome.dto.response.ConversationSummaryResponse;
import com.rehome.rehome.dto.response.MessageResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.service.ConversationService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @GetMapping
    @PreAuthorize("hasAnyRole('MEMBER','ORGANIZATION','MODERATOR')")
    @Operation(summary = "Danh sách hội thoại theo vai trò; Moderator thấy hộp thư chung của kho (F-089/F-090/F-091)")
    public ResponseEntity<ApiResponse<List<ConversationSummaryResponse>>> listConversations(
            @AuthenticationPrincipal Account account) {
        return ResponseEntity.ok(ApiResponse.success(conversationService.listConversations(account)));
    }

    @PostMapping
    @PreAuthorize("hasRole('MEMBER')")
    @Operation(summary = "Mở hội thoại với 1 tổ chức hoặc kho; đã có thì trả về hội thoại cũ (F-089/F-090)")
    public ResponseEntity<ApiResponse<ConversationResponse>> createConversation(
            @RequestBody CreateConversationRequest request,
            @AuthenticationPrincipal Account account) {
        ConversationResponse result = conversationService.createConversation(account.getId(), request);
        return ResponseEntity.status(201).body(ApiResponse.success(result, 201));
    }

    @GetMapping("/{id}/messages")
    @PreAuthorize("hasAnyRole('MEMBER','ORGANIZATION','MODERATOR')")
    @Operation(summary = "Lịch sử tin nhắn, mới nhất trước (F-089/F-090)")
    public ResponseEntity<ApiResponse<PageResponse<MessageResponse>>> listMessages(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Account account) {
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.listMessages(account, id, page, size)));
    }

    @PostMapping("/{id}/messages")
    @PreAuthorize("hasAnyRole('MEMBER','ORGANIZATION','MODERATOR')")
    @Operation(summary = "Gửi tin nhắn (F-089/F-090/F-091)")
    public ResponseEntity<ApiResponse<MessageResponse>> sendMessage(
            @PathVariable UUID id,
            @RequestBody SendMessageRequest request,
            @AuthenticationPrincipal Account account) {
        MessageResponse result = conversationService.sendMessage(account, id, request);
        return ResponseEntity.status(201).body(ApiResponse.success(result, 201));
    }
}
