package com.rehome.rehome.repository.spec;

import com.rehome.rehome.entity.audit.AuditLog;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AuditLogSpecifications {

    private AuditLogSpecifications() {}

    // Tham số null hoặc rỗng thì bỏ qua điều kiện đó
    public static Specification<AuditLog> filter(UUID actorAccountId, String actionType, String targetType) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (actorAccountId != null) {
                predicates.add(cb.equal(root.get("actorAccount").get("id"), actorAccountId));
            }
            if (actionType != null && !actionType.isBlank()) {
                predicates.add(cb.equal(root.get("actionType"), actionType.trim()));
            }
            if (targetType != null && !targetType.isBlank()) {
                predicates.add(cb.equal(root.get("targetType"), targetType.trim()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
