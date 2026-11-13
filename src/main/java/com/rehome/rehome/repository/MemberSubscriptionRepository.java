package com.rehome.rehome.repository;

import com.rehome.rehome.entity.enums.MembershipTier;
import com.rehome.rehome.entity.enums.SubscriptionStatus;
import com.rehome.rehome.entity.membership.MemberSubscription;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MemberSubscriptionRepository extends JpaRepository<MemberSubscription, UUID> {

    /**
     * Khóa dòng ACTIVE của một Member trước khi tạo subscription mới. Không khóa thì hai request
     * mua gói song song cùng thấy "chưa có gói ACTIVE" và cùng tạo -> Member có 2 gói ACTIVE,
     * vi phạm ràng buộc nghiệp vụ mà DB không enforce được bằng unique index.
     *
     * <p>Cùng cơ chế với {@code WalletRepository.findByMemberIdForUpdate} ở OrderServiceImpl.
     * Dùng {@code setMaxResults(1)} ngầm qua Optional nên dữ liệu lỗi (2 dòng ACTIVE) sẽ ném
     * {@code IncorrectResultSizeDataAccessException} thay vì âm thầm lấy một dòng - cố ý, để lộ lỗi.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from MemberSubscription s where s.member.id = :memberId and s.status = "
            + "com.rehome.rehome.entity.enums.SubscriptionStatus.ACTIVE")
    Optional<MemberSubscription> findActiveByMemberIdForUpdate(@Param("memberId") UUID memberId);

    @EntityGraph(attributePaths = {"plan"})
    @Query("select s from MemberSubscription s where s.member.id = :memberId and s.status = "
            + "com.rehome.rehome.entity.enums.SubscriptionStatus.ACTIVE")
    Optional<MemberSubscription> findActiveByMemberId(@Param("memberId") UUID memberId);

    // Có giao dịch mua gói nào đang chờ thanh toán không (MEMBERSHIP_PAYMENT_PENDING 409)
    List<MemberSubscription> findAllByMember_IdAndStatus(UUID memberId, SubscriptionStatus status);

    @EntityGraph(attributePaths = {"plan"})
    Page<MemberSubscription> findAllByMember_Id(UUID memberId, Pageable pageable);

    long countByPlan_IdAndStatus(UUID planId, SubscriptionStatus status);

    // Admin tăng hạn mức của một hạng -> cập nhật snapshot của mọi subscription ACTIVE hạng đó.
    // Không phân trang: số người đang dùng một hạng là hữu hạn và thao tác này hiếm (Admin sửa gói).
    List<MemberSubscription> findAllByTierAndStatus(MembershipTier tier, SubscriptionStatus status);

    // Job 00:05: gói ACTIVE đã quá hạn -> EXPIRED
    @EntityGraph(attributePaths = {"member"})
    List<MemberSubscription> findAllByStatusAndExpiresAtBefore(SubscriptionStatus status, Instant moment);

    // Job 09:00 nhắc gia hạn: ACTIVE, còn hạn, hết hạn trước mốc cutoff, chưa gửi nhắc lần nào
    @EntityGraph(attributePaths = {"member", "plan"})
    @Query("select s from MemberSubscription s where s.status = :status "
            + "and s.expiresAt > :now and s.expiresAt <= :cutoff and s.expiryReminderSentAt is null")
    List<MemberSubscription> findExpiringSoon(@Param("status") SubscriptionStatus status,
                                              @Param("now") Instant now,
                                              @Param("cutoff") Instant cutoff);

    // Job 00:15 phát voucher: ACTIVE, còn hạn, chưa phát lần nào hoặc lần phát gần nhất đã quá 1 tháng
    @EntityGraph(attributePaths = {"member"})
    @Query("select s from MemberSubscription s where s.status = :status and s.expiresAt > :now "
            + "and (s.lastVoucherIssuedAt is null or s.lastVoucherIssuedAt <= :cutoff)")
    List<MemberSubscription> findDueForVoucherIssue(@Param("status") SubscriptionStatus status,
                                                     @Param("now") Instant now,
                                                     @Param("cutoff") Instant cutoff);

    @EntityGraph(attributePaths = {"member", "plan", "grantedByAdmin"})
    Optional<MemberSubscription> findWithDetailsById(UUID id);
}
