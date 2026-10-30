package com.rehome.rehome.entity.marketplace;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ConsignmentFormStatus;
import com.rehome.rehome.entity.enums.ProductCondition;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "consignment_forms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsignmentForm {
    // Khóa chính của form ký gửi
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    // Member gửi form
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Account member;

    // Kho Member chọn mang đồ đến; null khi còn là DRAFT, được gán lúc gửi (POST /consignment-forms/submit)
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "warehouse_id", nullable = true)
    private Warehouse warehouse;

    // Danh mục của món đồ
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    // Thương hiệu của món đồ
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;

    // Tên món đồ do Member khai
    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    // Mô tả tình trạng do Member tự khai
    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    // Giá Member mong muốn; giá chốt thật do Moderator thỏa thuận lại
    @Column(name = "desired_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal desiredPrice;

    // DRAFT / PENDING / APPROVED / REJECTED / EXPIRED / CANCELLED / INSPECTING; DRAFT = nháp trong "Tủ đồ"
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @ColumnDefault("'PENDING'")
    @Builder.Default
    private ConsignmentFormStatus status = ConsignmentFormStatus.PENDING;

    // Lý do từ chối, để Member sửa form
    @Column(name = "reject_reason", nullable = true, columnDefinition = "text")
    private String rejectReason;

    // Hạn mang đồ đến kho sau khi duyệt, mặc định 7 ngày; quá hạn thành EXPIRED
    @Column(name = "bring_in_deadline", nullable = true)
    private LocalDate bringInDeadline;

    // Thời điểm gửi form
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Thời điểm sửa gần nhất
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Mã dễ đọc, ví dụ KG-000123
    @Column(name = "form_no", unique = true, length = 30)
    private String formNo;

    // Size Member khai: XS..XXL, Free size, hoặc 35..45 với giày
    @Column(name = "size", length = 50)
    private String size;

    // NEW_TAG / LIKE_NEW / GOOD / FLAW
    @Enumerated(EnumType.STRING)
    @Column(name = "item_condition", length = 30)
    private ProductCondition condition;

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
