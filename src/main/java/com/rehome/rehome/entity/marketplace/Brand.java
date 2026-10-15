package com.rehome.rehome.entity.marketplace;

import com.rehome.rehome.entity.enums.CatalogStatus;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "brands")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Brand {
    // Khóa chính thương hiệu
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    // Tên thương hiệu được nhận ký gửi
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    // ACTIVE / HIDDEN — ẩn thì không nhận ký gửi mới, dữ liệu cũ vẫn giữ
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @ColumnDefault("'ACTIVE'")
    @Builder.Default
    private CatalogStatus status = CatalogStatus.ACTIVE;
}
