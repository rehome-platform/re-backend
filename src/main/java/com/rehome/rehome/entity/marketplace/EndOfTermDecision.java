package com.rehome.rehome.entity.marketplace;

import com.rehome.rehome.entity.enums.EndOfTermChoice;
import com.rehome.rehome.entity.enums.ReConsignCounterResponseAction;
import com.rehome.rehome.entity.enums.ReConsignStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "end_of_term_decisions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EndOfTermDecision {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = true)
    private EndOfTermChoice decision;

    @OneToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "new_receipt_id", nullable = true, unique = true)
    private ConsignmentReceipt newReceipt;

    @Column(name = "decided_at", nullable = true)
    private Instant decidedAt;

    @Column(name = "deadline", nullable = false)
    private Instant deadline;

    @Column(name = "taken_back_at", nullable = true)
    private Instant takenBackAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "re_consign_status", nullable = true)
    private ReConsignStatus reConsignStatus;

    @Column(name = "proposal_round", nullable = true)
    private Integer proposalRound;

    @Column(name = "counter_rounds_used", nullable = false)
    @org.hibernate.annotations.ColumnDefault("0")
    @Builder.Default
    private int counterRoundsUsed = 0;

    @Column(name = "proposal_deadline", nullable = true)
    private Instant proposalDeadline;

    @Column(name = "proposal_reminder_sent_at", nullable = true)
    private Instant proposalReminderSentAt;

    @Column(name = "member_counter_price", precision = 19, scale = 2)
    private BigDecimal memberCounterPrice;

    @Column(name = "member_counter_note", columnDefinition = "text")
    private String memberCounterNote;

    @Column(name = "countered_at", nullable = true)
    private Instant counteredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderator_response", nullable = true)
    private ReConsignCounterResponseAction moderatorResponse;

    @Column(name = "moderator_response_note", columnDefinition = "text")
    private String moderatorResponseNote;

    @Column(name = "responded_at", nullable = true)
    private Instant respondedAt;

    @Column(name = "declined_at", nullable = true)
    private Instant declinedAt;

    @Column(name = "decline_reason", columnDefinition = "text")
    private String declineReason;

    @Version
    @Column(nullable = false)
    @org.hibernate.annotations.ColumnDefault("0")
    private long version;
}
