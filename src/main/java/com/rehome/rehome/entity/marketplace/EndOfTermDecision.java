package com.rehome.rehome.entity.marketplace;

import com.rehome.rehome.entity.enums.EndOfTermChoice;
import jakarta.persistence.*;
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
}
