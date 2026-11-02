package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.NotificationResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.NotificationTargetType;
import com.rehome.rehome.entity.enums.NotificationType;

import java.util.UUID;

public interface NotificationService {

    // F-092/F-093/F-094: thông báo của chính người đang đăng nhập, mới nhất trước.
    PageResponse<NotificationResponse> listNotifications(UUID accountId, int page, int size);

    // Các service nghiệp vụ gọi khi cần báo cho 1 tài khoản (giống QueueService.enqueue).
    // targetType/targetId để app mở đúng màn hình khi bấm vào thông báo; warehouseId chỉ có ý
    // nghĩa với DONATION_RECORDED (OC-07). Truyền null cho 3 tham số này khi không cần deep-link.
    void notify(Account recipientAccount, NotificationType type, String title, String body,
                NotificationTargetType targetType, UUID targetId, UUID warehouseId);
}
