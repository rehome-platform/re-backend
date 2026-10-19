package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.ConsignmentForm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ConsignmentFormRepository extends JpaRepository<ConsignmentForm, UUID> {
}
