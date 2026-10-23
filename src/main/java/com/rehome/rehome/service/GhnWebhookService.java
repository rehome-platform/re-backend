package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.GhnWebhookPayload;

public interface GhnWebhookService {
    void handleWebhook(GhnWebhookPayload payload);
}
