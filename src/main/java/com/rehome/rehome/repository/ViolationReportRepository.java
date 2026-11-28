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
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("""
            select r from ViolationReport r
            where r.status = com.rehome.rehome.entity.enums.ViolationReportStatus.PENDING and (
                (r.targetType = com.rehome.rehome.entity.enums.ViolationTargetType.ORGANIZATION_ACCOUNT
                    and r.targetId = :organizationId)
                or (r.targetType = com.rehome.rehome.entity.enums.ViolationTargetType.POST and r.targetId in
                    (select p.id from CommunityPost p where p.organization.accountId = :organizationId))
                or (r.targetType = com.rehome.rehome.entity.enums.ViolationTargetType.CAMPAIGN and r.targetId in
                    (select c.id from Campaign c where c.organization.accountId = :organizationId))
            ) order by r.id
            """)
    List<ViolationReport> findPendingForOrganization(@org.springframework.data.repository.query.Param("organizationId") UUID organizationId);


    @Query("select v.targetId, count(v) from ViolationReport v "
            + "where v.targetType = :targetType and v.targetId in :targetIds and v.status = :status "
            + "group by v.targetId")
    List<Object[]> countByTargetTypeAndTargetIdInAndStatus(@Param("targetType") ViolationTargetType targetType,
                                                            @Param("targetIds") Collection<UUID> targetIds,
                                                            @Param("status") ViolationReportStatus status);

    boolean existsByReporterMember_IdAndTargetTypeAndTargetIdAndStatus(
            UUID reporterId, ViolationTargetType targetType, UUID targetId, ViolationReportStatus status);

    List<ViolationReport> findAllByOrderByCreatedAtAsc();

    List<ViolationReport> findByStatusOrderByCreatedAtAsc(ViolationReportStatus status);

    List<ViolationReport> findByTargetTypeOrderByCreatedAtAsc(ViolationTargetType targetType);

    List<ViolationReport> findByStatusAndTargetTypeOrderByCreatedAtAsc(ViolationReportStatus status,
                                                                        ViolationTargetType targetType);

    List<ViolationReport> findByTargetTypeAndTargetIdOrderByCreatedAtAsc(ViolationTargetType targetType,
                                                                          UUID targetId);

    List<ViolationReport> findByTargetTypeAndTargetIdInAndStatus(ViolationTargetType targetType,
                                                                   Collection<UUID> targetIds,
                                                                   ViolationReportStatus status);

    List<ViolationReport> findByTargetTypeAndTargetIdAndStatusOrderByCreatedAtAsc(
            ViolationTargetType targetType, UUID targetId, ViolationReportStatus status);

    @Query("select v from ViolationReport v where v.status = com.rehome.rehome.entity.enums.ViolationReportStatus.RESOLVED "
            + "and ("
            + "  (v.targetType = com.rehome.rehome.entity.enums.ViolationTargetType.ORGANIZATION_ACCOUNT and v.targetId = :organizationId) "
            + "  or (v.targetType = com.rehome.rehome.entity.enums.ViolationTargetType.POST and v.targetId in "
            + "      (select p.id from CommunityPost p where p.organization.accountId = :organizationId)) "
            + "  or (v.targetType = com.rehome.rehome.entity.enums.ViolationTargetType.CAMPAIGN and v.targetId in "
            + "      (select c.id from Campaign c where c.organization.accountId = :organizationId)) "
            + ") order by v.resolvedAt desc")
    List<ViolationReport> findResolvedByOrganizationId(@Param("organizationId") UUID organizationId);
}
