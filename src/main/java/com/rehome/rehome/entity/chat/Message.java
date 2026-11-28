package com.rehome.rehome.entity.chat;

import com.rehome.rehome.entity.auth.Account;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
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
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_account_id", nullable = false)
    private Account senderAccount;

    @Column(name = "content", nullable = true, columnDefinition = "text")
    private String content;

    @Column(name = "original_content", nullable = true, columnDefinition = "text")
    private String originalContent;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "read_at", nullable = true)
    private Instant readAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "message_image_urls", joinColumns = @JoinColumn(name = "message_id"))
    @Column(name = "image_url", nullable = false, length = 2048)
    @OrderColumn(name = "position")
    private List<String> imageUrls;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (sentAt == null) sentAt = now;
    }
}
