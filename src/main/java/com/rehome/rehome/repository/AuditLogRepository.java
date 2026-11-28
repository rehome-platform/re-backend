package com.rehome.rehome.repository;

import com.rehome.rehome.entity.audit.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    @Query(value = "select l from AuditLog l join fetch l.actorAccount "
            + "where l.warehouseId = :warehouseId and l.auditGroup = :auditGroup "
            + "order by l.createdAt desc, l.id desc",
            countQuery = "select count(l) from AuditLog l "
                    + "where l.warehouseId = :warehouseId and l.auditGroup = :auditGroup")
    org.springframework.data.domain.Page<AuditLog> findWarehouseHistory(
            @Param("warehouseId") UUID warehouseId, @Param("auditGroup") String auditGroup,
            org.springframework.data.domain.Pageable pageable);

    @Query("select distinct l.actionType from AuditLog l order by l.actionType")
    List<String> findDistinctActionTypes();

    @Query("select distinct l.actorAccount.id from AuditLog l where l.actorAccount is not null")
    List<UUID> findDistinctActorIds();

    @Query("select l.targetId, count(l), max(l.createdAt) from AuditLog l "
            + "where l.actionType = :actionType and l.targetType = :targetType and l.targetId in :targetIds "
            + "group by l.targetId")
    List<Object[]> summarizeByTarget(@Param("actionType") String actionType,
                                     @Param("targetType") String targetType,
                                     @Param("targetIds") Collection<String> targetIds);
}
