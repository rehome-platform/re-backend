package com.rehome.rehome.entity.chat;

import com.rehome.rehome.entity.Account;
import com.rehome.rehome.entity.enums.ConversationType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "conversations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation {
    // Khóa chính
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    // MEMBER_ORGANIZATION / MEMBER_WAREHOUSE — quyết định counterpart_id là gì
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private ConversationType type;

    // Member trong cuộc hội thoại
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Account member;

    // Khóa đa hình: OrganizationProfile.account_id hoặc Warehouse.id tùy type — không đặt FK được vì trỏ hai bảng
    @Column(name = "counterpart_id", nullable = false)
    private UUID counterpartId;

    // Thời điểm mở hội thoại
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
    }
}
