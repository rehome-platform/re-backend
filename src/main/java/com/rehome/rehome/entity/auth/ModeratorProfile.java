package com.rehome.rehome.entity.auth;

import com.rehome.rehome.entity.Account;
import com.rehome.rehome.entity.marketplace.Warehouse;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "moderator_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModeratorProfile {
    // Dùng chung khóa với Account, quan hệ 1-1
    @Id
    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    // Họ tên Moderator, in trên biên nhận ký gửi — mục 12
    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    // Mã nhân viên, in trên biên nhận, ví dụ MOD-07-003 — mục 12
    @Column(name = "moderator_code", nullable = false, unique = true, length = 255)
    private String moderatorCode;

    // Kho mà Moderator thuộc về
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;
}
