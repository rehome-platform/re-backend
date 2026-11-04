package com.rehome.rehome.controller;

import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.NotificationResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.UnreadNotificationCountResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @PreAuthorize("hasAnyRole('MEMBER','ORGANIZATION','MODERATOR')")
    @Operation(summary = "Danh sách thông báo trong ứng dụng (F-092/F-093/F-094)")
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> listNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Account account) {
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.listNotifications(account.getId(), page, size)));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("hasAnyRole('MEMBER','ORGANIZATION','MODERATOR')")
    @Operation(summary = "Số thông báo chưa đọc, dùng hiện badge")
    public ResponseEntity<ApiResponse<UnreadNotificationCountResponse>> getUnreadCount(
            @AuthenticationPrincipal Account account) {
        return ResponseEntity.ok(ApiResponse.success(
                new UnreadNotificationCountResponse(notificationService.countUnread(account.getId()))));
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("hasAnyRole('MEMBER','ORGANIZATION','MODERATOR')")
    @Operation(summary = "Đánh dấu một thông báo đã đọc")
    public ResponseEntity<ApiResponse<NotificationResponse>> markAsRead(
            @PathVariable UUID id,
            @AuthenticationPrincipal Account account) {
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.markAsRead(account.getId(), id), "Đã đọc thông báo"));
    }

    @PatchMapping("/read-all")
    @PreAuthorize("hasAnyRole('MEMBER','ORGANIZATION','MODERATOR')")
    @Operation(summary = "Đánh dấu tất cả thông báo đã đọc")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(@AuthenticationPrincipal Account account) {
        notificationService.markAllAsRead(account.getId());
        return ResponseEntity.ok(ApiResponse.success(null, 204, "Đã đọc tất cả thông báo"));
    }
}
