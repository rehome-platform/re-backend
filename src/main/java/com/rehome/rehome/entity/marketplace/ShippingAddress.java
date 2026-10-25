package com.rehome.rehome.entity.marketplace;

import com.rehome.rehome.entity.auth.Account;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "shipping_addresss")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShippingAddress {
    // Khóa chính
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    // Chủ địa chỉ
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Account member;

    // Tên người nhận hàng
    @Column(name = "recipient_name", nullable = false, length = 255)
    private String recipientName;

    // Số điện thoại người nhận
    @Column(name = "phone", nullable = false, length = 255)
    private String phone;

    // Địa chỉ chi tiết để GHN giao
    @Column(name = "address_detail", nullable = false, length = 255)
    private String addressDetail;

    // Phường/xã người nhận theo hành chính 2 cấp (sau 01/07/2025) - bắt buộc với GHN (to_ward_name)
    @Column(name = "ward_name", nullable = true, length = 255)
    private String wardName;

    // Mã phường/xã trong danh mục GHN hệ 2 cấp, lấy từ lúc đối chiếu tên với GHN.
    // Tạo vận đơn vẫn gửi theo tên, mã này để FE hiển thị lại đúng mục đã chọn và để
    // dò lại được địa chỉ khi GHN đổi tên đơn vị hành chính.
    @Column(name = "ward_code", nullable = true, length = 64)
    private String wardCode;

    // Tỉnh/thành người nhận - bắt buộc với GHN (to_province_name)
    @Column(name = "province_name", nullable = true, length = 255)
    private String provinceName;

    // ProvinceID trong danh mục GHN, cần để gọi GET /address-units/wards?provinceId= khi sửa địa chỉ
    @Column(name = "province_id", nullable = true)
    private Integer provinceId;

    // Địa chỉ được chọn sẵn khi đặt hàng
    @Column(name = "is_default", nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private Boolean isDefault = false;
}
