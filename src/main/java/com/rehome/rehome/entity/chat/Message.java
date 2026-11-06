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

    // Nội dung tin nhắn; có thể null nếu tin nhắn chỉ gửi ảnh (imageUrls không rỗng)
    @Column(name = "content", nullable = true, columnDefinition = "text")
    private String content;

    // Thời điểm gửi
    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    // Thời điểm người nhận đọc; null là chưa đọc
    @Column(name = "read_at", nullable = true)
    private Instant readAt;

    // Ảnh gửi kèm tin nhắn; content được phép rỗng nếu có ảnh
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
