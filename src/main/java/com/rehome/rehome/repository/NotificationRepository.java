package com.rehome.rehome.repository;

import com.rehome.rehome.entity.chat.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByRecipientAccount_Id(UUID recipientAccountId, Pageable pageable);

    // Kèm recipient vào điều kiện để đánh dấu đọc không đọc được thông báo của người khác
    Optional<Notification> findByIdAndRecipientAccount_Id(UUID id, UUID recipientAccountId);

    long countByRecipientAccount_IdAndIsReadFalse(UUID recipientAccountId);

    @Modifying
    @Query("update Notification n set n.isRead = true "
            + "where n.recipientAccount.id = :accountId and n.isRead = false")
    void markAllAsRead(@Param("accountId") UUID accountId);
}
