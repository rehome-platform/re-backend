package com.rehome.rehome.repository;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.QueueType;
import com.rehome.rehome.entity.marketplace.QueueItem;
import com.rehome.rehome.entity.marketplace.Warehouse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QueueItemRepository extends JpaRepository<QueueItem, String> {

    @Query("select q from QueueItem q "
            + "where q.type = :type and (q.warehouse is null or q.warehouse = :warehouse) "
            + "order by q.createdAt asc")
    List<QueueItem> findVisibleByType(@Param("type") QueueType type, @Param("warehouse") Warehouse warehouse);

    @Query("select q from QueueItem q "
            + "where (q.warehouse is null or q.warehouse = :warehouse) "
            + "order by q.createdAt asc")
    List<QueueItem> findAllVisible(@Param("warehouse") Warehouse warehouse);

    Optional<QueueItem> findByTypeAndRefId(QueueType type, String refId);

    // Claim an toàn khi 2 Moderator bấm cùng lúc: chỉ 1 người update được dòng claimed_by IS NULL
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update QueueItem q set q.claimedBy = :moderator, q.claimedAt = :now "
            + "where q.id = :id and q.claimedBy is null")
    int claimIfFree(@Param("id") String id, @Param("moderator") Account moderator, @Param("now") Instant now);
}