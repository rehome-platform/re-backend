package com.rehome.rehome.entity.marketplace;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

/**
 * Nội dung các Điều khoản dạng đoạn văn trong biên nhận ký gửi, do Admin soạn
 * (Điều 1, 2, 3 và "Vô thừa nhận" vẫn cố định trong code vì gắn bảng giá/bảng kỳ).
 *
 * <p>{@code bodyTemplate} có thể chứa placeholder {@code {{floorPrice}}},
 * {@code {{withdrawalFee}}}, {@code {{freeWithdrawalWindowDays}}},
 * {@code {{degradedCompensationRate}}} — được thay bằng số liệu thật của từng biên nhận lúc tạo
 * (xem {@code ReceiptClauseRenderer}).
 *
 * <p>Không có snapshot riêng ở đây: mỗi {@link ConsignmentReceipt} tự lưu lại bản đã render vào
 * {@code clausesSnapshot} ngay lúc tạo, nên Admin sửa/xoá template sau đó không ảnh hưởng biên
 * nhận đã có — chỉ áp dụng cho biên nhận tạo mới.
 */
@Entity
@Table(name = "receipt_clause_templates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceiptClauseTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    // Thứ tự hiển thị giữa các điều dạng đoạn văn; số "Điều" thật được tính lại lúc render,
    // không lưu cứng ở đây để chèn/xoá điều mới không phải đánh số lại tay
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "body_template", nullable = false, columnDefinition = "text")
    private String bodyTemplate;

    // Tắt = không áp dụng cho biên nhận tạo mới nữa, nhưng biên nhận cũ đã snapshot vẫn giữ nguyên
    @Column(name = "active", nullable = false)
    @ColumnDefault("true")
    @Builder.Default
    private Boolean active = true;

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
