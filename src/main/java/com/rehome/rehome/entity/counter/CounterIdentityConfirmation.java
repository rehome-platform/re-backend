package com.rehome.rehome.entity.counter;

import com.rehome.rehome.entity.auth.Account;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "counter_identity_confirmations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CounterIdentityConfirmation {
    // 1 dòng/account, ghi đè mỗi lần Moderator xác nhận lại danh tính tại quầy
    @Id
    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "confirmed_at", nullable = false)
    private Instant confirmedAt;

    // Hết hạn 15 phút sau khi xác nhận; bàn giao sau giờ này phải xác nhận lại
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "confirmed_by_moderator_id", nullable = false)
    private Account confirmedBy;
}
