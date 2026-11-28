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
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Account member;

    @Column(name = "recipient_name", nullable = false, length = 255)
    private String recipientName;

    @Column(name = "phone", nullable = false, length = 255)
    private String phone;

    @Column(name = "address_detail", nullable = false, length = 255)
    private String addressDetail;

    @Column(name = "ward_name", nullable = true, length = 255)
    private String wardName;

    @Column(name = "ward_code", nullable = true, length = 64)
    private String wardCode;

    @Column(name = "province_name", nullable = true, length = 255)
    private String provinceName;

    @Column(name = "province_id", nullable = true)
    private Integer provinceId;

    @Column(name = "is_default", nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private Boolean isDefault = false;
}
