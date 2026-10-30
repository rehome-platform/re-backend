package com.rehome.rehome.entity.marketplace;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "consignment_form_photos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsignmentFormPhoto {
    // Khóa chính
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    // Form chứa ảnh này
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "form_id", nullable = false)
    private ConsignmentForm form;

    // Ảnh món đồ Member chụp kèm form
    @Column(name = "image_url", nullable = false, length = 2048)
    private String imageUrl;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;
}
