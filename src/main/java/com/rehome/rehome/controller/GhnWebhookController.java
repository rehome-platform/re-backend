package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.GhnWebhookPayload;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.service.GhnWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// API 71 - POST /webhooks/ghn (F-128). GHN gọi về, không qua JWT - xem SecurityConfig.PUBLIC_ENDPOINTS.
@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
public class GhnWebhookController {

    private final GhnWebhookService ghnWebhookService;

    @PostMapping("/ghn")
    public ResponseEntity<ApiResponse<Void>> handleGhnWebhook(@RequestBody GhnWebhookPayload payload) {
        ghnWebhookService.handleWebhook(payload);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
