package com.rehome.rehome.repository.spec;

import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.util.VietnameseText;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> filter(String search, UUID categoryId, UUID brandId,
                                                  BigDecimal priceMin, BigDecimal priceMax, UUID warehouseId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("status"), ProductStatus.ON_SALE));

            // So trên cột đã bỏ dấu để "ao khoac" khớp "Áo khoác"; từ khóa cũng phải bỏ dấu
            // để người gõ đủ dấu vẫn tìm được như cũ.
            if (search != null && !search.isBlank()) {
                String keyword = VietnameseText.normalize(search);
                predicates.add(cb.like(root.get("nameNormalized"), "%" + keyword + "%"));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (brandId != null) {
                predicates.add(cb.equal(root.get("brand").get("id"), brandId));
            }
            if (warehouseId != null) {
                predicates.add(cb.equal(root.get("warehouse").get("id"), warehouseId));
            }
            if (priceMin != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("currentPrice"), priceMin));
            }
            if (priceMax != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("currentPrice"), priceMax));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
