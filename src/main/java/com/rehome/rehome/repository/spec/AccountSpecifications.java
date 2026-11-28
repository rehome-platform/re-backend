package com.rehome.rehome.repository.spec;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.ModeratorProfile;
import com.rehome.rehome.entity.enums.AccountStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.util.PhoneNumbers;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class AccountSpecifications {

    private static final char ESCAPE = '\\';

    private AccountSpecifications() {}

    public static Specification<Account> moderators(UUID warehouseId, String search, AccountStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("role"), Role.MODERATOR));
            if (warehouseId != null) {
                predicates.add(cb.equal(root.get("warehouse").get("id"), warehouseId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (search != null && !search.isBlank()) {
                String keyword = PhoneNumbers.toSearchKeyword(search.toLowerCase(Locale.ROOT));
                String pattern = "%" + escapeLike(keyword) + "%";

                Subquery<Integer> byName = query.subquery(Integer.class);
                Root<ModeratorProfile> profile = byName.from(ModeratorProfile.class);
                byName.select(cb.literal(1)).where(
                        cb.equal(profile.get("accountId"), root.get("id")),
                        cb.like(cb.lower(profile.get("fullName")), pattern, ESCAPE));

                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("email")), pattern, ESCAPE),
                        cb.like(root.get("phone"), pattern, ESCAPE),
                        cb.exists(byName)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
