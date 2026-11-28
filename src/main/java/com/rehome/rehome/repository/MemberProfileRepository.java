package com.rehome.rehome.repository;

import com.rehome.rehome.entity.auth.MemberProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MemberProfileRepository extends JpaRepository<MemberProfile, UUID> {
    Optional<MemberProfile> findByAccountId(UUID accountId);
    Optional<MemberProfile> findByMemberCode(String memberCode);
    List<MemberProfile> findByAccount_Phone(String phone);
    List<MemberProfile> findAllByAccount_IdIn(Collection<UUID> accountIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update MemberProfile m set m.pointsBalance = m.pointsBalance + :points where m.accountId = :memberId")
    int addPoints(@Param("memberId") UUID memberId, @Param("points") int points);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update MemberProfile m set m.pointsBalance = m.pointsBalance - :cost, "
            + "m.bonusAiTryonCredits = m.bonusAiTryonCredits + :credits "
            + "where m.accountId = :memberId and m.pointsBalance >= :cost")
    int redeemForAiTryon(@Param("memberId") UUID memberId, @Param("cost") int cost, @Param("credits") int credits);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update MemberProfile m set m.pointsBalance = m.pointsBalance - :cost, "
            + "m.bonusLiveTryonCredits = m.bonusLiveTryonCredits + :credits "
            + "where m.accountId = :memberId and m.pointsBalance >= :cost")
    int redeemForLiveTryon(@Param("memberId") UUID memberId, @Param("cost") int cost, @Param("credits") int credits);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update MemberProfile m set m.bonusAiTryonCredits = m.bonusAiTryonCredits - 1 "
            + "where m.accountId = :memberId and m.bonusAiTryonCredits > 0")
    int consumeAiTryonCredit(@Param("memberId") UUID memberId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update MemberProfile m set m.bonusLiveTryonCredits = m.bonusLiveTryonCredits - 1 "
            + "where m.accountId = :memberId and m.bonusLiveTryonCredits > 0")
    int consumeLiveTryonCredit(@Param("memberId") UUID memberId);

    @Query("select m.bonusAiTryonCredits from MemberProfile m where m.accountId = :memberId")
    Optional<Integer> findBonusAiTryonCredits(@Param("memberId") UUID memberId);

    @Query("select m.bonusLiveTryonCredits from MemberProfile m where m.accountId = :memberId")
    Optional<Integer> findBonusLiveTryonCredits(@Param("memberId") UUID memberId);
}