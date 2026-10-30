package com.rehome.rehome.repository;

import com.rehome.rehome.entity.enums.ConsignmentFormStatus;
import com.rehome.rehome.entity.marketplace.ConsignmentForm;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsignmentFormRepository extends JpaRepository<ConsignmentForm, UUID> {

    List<ConsignmentForm> findByMember_IdAndStatusInOrderByCreatedAtAsc(
            UUID memberId, Collection<ConsignmentFormStatus> statuses);

    // API 34, 35: khóa dòng để 2 Moderator không xử lý cùng lúc
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from ConsignmentForm f where f.id = :id")
    Optional<ConsignmentForm> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = "warehouse")
    Page<ConsignmentForm> findByMemberId(UUID memberId, Pageable pageable);

    @EntityGraph(attributePaths = "warehouse")
    Page<ConsignmentForm> findByMemberIdAndStatus(UUID memberId, ConsignmentFormStatus status, Pageable pageable);

    // Danh sách "tất cả" không gồm nháp
    @EntityGraph(attributePaths = "warehouse")
    Page<ConsignmentForm> findByMemberIdAndStatusNot(UUID memberId, ConsignmentFormStatus status, Pageable pageable);

    long countByMember_IdAndStatus(UUID memberId, ConsignmentFormStatus status);

    @EntityGraph(attributePaths = {"warehouse", "category", "brand"})
    Optional<ConsignmentForm> findWithDetailsById(UUID id);

    @Query(value = "SELECT nextval('consignment_form_no_seq')", nativeQuery = true)
    long nextFormNo();
}
