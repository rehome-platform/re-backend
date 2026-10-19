package com.rehome.rehome.repository.spec;

import com.rehome.rehome.entity.enums.WalletTransactionType;
import com.rehome.rehome.entity.enums.WalletType;
import com.rehome.rehome.entity.marketplace.WalletTransaction;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WalletTransactionSpecifications {

    private WalletTransactionSpecifications() {
    }

    public static Specification<WalletTransaction> filter(UUID walletId, List<WalletTransactionType> types,
                                                            String direction) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("walletType"), WalletType.MEMBER));
            predicates.add(cb.equal(root.get("walletId"), walletId));

            if (types != null && !types.isEmpty()) {
                predicates.add(root.get("type").in(types));
            }

            if ("IN".equalsIgnoreCase(direction)) {
                predicates.add(cb.greaterThan(root.get("amount"), BigDecimal.ZERO));
            } else if ("OUT".equalsIgnoreCase(direction)) {
                predicates.add(cb.lessThan(root.get("amount"), BigDecimal.ZERO));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
