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

    List<ReceiptClauseTemplate> findAllByActiveTrueOrderByDisplayOrderAsc();

    Optional<ReceiptClauseTemplate> findTopByOrderByDisplayOrderDesc();
}
