package com.rehome.rehome.repository.spec;

import com.rehome.rehome.entity.enums.ViolationWordCategory;
import com.rehome.rehome.entity.enums.ViolationWordSeverity;
import com.rehome.rehome.entity.marketplace.ViolationWord;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public class ViolationWordSpecifications {

    private ViolationWordSpecifications() {}

    // Tham số null (hoặc keyword rỗng) thì bỏ qua điều kiện đó. Dùng Specification thay vì
    // "(:p is null or ...)" vì Postgres không suy được kiểu của tham số null.
    public static Specification<ViolationWord> filter(String keyword,
                                                      ViolationWordCategory category,
                                                      ViolationWordSeverity severity,
                                                      Boolean isActive) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("word")),
                        "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (severity != null) {
                predicates.add(cb.equal(root.get("severity"), severity));
            }
            if (isActive != null) {
                predicates.add(cb.equal(root.get("isActive"), isActive));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
