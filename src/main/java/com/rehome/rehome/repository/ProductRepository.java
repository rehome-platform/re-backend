package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, String>, JpaSpecificationExecutor<Product> {

    // Khóa dòng để 2 request checkout cùng sản phẩm không cùng vượt qua kiểm tra ON_SALE
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p " +
            "join fetch p.warehouse " +
            "join fetch p.consignmentReceipt " +
            "where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") String id);
}
