package com.rehome.rehome.repository;

import com.rehome.rehome.entity.auth.ModeratorProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ModeratorProfileRepository extends JpaRepository<ModeratorProfile, UUID> {

}
