package com.rehome.rehome.repository;

import com.rehome.rehome.entity.enums.MembershipTier;
import com.rehome.rehome.entity.enums.SubscriptionStatus;
import com.rehome.rehome.entity.membership.MemberSubscription;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/**
 * Tìm kiếm thành viên VIP cho màn W-02, với 4 bộ lọc tùy chọn.
 *
 * <p><b>Vì sao không dùng {@code @Query} với {@code (:p is null or ...)}?</b> Đây là bài học đã có
 * trong repo - xem ghi chú đầu {@code AdminReportQueryRepository}: <i>Postgres không suy được kiểu
 * của tham số null</i> trong biểu thức đó và trả lỗi "could not determine data type of parameter".
 * Test chạy trên H2 nên lỗi này KHÔNG lộ ra lúc test, chỉ nổ khi Admin mở màn W-04 trên môi trường
 * thật. Vì vậy các điều kiện tùy chọn được <b>ghép vào câu JPQL khi có</b>, và tham số chỉ được
 * bind khi nó thực sự xuất hiện trong câu.
 *
 * <p>Tên/mã Member nằm ở {@code MemberProfile} (không có quan hệ khai báo từ {@code Account}), nên
 * phải dùng entity join có {@code on} - và chỉ join khi thật sự lọc theo {@code q}, để truy vấn
 * không lọc không phải cõng thêm một bảng.
 */
@Repository
public class MembershipSubscriptionSearchRepository {

    @PersistenceContext
    private EntityManager em;

    /**
     * @param tier          null = mọi hạng
     * @param status        null = mọi trạng thái
     * @param keyword       đã được chuẩn hóa thành {@code %chữ thường%}; null = không tìm theo từ khóa
     * @param expiresBefore null = không lọc theo "sắp hết hạn"
     */
    public Page<MemberSubscription> search(MembershipTier tier, SubscriptionStatus status,
                                            String keyword, Instant expiresBefore, Pageable pageable) {

        StringBuilder from = new StringBuilder(" from MemberSubscription s");
        if (keyword != null) {
            from.append(" left join MemberProfile mp on mp.accountId = s.member.id");
        }

        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();

        if (tier != null) {
            conditions.add("s.tier = :tier");
            params.put("tier", tier);
        }
        if (status != null) {
            conditions.add("s.status = :status");
            params.put("status", status);
        }
        if (keyword != null) {
            conditions.add("(lower(s.member.email) like :q"
                    + " or lower(mp.fullName) like :q"
                    + " or lower(mp.memberCode) like :q)");
            params.put("q", keyword);
        }
        if (expiresBefore != null) {
            // s.expiresAt null = subscription còn PENDING_PAYMENT, chưa có hạn -> không phải "sắp hết hạn"
            conditions.add("s.expiresAt is not null and s.expiresAt <= :expiresBefore");
            params.put("expiresBefore", expiresBefore);
        }

        String where = conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);

        TypedQuery<MemberSubscription> query = em.createQuery(
                "select s" + from + where + " order by s.createdAt desc", MemberSubscription.class);
        TypedQuery<Long> countQuery = em.createQuery(
                "select count(s)" + from + where, Long.class);
        params.forEach((name, value) -> {
            query.setParameter(name, value);
            countQuery.setParameter(name, value);
        });

        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        return new PageImpl<>(query.getResultList(), pageable, countQuery.getSingleResult());
    }
}
