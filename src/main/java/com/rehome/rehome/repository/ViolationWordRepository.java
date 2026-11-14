package com.rehome.rehome.repository;

import com.rehome.rehome.entity.enums.ViolationWordCategory;
import com.rehome.rehome.entity.marketplace.ViolationWord;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Tìm kiếm kết hợp nhiều bộ lọc (từ khóa + nhóm + mức độ + trạng thái) đi qua JpaSpecificationExecutor
 * (xem ViolationWordSpecifications); các hàm derived bên dưới dành cho tra cứu đơn lẻ.
 */
@Repository
public interface ViolationWordRepository
        extends JpaRepository<ViolationWord, UUID>, JpaSpecificationExecutor<ViolationWord> {

    java.util.List<ViolationWord> findAllByIsActiveTrue();

    Page<ViolationWord> findByWordContainingIgnoreCase(String keyword, Pageable pageable);

    Page<ViolationWord> findByCategory(ViolationWordCategory category, Pageable pageable);

    Page<ViolationWord> findByIsActive(Boolean isActive, Pageable pageable);

    boolean existsByWord(String word);

    boolean existsByWordAndIdNot(String word, UUID id);
}
