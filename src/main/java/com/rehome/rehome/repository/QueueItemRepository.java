package com.rehome.rehome.repository;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.QueueType;
import com.rehome.rehome.entity.marketplace.QueueItem;
import com.rehome.rehome.entity.marketplace.Warehouse;
import java.time.Instant;
import java.util.Collection;
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

    @Query("select q from QueueItem q "
            + "where q.type = :type and q.claimedBy = :moderator "
            + "and (q.warehouse is null or q.warehouse = :warehouse) "
            + "order by q.createdAt asc")
    List<QueueItem> findVisibleByTypeClaimedBy(@Param("type") QueueType type,
                                           @Param("warehouse") Warehouse warehouse,
                                           @Param("moderator") Account moderator);

    @Query("select q from QueueItem q "
            + "where q.claimedBy = :moderator and (q.warehouse is null or q.warehouse = :warehouse) "
            + "order by q.createdAt asc")
    List<QueueItem> findAllVisibleClaimedBy(@Param("warehouse") Warehouse warehouse,
                                        @Param("moderator") Account moderator);

    Optional<QueueItem> findByTypeAndRefId(QueueType type, String refId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from QueueItem q left join fetch q.claimedBy where q.type = :type and q.refId = :refId")
    Optional<QueueItem> findByTypeAndRefIdForUpdate(@Param("type") QueueType type, @Param("refId") String refId);

    boolean existsByClaimedBy_Id(java.util.UUID accountId);

    @Query("select q from QueueItem q left join fetch q.claimedBy "
            + "where q.type in :types and q.refId in :refIds")
    List<QueueItem> findByTypesAndRefIds(@Param("types") Collection<QueueType> types,
                                         @Param("refIds") Collection<String> refIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update QueueItem q set q.claimedBy = :moderator, q.claimedAt = :now "
            + "where q.id = :id and q.claimedBy is null")
    int claimIfFree(@Param("id") String id, @Param("moderator") Account moderator, @Param("now") Instant now);
}
