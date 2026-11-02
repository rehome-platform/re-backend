package com.rehome.rehome.repository;

import com.rehome.rehome.entity.enums.ViolationReportStatus;
import com.rehome.rehome.entity.enums.ViolationTargetType;
import com.rehome.rehome.entity.marketplace.ViolationReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ViolationReportRepository extends JpaRepository<ViolationReport, UUID> {

    // OB-01: số báo cáo PENDING của từng bài, gom 1 query để tránh N+1
    @Query("select v.targetId, count(v) from ViolationReport v "
            + "where v.targetType = :targetType and v.targetId in :targetIds and v.status = :status "
            + "group by v.targetId")
    List<Object[]> countByTargetTypeAndTargetIdInAndStatus(@Param("targetType") ViolationTargetType targetType,
                                                            @Param("targetIds") Collection<UUID> targetIds,
                                                            @Param("status") ViolationReportStatus status);
}
