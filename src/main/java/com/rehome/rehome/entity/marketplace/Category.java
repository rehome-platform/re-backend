package com.rehome.rehome.entity.marketplace;

import com.rehome.rehome.entity.enums.CatalogStatus;
import com.rehome.rehome.entity.enums.TryOnSlot;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {
    // Khóa chính danh mục
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    // Tên danh mục, ví dụ Áo khoác
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    // TOP / BOTTOM / DRESS — quyết định món vào slot Áo, Quần hay Đầm khi thử đồ AI
    @Enumerated(EnumType.STRING)
    @Column(name = "tryon_slot", nullable = false)
    private TryOnSlot tryonSlot;

    // ACTIVE / HIDDEN — ẩn thì không nhận ký gửi mới, dữ liệu cũ vẫn giữ
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @ColumnDefault("'ACTIVE'")
    @Builder.Default
    private CatalogStatus status = CatalogStatus.ACTIVE;


    private String code;
}
