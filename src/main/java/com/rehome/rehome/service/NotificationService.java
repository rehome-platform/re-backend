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
    void notifyProduct(Account recipientAccount, NotificationType type, String title, String body,
                       String productId, UUID warehouseId);

    void notify(Account recipientAccount, NotificationType type, String title, String body,
                NotificationTargetType targetType, UUID targetId, UUID warehouseId);

    // UC-44: báo cho mọi Member đang theo dõi tổ chức organizationId (chiến dịch/bài mới). Chạy
    // @Async nên không làm chậm API tạo chiến dịch/bài - chỉ gọi sau khi đã lưu thành công.
    void notifyFollowers(UUID organizationId, NotificationType type, String title, String body,
                          NotificationTargetType targetType, UUID targetId);

    // Đánh dấu một thông báo của chính người gọi đã đọc. 404 nếu không thuộc về accountId.
    NotificationResponse markAsRead(UUID accountId, UUID notificationId);

    // Đánh dấu toàn bộ thông báo chưa đọc của người gọi đã đọc.
    void markAllAsRead(UUID accountId);

    // Số thông báo chưa đọc, dùng hiện badge.
    long countUnread(UUID accountId);
}
