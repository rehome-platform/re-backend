package com.rehome.rehome.entity.auth;

import com.rehome.rehome.entity.Account;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "member_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberProfile {
    // Dùng chung khóa với Account, quan hệ 1-1
    @Id
    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    // Họ tên Member, Moderator đối chiếu khi giao nhận tại quầy
    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    // Ảnh đại diện, dùng xác nhận đúng người tại quầy
    @Column(name = "avatar_url", nullable = true, length = 2048)
    private String avatarUrl;

    // Mã Member để tra cứu tại quầy
    @Column(name = "member_code", nullable = false, unique = true, length = 255)
    private String memberCode;
}
