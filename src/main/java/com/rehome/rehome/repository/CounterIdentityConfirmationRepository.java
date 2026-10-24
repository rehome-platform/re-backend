package com.rehome.rehome.repository;

import com.rehome.rehome.entity.counter.CounterIdentityConfirmation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CounterIdentityConfirmationRepository extends JpaRepository<CounterIdentityConfirmation, UUID> {
}
