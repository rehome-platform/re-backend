package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.DonationNote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DonationNoteRepository extends JpaRepository<DonationNote, UUID> {

    // OC-08: note quyên góp của 1 chiến dịch, mới nhất trước
    Page<DonationNote> findByCampaign_IdOrderByCreatedAtDesc(UUID campaignId, Pageable pageable);

    // F-167/OC-08: đếm số note để hiển thị donationNoteCount ở chi tiết chiến dịch
    long countByCampaign_Id(UUID campaignId);
}
