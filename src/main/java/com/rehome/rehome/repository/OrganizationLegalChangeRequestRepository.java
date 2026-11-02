package com.rehome.rehome.repository;

import com.rehome.rehome.entity.auth.OrganizationLegalChangeRequest;
import com.rehome.rehome.entity.enums.LegalChangeRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationLegalChangeRequestRepository
        extends JpaRepository<OrganizationLegalChangeRequest, UUID> {

    // OP-05: yêu cầu đổi thông tin pháp lý gần nhất của chính tổ chức (duyệt hay từ chối gì cũng lấy)
    Optional<OrganizationLegalChangeRequest> findFirstByOrganization_AccountIdOrderByCreatedAtDesc(
            UUID organizationAccountId);

    // Mỗi lần chỉ 1 yêu cầu đang chờ duyệt (wireframe OP-05) - chặn tạo mới khi còn PENDING
    boolean existsByOrganization_AccountIdAndStatus(UUID organizationAccountId, LegalChangeRequestStatus status);
}
