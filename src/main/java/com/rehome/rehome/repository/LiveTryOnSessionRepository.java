package com.rehome.rehome.repository;

import com.rehome.rehome.entity.enums.LiveTryOnStatus;
import com.rehome.rehome.entity.tryon.LiveTryOnSession;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LiveTryOnSessionRepository extends JpaRepository<LiveTryOnSession, UUID> {

    long countByMember_IdAndFirstFrameAtGreaterThanEqualAndFirstFrameAtLessThan(
            UUID memberId, Instant from, Instant to);

    long countByMember_IdAndFirstFrameAtIsNullAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            UUID memberId, Instant from, Instant to);

    Optional<LiveTryOnSession> findByIdAndMember_Id(UUID id, UUID memberId);

    @EntityGraph(attributePaths = "product")
    Page<LiveTryOnSession> findByMember_IdAndStatusAndVideoUrlIsNotNull(
            UUID memberId, LiveTryOnStatus status, Pageable pageable);
}
