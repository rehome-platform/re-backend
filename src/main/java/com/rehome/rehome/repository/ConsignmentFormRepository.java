package com.rehome.rehome.repository;

import com.rehome.rehome.entity.enums.ConsignmentFormStatus;
import com.rehome.rehome.entity.marketplace.ConsignmentForm;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ConsignmentFormRepository extends JpaRepository<ConsignmentForm, UUID> {

    Page<ConsignmentForm> findByMemberId(UUID memberId, Pageable pageable);
    List<ConsignmentForm> findByMember_IdAndStatusInOrderByCreatedAtAsc(
            UUID memberId, Collection<ConsignmentFormStatus> statuses);
    Page<ConsignmentForm> findByMemberIdAndStatus(UUID memberId, ConsignmentFormStatus status, Pageable pageable);
}
