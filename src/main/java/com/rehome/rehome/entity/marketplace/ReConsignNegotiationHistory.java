package com.rehome.rehome.entity.marketplace;

import com.rehome.rehome.entity.enums.ReConsignNegotiationAction;
import com.rehome.rehome.entity.enums.Role;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

// Một vòng thương lượng giá ký gửi lại cuối kỳ (Moderator đề xuất / Member đề xuất lại / Moderator
// phản hồi / từ chối / quá hạn), để hai bên xem lại toàn bộ lịch sử trên GET biên nhận.
@Entity
@Table(name = "re_consign_negotiation_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReConsignNegotiationHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "decision_id", nullable = false)
    private EndOfTermDecision decision;

    // Ai thực hiện hành động này: MEMBER hoặc MODERATOR
    @Enumerated(EnumType.STRING)
    @Column(name = "actor", nullable = false)
    private Role actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private ReConsignNegotiationAction action;

    // Giá đề xuất/chốt ở vòng này; null với DECLINE/EXPIRE
    @Column(name = "price", precision = 19, scale = 2)
    private BigDecimal price;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
