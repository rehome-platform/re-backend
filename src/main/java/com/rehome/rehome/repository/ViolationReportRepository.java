package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.ViolationReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ViolationReportRepository extends JpaRepository<ViolationReport, UUID> {
}
