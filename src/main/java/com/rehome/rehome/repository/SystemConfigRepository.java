package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SystemConfigRepository extends JpaRepository<SystemConfig, UUID> {
    Optional<SystemConfig> findByKey(String key);
}
