package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.DonationNote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DonationNoteRepository extends JpaRepository<DonationNote, UUID> {

    Page<DonationNote> findByCampaign_IdOrderByCreatedAtDesc(UUID campaignId, Pageable pageable);

    long countByCampaign_Id(UUID campaignId);
}
