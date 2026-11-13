package com.rehome.rehome.repository;

import com.rehome.rehome.entity.enums.MemberVoucherStatus;
import com.rehome.rehome.entity.membership.MemberVoucher;
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
public interface MemberVoucherRepository extends JpaRepository<MemberVoucher, UUID> {

    /**
     * Khóa dòng voucher trước khi đánh dấu USED. Khóa xong phải ĐỌC LẠI {@code status} trong lock
     * rồi mới set - không khóa thì hai request đặt hàng song song cùng dùng một mã sẽ cùng vượt
     * qua bước kiểm tra. Đúng loại lỗi đã tránh ở {@code ProductRepository.findByIdForUpdate} và
     * {@code WalletRepository.findByMemberIdForUpdate}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from MemberVoucher v where upper(v.code) = upper(:code)")
    Optional<MemberVoucher> findByCodeForUpdate(@Param("code") String code);

    @EntityGraph(attributePaths = {"voucherTemplate"})
    Optional<MemberVoucher> findByCodeIgnoreCase(String code);

    /**
     * Ví voucher, KHÔNG lọc trạng thái: UNUSED trước, trong đó mã sắp hết hạn lên đầu. Thứ tự nằm
     * trong query nên {@code Pageable} truyền vào không được kèm {@code Sort}.
     *
     * <p>Cố ý tách thành hai method thay vì một câu có {@code (:status is null or ...)}: Postgres
     * không suy được kiểu của tham số null trong biểu thức đó (xem ghi chú ở
     * {@code AdminReportQueryRepository}), và lỗi đó không lộ ra khi test trên H2.
     */
    @EntityGraph(attributePaths = {"voucherTemplate"})
    @Query("""
            select v from MemberVoucher v
            where v.member.id = :memberId
            order by case when v.status = com.rehome.rehome.entity.enums.MemberVoucherStatus.UNUSED
                          then 0 else 1 end,
                     v.expiresAt asc
            """)
    Page<MemberVoucher> findWallet(@Param("memberId") UUID memberId, Pageable pageable);

    /**
     * Ví voucher của MỘT trạng thái (3 tab của màn M-04). Không cần sắp UNUSED-trước ở đây vì mọi
     * dòng cùng trạng thái - chỉ cần mã sắp hết hạn lên đầu.
     */
    @EntityGraph(attributePaths = {"voucherTemplate"})
    Page<MemberVoucher> findByMember_IdAndStatusOrderByExpiresAtAsc(
            UUID memberId, MemberVoucherStatus status, Pageable pageable);

    // Mã còn dùng được của Member, cho GET /vouchers/applicable
    @EntityGraph(attributePaths = {"voucherTemplate"})
    @Query("""
            select v from MemberVoucher v
            where v.member.id = :memberId
              and v.status = com.rehome.rehome.entity.enums.MemberVoucherStatus.UNUSED
              and v.expiresAt > :now
            order by v.expiresAt asc
            """)
    List<MemberVoucher> findUsable(@Param("memberId") UUID memberId, @Param("now") Instant now);

    long countByMember_IdAndStatus(UUID memberId, MemberVoucherStatus status);

    long countByMember_IdAndStatusAndExpiresAtBefore(UUID memberId, MemberVoucherStatus status, Instant moment);

    // Job 00:20: mã UNUSED đã quá hạn -> EXPIRED
    List<MemberVoucher> findAllByStatusAndExpiresAtBefore(MemberVoucherStatus status, Instant moment);

    // Idempotent cho VoucherIssueJob: đã phát bao nhiêu mã của mẫu này từ subscription này
    // trong khoảng [from, to)
    long countBySourceSubscription_IdAndVoucherTemplate_IdAndIssuedAtGreaterThanEqualAndIssuedAtLessThan(
            UUID subscriptionId, UUID templateId, Instant from, Instant to);

    @EntityGraph(attributePaths = {"voucherTemplate"})
    List<MemberVoucher> findAllBySourceSubscription_IdOrderByIssuedAtAsc(UUID subscriptionId);

    boolean existsByCode(String code);
}
