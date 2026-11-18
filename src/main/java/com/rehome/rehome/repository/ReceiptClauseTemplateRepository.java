package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.ReceiptClauseTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReceiptClauseTemplateRepository extends JpaRepository<ReceiptClauseTemplate, UUID> {

    List<ReceiptClauseTemplate> findAllByOrderByDisplayOrderAsc();

    // Dùng lúc tạo biên nhận mới: chỉ các điều đang bật mới được đưa vào snapshot
    List<ReceiptClauseTemplate> findAllByActiveTrueOrderByDisplayOrderAsc();

    // Để gán displayOrder kế tiếp khi Admin thêm điều mới
    Optional<ReceiptClauseTemplate> findTopByOrderByDisplayOrderDesc();
}
