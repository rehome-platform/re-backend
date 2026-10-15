package com.rehome.rehome.repository;

import com.rehome.rehome.entity.auth.EmailOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmailOtpRepository extends JpaRepository<EmailOtp, String> { }
