package com.rehome.rehome.entity.tryon;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.LiveTryOnStatus;
import com.rehome.rehome.entity.marketplace.Product;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

/**
 * Một lượt thử đồ AI real-time (Decart Lucy VTON) qua camera. Khác {@link TryOnImage}: đây là
 * video 10 giây, quay trực tiếp trên app, không lưu lại vào bộ sưu tập F-080.
 *
 * <p>Mỗi row được tạo ngay khi mint client token cho Decart (trước khi biết Member có kết nối
 * camera thành công hay không) - đây là lúc quota 2 lượt/ngày bị trừ, vì đây là checkpoint duy
 * nhất backend kiểm soát được trước khi phát sinh chi phí stream thật.
 */
@Entity
@Table(name = "live_tryon_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LiveTryOnSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    // Chủ phiên thử đồ
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Account member;

    // Món được thử (lấy ảnh làm garment reference gửi cho Decart)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // Model Decart dùng cho phiên này, ví dụ lucy-vton-3.5 - ghi lại để đối chiếu chi phí sau này
    @Column(name = "model_id", nullable = false, length = 100)
    private String modelId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private LiveTryOnStatus status = LiveTryOnStatus.STARTED;

    // Mobile tự báo lại sau khi quay xong, chỉ để audit/đối chiếu chi phí - không dùng để tính quota
    @Column(name = "actual_duration_seconds", nullable = true)
    private Integer actualDurationSeconds;

    // URL video kết quả sau khi mobile upload xong; null khi còn STARTED hoặc FAILED không có video
    @Column(name = "video_url", nullable = true, length = 2048)
    private String videoUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at", nullable = true)
    private Instant completedAt;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
    }
}
