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

    // API filter-options: chỉ lấy loại thao tác thực sự có trong log
    @Query("select distinct l.actionType from AuditLog l order by l.actionType")
    List<String> findDistinctActionTypes();

    // API filter-options: chỉ lấy người thực hiện thực sự có trong log (id, chưa nạp Account)
    @Query("select distinct l.actorAccount.id from AuditLog l")
    List<UUID> findDistinctActorIds();

    // API 25: số lần và lần gần nhất một đối tượng bị tác động bởi actionType (vd BAN_ACCOUNT), gom theo targetId.
    // Mỗi phần tử: [targetId (String), count (Long), max(createdAt) (Instant)]
    @Query("select l.targetId, count(l), max(l.createdAt) from AuditLog l "
            + "where l.actionType = :actionType and l.targetType = :targetType and l.targetId in :targetIds "
            + "group by l.targetId")
    List<Object[]> summarizeByTarget(@Param("actionType") String actionType,
                                     @Param("targetType") String targetType,
                                     @Param("targetIds") Collection<String> targetIds);
}
