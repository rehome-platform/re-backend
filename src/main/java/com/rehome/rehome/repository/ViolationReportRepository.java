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

    // Chặn báo cáo trùng (409 DUPLICATE_PENDING_REPORT): Member đã có báo cáo PENDING cho đúng target này
    boolean existsByReporterMember_IdAndTargetTypeAndTargetIdAndStatus(
            UUID reporterId, ViolationTargetType targetType, UUID targetId, ViolationReportStatus status);

    // GET /violation-reports: lọc theo status/type (4 nhánh - null = không lọc tiêu chí đó), gom
    // nhóm theo target ở tầng service. Luôn asc theo createdAt để phần tử đầu/cuối mỗi nhóm chính
    // là báo cáo cũ nhất/mới nhất, không cần sort lại trong nhóm.
    List<ViolationReport> findAllByOrderByCreatedAtAsc();

    List<ViolationReport> findByStatusOrderByCreatedAtAsc(ViolationReportStatus status);

    List<ViolationReport> findByTargetTypeOrderByCreatedAtAsc(ViolationTargetType targetType);

    List<ViolationReport> findByStatusAndTargetTypeOrderByCreatedAtAsc(ViolationReportStatus status,
                                                                        ViolationTargetType targetType);

    // Mọi báo cáo của cùng 1 target (dùng cho detail "các báo cáo khác" và để đóng hết lúc resolve/dismiss)
    List<ViolationReport> findByTargetTypeAndTargetIdOrderByCreatedAtAsc(ViolationTargetType targetType,
                                                                          UUID targetId);

    List<ViolationReport> findByTargetTypeAndTargetIdAndStatusOrderByCreatedAtAsc(
            ViolationTargetType targetType, UUID targetId, ViolationReportStatus status);

    // Lịch sử vi phạm của 1 tổ chức: báo cáo RESOLVED trên chính tài khoản tổ chức, hoặc trên
    // bài viết/chiến dịch do tổ chức đó tạo. targetId đa hình (không FK được) nên phải join bằng
    // subquery theo từng loại target thay vì join trực tiếp.
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
