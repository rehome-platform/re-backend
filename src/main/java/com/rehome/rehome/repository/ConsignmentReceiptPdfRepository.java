package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.ConsignmentReceiptPdf;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsignmentReceiptPdfRepository extends JpaRepository<ConsignmentReceiptPdf, UUID> {
}
