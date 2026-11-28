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
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "word", nullable = false, unique = true, length = 255)
    private String word;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private ViolationWordCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 50)
    private ViolationWordSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 50)
    private ViolationWordAction action;

    @Column(name = "is_active", nullable = false)
    @ColumnDefault("true")
    @Builder.Default
    private Boolean isActive = true;

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
