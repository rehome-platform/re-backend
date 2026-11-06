package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.RegisterDeviceTokenRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.service.DeviceTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * App gọi lúc đăng nhập để đăng ký token FCM, và lúc đăng xuất để gỡ - push notification
 * (F-092/F-093/F-094) khi app đã tắt/background, lúc WebSocket không còn kết nối.
 */
@RestController
@RequestMapping("/me/device-tokens")
@RequiredArgsConstructor
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> register(@AuthenticationPrincipal Account account,
                                                        @RequestBody RegisterDeviceTokenRequest request) {
        deviceTokenService.register(account.getId(), request);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã đăng ký nhận thông báo trên thiết bị này."));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> unregister(@AuthenticationPrincipal Account account,
                                                          @RequestParam String token) {
        deviceTokenService.unregister(account.getId(), token);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã gỡ thông báo trên thiết bị này."));
    }
}
