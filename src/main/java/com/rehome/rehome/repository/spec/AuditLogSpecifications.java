package com.rehome.rehome.repository.spec;

import com.rehome.rehome.entity.audit.AuditLog;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.Role;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class AuditLogSpecifications {

    private AuditLogSpecifications() {}

    public static Specification<AuditLog> filter(UUID actorAccountId,
                                                 Role actorRole,
                                                 String search,
                                                 Collection<UUID> matchedAccountIds,
                                                 Collection<String> actionTypes,
                                                 String targetType,
                                                 String targetId,
                                                 Instant from,
                                                 Instant to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (actorAccountId != null) {
                predicates.add(cb.equal(root.get("actorAccount").get("id"), actorAccountId));
            }
            if (actorRole != null) {
                predicates.add(cb.equal(root.get("actorAccount").get("role"), actorRole));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                Join<AuditLog, Account> actor = root.join("actorAccount", jakarta.persistence.criteria.JoinType.LEFT);

                List<Predicate> anyOf = new ArrayList<>();
                anyOf.add(cb.like(cb.lower(root.<String>get("actionType")), pattern));
                anyOf.add(cb.like(cb.lower(root.<String>get("targetId")), pattern));
                anyOf.add(cb.like(cb.lower(cb.function("jsonb_extract_path_text", String.class,
                        root.get("detail"), cb.literal("reason"))), pattern));
                anyOf.add(cb.like(cb.lower(actor.<String>get("email")), pattern));
                anyOf.add(cb.like(cb.lower(actor.get("id").as(String.class)), pattern));
                if (matchedAccountIds != null && !matchedAccountIds.isEmpty()) {
                    anyOf.add(actor.get("id").in(matchedAccountIds));
                    anyOf.add(root.<String>get("targetId")
                            .in(matchedAccountIds.stream().map(UUID::toString).toList()));
                }
                predicates.add(cb.or(anyOf.toArray(new Predicate[0])));
            }
            if (actionTypes != null && !actionTypes.isEmpty()) {
                predicates.add(root.get("actionType").in(actionTypes));
            }
            if (targetType != null && !targetType.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.<String>get("targetType")),
                        targetType.trim().toUpperCase(Locale.ROOT)));
            }
            if (targetId != null && !targetId.isBlank()) {
                predicates.add(cb.equal(root.get("targetId"), targetId.trim()));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.<Instant>get("createdAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
