package com.rehome.rehome.repository;

import com.rehome.rehome.entity.audit.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    // API filter-options: chỉ lấy loại thao tác thực sự có trong log
    @Query("select distinct l.actionType from AuditLog l order by l.actionType")
    List<String> findDistinctActionTypes();

    // API filter-options: chỉ lấy người thực hiện thực sự có trong log (id, chưa nạp Account)
    @Query("select distinct l.actorAccount.id from AuditLog l")
    List<UUID> findDistinctActorIds();
}
