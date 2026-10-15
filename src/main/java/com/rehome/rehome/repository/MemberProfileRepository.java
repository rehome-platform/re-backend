package com.rehome.rehome.repository;

import com.rehome.rehome.entity.auth.MemberProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberProfileRepository extends JpaRepository<MemberProfile, Long> {
}
