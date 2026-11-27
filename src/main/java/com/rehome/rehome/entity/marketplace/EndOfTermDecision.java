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
    // Khóa chính
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    // Sản phẩm đã hết 90 ngày trên sàn
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // TAKE_BACK / RE_CONSIGN / UNCLAIMED — null là Member chưa chọn
    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = true)
    private EndOfTermChoice decision;

    // Biên nhận mới nếu Member chọn ký gửi lại
    @OneToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "new_receipt_id", nullable = true, unique = true)
    private ConsignmentReceipt newReceipt;

    // Thời điểm Member chọn
    @Column(name = "decided_at", nullable = true)
    private Instant decidedAt;

    // Hạn 30 ngày phải chọn; quá hạn thành hàng vô thừa nhận
    @Column(name = "deadline", nullable = false)
    private Instant deadline;

    // Thời điểm Moderator bàn giao hàng tại quầy cho Member chọn TAKE_BACK — API 72
    @Column(name = "taken_back_at", nullable = true)
    private Instant takenBackAt;

    // ===== Thương lượng giá ký gửi lại (chỉ có ý nghĩa khi decision = RE_CONSIGN) =====

    // null cho tới khi Member chọn RE_CONSIGN; AWAITING_PRICE ngay sau đó
    @Enumerated(EnumType.STRING)
    @Column(name = "re_consign_status", nullable = true)
    private ReConsignStatus reConsignStatus;

    // Số lần Moderator đã đề xuất (bắt đầu từ 1 ở setReConsignTerms, tăng mỗi lần counter-response)
    @Column(name = "proposal_round", nullable = true)
    private Integer proposalRound;

    // Số lần Member đã đề xuất giá (counter-offer), so với re_consign_max_counter_rounds
    @Column(name = "counter_rounds_used", nullable = false)
    @org.hibernate.annotations.ColumnDefault("0")
    @Builder.Default
    private int counterRoundsUsed = 0;

    // Hạn Member phải xác nhận/đề xuất lại đề xuất hiện tại của Moderator (now + re_consign_confirm_days)
    @Column(name = "proposal_deadline", nullable = true)
    private Instant proposalDeadline;

    // Job nhắc 1 ngày trước proposalDeadline chỉ gửi 1 lần mỗi vòng đề xuất; reset về null mỗi khi
    // proposalDeadline dời sang vòng mới (setReConsignTerms / counter-response)
    @Column(name = "proposal_reminder_sent_at", nullable = true)
    private Instant proposalReminderSentAt;

    // Đề xuất gần nhất của Member (counter-offer)
    @Column(name = "member_counter_price", precision = 19, scale = 2)
    private BigDecimal memberCounterPrice;

    @Column(name = "member_counter_note", columnDefinition = "text")
    private String memberCounterNote;

    @Column(name = "countered_at", nullable = true)
    private Instant counteredAt;

    // Phản hồi gần nhất của Moderator cho đề xuất của Member
    @Enumerated(EnumType.STRING)
    @Column(name = "moderator_response", nullable = true)
    private ReConsignCounterResponseAction moderatorResponse;

    @Column(name = "moderator_response_note", columnDefinition = "text")
    private String moderatorResponseNote;

    @Column(name = "responded_at", nullable = true)
    private Instant respondedAt;

    // Member từ chối (decline) hoặc job đánh dấu EXPIRED
    @Column(name = "declined_at", nullable = true)
    private Instant declinedAt;

    @Column(name = "decline_reason", columnDefinition = "text")
    private String declineReason;

    @Version
    @Column(nullable = false)
    @org.hibernate.annotations.ColumnDefault("0")
    private long version;
}
