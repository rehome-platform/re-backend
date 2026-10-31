package com.rehome.rehome.repository;

import com.rehome.rehome.entity.chat.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByRecipientAccount_Id(UUID recipientAccountId, Pageable pageable);
}
