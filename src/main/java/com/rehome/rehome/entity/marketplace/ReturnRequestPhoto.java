package com.rehome.rehome.entity.marketplace;

import com.rehome.rehome.entity.enums.ReturnPhotoSource;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "return_request_photos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequestPhoto {
    // Khóa chính
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    // Yêu cầu trả hàng chứa ảnh này
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "return_request_id", nullable = false)
    private ReturnRequest returnRequest;

    // Ảnh Member chụp làm bằng chứng
    @Column(name = "image_url", nullable = false, length = 2048)
    private String imageUrl;

    // MEMBER = ảnh gửi kèm lúc tạo yêu cầu; MODERATOR = ảnh bằng chứng lúc kiểm hàng (API 85)
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    @ColumnDefault("'MEMBER'")
    @Builder.Default
    private ReturnPhotoSource source = ReturnPhotoSource.MEMBER;
}
