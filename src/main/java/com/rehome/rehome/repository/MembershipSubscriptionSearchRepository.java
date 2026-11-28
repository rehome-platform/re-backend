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

@Repository
public class MembershipSubscriptionSearchRepository {

    @PersistenceContext
    private EntityManager em;

    public Page<MemberSubscription> search(MembershipTier tier, SubscriptionStatus status,
                                            String keyword, Instant expiresBefore, Pageable pageable) {

        StringBuilder from = new StringBuilder(" from MemberSubscription s");
        if (keyword != null) {
            from.append(" left join MemberProfile mp on mp.accountId = s.member.id");
        }

        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();

        conditions.add("s.createdAt = (select max(s2.createdAt) from MemberSubscription s2"
                + " where s2.member = s.member)");

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
