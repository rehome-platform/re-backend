package com.rehome.rehome.entity.marketplace;

import com.rehome.rehome.entity.enums.ReConsignNegotiationAction;
import com.rehome.rehome.entity.enums.Role;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "actor", nullable = false)
    private Role actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private ReConsignNegotiationAction action;

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
