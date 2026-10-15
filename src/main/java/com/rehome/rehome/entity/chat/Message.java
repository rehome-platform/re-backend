package com.rehome.rehome.entity.chat;

import com.rehome.rehome.entity.auth.Account;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {
    // Khóa chính
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    // Hội thoại chứa tin nhắn
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    // Người gửi
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_account_id", nullable = false)
    private Account senderAccount;

    // Nội dung tin nhắn
    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    // Thời điểm gửi
    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    // Thời điểm người nhận đọc; null là chưa đọc
    @Column(name = "read_at", nullable = true)
    private Instant readAt;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (sentAt == null) sentAt = now;
    }
}
