package com.rehome.rehome.entity.marketplace;

import com.rehome.rehome.entity.enums.ViolationWordAction;
import com.rehome.rehome.entity.enums.ViolationWordCategory;
import com.rehome.rehome.entity.enums.ViolationWordSeverity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "violation_words")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ViolationWord {
    // Khóa chính
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    // Từ khóa đã chuẩn hóa: trim, chữ thường, gộp khoảng trắng; duy nhất toàn hệ thống
    @Column(name = "word", nullable = false, unique = true, length = 255)
    private String word;

    // PROFANITY / FRAUD / COUNTERFEIT / SPAM / INAPPROPRIATE
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private ViolationWordCategory category;

    // WARNING / BLOCKED
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 50)
    private ViolationWordSeverity severity;

    // AUTO_FLAG / AUTO_REJECT / NOTIFY_ADMIN
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 50)
    private ViolationWordAction action;

    // Tắt thì từ khóa được giữ lại nhưng không còn áp dụng
    @Column(name = "is_active", nullable = false)
    @ColumnDefault("true")
    @Builder.Default
    private Boolean isActive = true;

    // Lý do / ghi chú của Admin khi thêm từ khóa
    @Column(name = "note", nullable = true, columnDefinition = "text")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = Instant.now();
    }
}
