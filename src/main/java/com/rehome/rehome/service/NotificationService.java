package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.NotificationResponse;
import com.rehome.rehome.dto.response.PageResponse;

import java.util.UUID;

public interface NotificationService {

    // F-092/F-093/F-094: thông báo của chính người đang đăng nhập, mới nhất trước.
    PageResponse<NotificationResponse> listNotifications(UUID accountId, int page, int size);
}
