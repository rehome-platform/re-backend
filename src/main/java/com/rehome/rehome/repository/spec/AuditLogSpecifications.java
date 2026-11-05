package com.rehome.rehome.repository.spec;

import com.rehome.rehome.entity.audit.AuditLog;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class AuditLogSpecifications {

    private AuditLogSpecifications() {}

    // Tham số null hoặc rỗng thì bỏ qua điều kiện đó.
    // Khoảng thời gian là [from, to): gồm from, không gồm to.
    public static Specification<AuditLog> filter(UUID actorAccountId,
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
            if (actionTypes != null && !actionTypes.isEmpty()) {
                predicates.add(root.get("actionType").in(actionTypes));
            }
            if (targetType != null && !targetType.isBlank()) {
                predicates.add(cb.equal(root.get("targetType"), targetType.trim()));
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
