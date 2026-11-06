package com.rehome.rehome.repository.spec;

import com.rehome.rehome.entity.audit.AuditLog;
import com.rehome.rehome.entity.auth.Account;
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

    // Tham số null hoặc rỗng thì bỏ qua điều kiện đó.
    // Khoảng thời gian là [from, to): gồm from, không gồm to.
    //
    // search: từ khoá tự do, không phân biệt hoa thường, khớp MỘT trong các trường sau:
    //   - actionType, targetId (id Member/tổ chức bị tác động), reason trong detail (JSON)
    //   - người thực hiện: id hoặc email của actor
    //   - matchedAccountIds: id các tài khoản có họ tên/tên tổ chức khớp từ khoá (service tra sẵn);
    //     dùng để khớp cả actor lẫn targetId, giúp tìm theo tên thay vì chỉ theo id
    public static Specification<AuditLog> filter(UUID actorAccountId,
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
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                Join<AuditLog, Account> actor = root.join("actorAccount");

                List<Predicate> anyOf = new ArrayList<>();
                anyOf.add(cb.like(cb.lower(root.<String>get("actionType")), pattern));
                anyOf.add(cb.like(cb.lower(root.<String>get("targetId")), pattern));
                // reason nằm trong cột JSONB detail ({"reason": "...", "beforeStatus": ...}); đọc đúng khoá
                // reason để từ khoá như "status" không khớp nhầm tên khoá. Hàm này chỉ có trên PostgreSQL.
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
                // Chuẩn hóa chữ hoa: targetType luôn lưu dạng hằng số (ACCOUNT, ORDER, ...)
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
