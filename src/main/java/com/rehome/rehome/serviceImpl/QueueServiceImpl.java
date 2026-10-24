package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.response.QueueClaimResponse;
import com.rehome.rehome.dto.response.QueueItemResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.QueueType;
import com.rehome.rehome.entity.marketplace.QueueItem;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.QueueItemRepository;
import com.rehome.rehome.service.QueueService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QueueServiceImpl implements QueueService {

    private final QueueItemRepository queueItemRepository;

    @Override
    @Transactional(readOnly = true)
    public List<QueueItemResponse> list(String type, Account moderator) {
        Warehouse warehouse = requireWarehouse(moderator);

        List<QueueItem> items;
        if (type == null || type.isBlank()) {
            items = queueItemRepository.findAllVisible(warehouse);
        } else {
            items = queueItemRepository.findVisibleByType(parseType(type), warehouse);
        }
        return items.stream().map(QueueItemResponse::from).toList();
    }

    @Override
    @Transactional
    public QueueClaimResponse claim(String id, Account moderator) {
        Warehouse warehouse = requireWarehouse(moderator);

        QueueItem item = queueItemRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.QUEUE_ITEM_NOT_FOUND));

        // Việc thuộc kho khác thì coi như không tồn tại
        if (item.getWarehouse() != null && !item.getWarehouse().getId().equals(warehouse.getId())) {
            throw new AppException(ErrorCode.QUEUE_ITEM_NOT_FOUND);
        }

        Instant now = Instant.now();
        int updated = queueItemRepository.claimIfFree(id, moderator, now);
        if (updated == 0) {
            throw new AppException(ErrorCode.QUEUE_ITEM_ALREADY_CLAIMED);
        }
        return QueueClaimResponse.builder()
                .id(id)
                .claimedBy(moderator.getId().toString())
                .claimedAt(now)
                .build();
    }

    @Override
    @Transactional
    public void release(String id, Account moderator) {
        QueueItem item = queueItemRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.QUEUE_ITEM_NOT_FOUND));

        if (item.getClaimedBy() == null || !item.getClaimedBy().getId().equals(moderator.getId())) {
            throw new AppException(ErrorCode.QUEUE_ITEM_NOT_CLAIMED_BY_YOU);
        }
        item.setClaimedBy(null);
        item.setClaimedAt(null);
        queueItemRepository.save(item);
    }

    @Override
    @Transactional
    public QueueItem enqueue(QueueType type, String refId, Warehouse warehouse) {
        return queueItemRepository.findByTypeAndRefId(type, refId)
                .orElseGet(() -> queueItemRepository.save(
                        QueueItem.builder().type(type).refId(refId).warehouse(warehouse).build()));
    }

    @Override
    @Transactional
    public void dequeue(QueueType type, String refId) {
        queueItemRepository.findByTypeAndRefId(type, refId).ifPresent(queueItemRepository::delete);
    }

    private Warehouse requireWarehouse(Account moderator) {
        if (moderator.getWarehouse() == null) {
            throw new AppException(ErrorCode.MODERATOR_NO_WAREHOUSE);
        }
        return moderator.getWarehouse();
    }

    private QueueType parseType(String type) {
        try {
            return QueueType.valueOf(type.trim());
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.INVALID_QUEUE_TYPE);
        }
    }
}