package com.rehome.rehome.entity.marketplace;

import com.rehome.rehome.entity.Account;
import com.rehome.rehome.entity.enums.ProductStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {
    // Mã SKU Moderator gán lúc nhập kho, ví dụ Q7-AK-000845
    @Id
    @Column(name = "id", nullable = false, length = 255)
    private String id;

    // Biên nhận sinh ra sản phẩm này, quan hệ 1-1
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consignment_receipt_id", nullable = false, unique = true)
    private ConsignmentReceipt consignmentReceipt;

    // Người ký gửi, cũng là người nhận tiền khi bán được
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Account member;

    // Kho đang giữ hàng
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    // Danh mục, cũng quyết định slot thử đồ Áo hay Quần
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    // Thương hiệu
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;

    // Tên sản phẩm hiển thị trên sàn
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    // Kích cỡ, ví dụ M
    @Column(name = "size", nullable = true, length = 255)
    private String size;

    // Tình trạng theo kết quả kiểm định của Moderator
    @Column(name = "condition_note", nullable = false, columnDefinition = "text")
    private String conditionNote;

    // Giá đang bán; Member hạ được nhưng không dưới giá sàn
    @Column(name = "current_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal currentPrice;

    // ON_SALE / RESERVED / SOLD / WITHDRAWN / UNCLAIMED / DONATED / DESTROYED
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @ColumnDefault("'ON_SALE'")
    @Builder.Default
    private ProductStatus status = ProductStatus.ON_SALE;

    // Kỳ ký gửi đang chạy: 1, 2 hay 3 — quyết định tỷ lệ hoa hồng
    @Column(name = "current_term", nullable = false)
    @ColumnDefault("1")
    @Builder.Default
    private Short currentTerm = (short) 1;

    // Ngày bắt đầu kỳ hiện tại, mốc tính cửa sổ rút miễn phí 7 ngày
    @Column(name = "term_started_at", nullable = false)
    private Instant termStartedAt;

    // Ngày hết kỳ cuối (ngày 90), lúc này gỡ khỏi sàn
    @Column(name = "end_of_term_deadline", nullable = true)
    private Instant endOfTermDeadline;

    // Hạn 30 ngày Member chọn cách xử lý cuối kỳ; quá hạn thành UNCLAIMED
    @Column(name = "unclaimed_decision_deadline", nullable = true)
    private Instant unclaimedDecisionDeadline;

    // Vị trí kệ trong kho để lấy hàng nhanh
    @Column(name = "shelf_location", nullable = true, length = 255)
    private String shelfLocation;

    // Thời điểm nhập kho
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Thời điểm cập nhật gần nhất
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
