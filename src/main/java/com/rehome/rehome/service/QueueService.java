package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.QueueClaimResponse;
import com.rehome.rehome.dto.response.QueueItemResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.QueueType;
import com.rehome.rehome.entity.marketplace.QueueItem;
import com.rehome.rehome.entity.marketplace.Warehouse;
import java.util.List;

public interface QueueService {

    List<QueueItemResponse> list(String type, Account moderator);

    List<QueueItemResponse> list(String type, Boolean mine, Account moderator);

    QueueClaimResponse claim(String id, Account moderator);

    void release(String id, Account moderator);

    QueueItem enqueue(QueueType type, String refId, Warehouse warehouse);

    void dequeue(QueueType type, String refId);
}
