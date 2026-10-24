package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.QueueClaimResponse;
import com.rehome.rehome.dto.response.QueueItemResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.QueueType;
import com.rehome.rehome.entity.marketplace.QueueItem;
import com.rehome.rehome.entity.marketplace.Warehouse;
import java.util.List;

public interface QueueService {

    // API 28: danh sách việc, lọc theo type (null = tất cả), theo kho của Moderator
    List<QueueItemResponse> list(String type, Account moderator);

    // API 29: nhận việc, ném 409 nếu người khác nhận trước
    QueueClaimResponse claim(String id, Account moderator);

    // API 30: nhả việc đang nhận
    void release(String id, Account moderator);

    // Các service nghiệp vụ gọi khi có việc mới. Idempotent theo (type, refId)
    QueueItem enqueue(QueueType type, String refId, Warehouse warehouse);

    // Đóng việc khi nghiệp vụ gốc đã xử lý xong
    void dequeue(QueueType type, String refId);
}