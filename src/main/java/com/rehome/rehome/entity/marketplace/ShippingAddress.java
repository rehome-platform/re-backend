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

    // Phường/xã người nhận - bắt buộc với GHN (to_ward_name)
    @Column(name = "ward_name", nullable = true, length = 255)
    private String wardName;

    // Quận/huyện người nhận - bắt buộc với GHN khi isNewAddress = false (to_district_name)
    @Column(name = "district_name", nullable = true, length = 255)
    private String districtName;

    // Tỉnh/thành người nhận - bắt buộc với GHN (to_province_name)
    @Column(name = "province_name", nullable = true, length = 255)
    private String provinceName;

    // true = theo đơn vị hành chính mới (sau 01/07/2025, không còn quận/huyện) khi gửi cho GHN
    @Column(name = "is_new_address", nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private Boolean isNewAddress = false;

    // Địa chỉ được chọn sẵn khi đặt hàng
    @Column(name = "is_default", nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private Boolean isDefault = false;
}
