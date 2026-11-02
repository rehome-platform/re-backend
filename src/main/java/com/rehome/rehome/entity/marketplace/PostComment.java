package com.rehome.rehome.entity.marketplace;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ContentStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "post_comments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostComment {
    // Khóa chính
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    // Bài được bình luận
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private CommunityPost post;

    // Người bình luận: MEMBER hoặc ORGANIZATION (tổ chức trả lời trên bài của chính mình).
    // Đổi tên từ member_id -> author_id, chạy src/main/java/com/rehome/rehome/scripts/
    // 2026-11-02_post_comments_author_and_parent.sql trên Postgres vì ddl-auto=update không rename được cột.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private Account author;

    // Bình luận gốc đang được trả lời; null = bình luận gốc. Chỉ 1 cấp: nếu client trả lời
    // vào một reply thì CommunityPostServiceImpl.createComment tự gắn vào bình luận gốc của
    // reply đó, parent ở đây luôn là bình luận gốc (parent.parent luôn null).
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "parent_id", nullable = true)
    private PostComment parent;

    // Nội dung bình luận
    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    // VISIBLE / REMOVED — Moderator ẩn bình luận thì chuyển REMOVED; vẫn trả về dòng này
    // (có parentId/author/createdAt) nhưng content = null ở tầng response
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @ColumnDefault("'VISIBLE'")
    @Builder.Default
    private ContentStatus status = ContentStatus.VISIBLE;

    // Thời điểm bình luận
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
    }
}
