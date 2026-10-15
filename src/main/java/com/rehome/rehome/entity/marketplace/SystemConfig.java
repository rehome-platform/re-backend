package com.rehome.rehome.entity.marketplace;

import com.rehome.rehome.entity.auth.Account;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "system_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemConfig {
    // Khóa chính
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    // Tên tham số, ví dụ commission_rate_term1, min_payout_amount
    @Column(name = "\"key\"", nullable = false, unique = true, length = 255)
    private String key;

    // Giá trị lưu dạng chuỗi, code tự ép kiểu khi đọc
    @Column(name = "\"value\"", nullable = false, length = 255)
    private String value;

    // Admin đã sửa tham số
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "updated_by", nullable = false)
    private Account updatedBy;

    // Thời điểm sửa; chỉ áp dụng cho giao dịch phát sinh sau đó
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = Instant.now();
    }
}
