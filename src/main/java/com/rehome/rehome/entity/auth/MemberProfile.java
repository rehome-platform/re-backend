package com.rehome.rehome.entity.auth;

import com.rehome.rehome.generator.GeneratedMemberCode;
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
@Data
public class MemberProfile {
    @Id
    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(name = "avatar_url", nullable = true, length = 2048)
    private String avatarUrl;

    @GeneratedMemberCode
    @Column(name = "member_code", nullable = false, unique = true, length = 255)
    private String memberCode;

    @Column(name = "points_balance", nullable = false, columnDefinition = "integer default 0")
    private int pointsBalance;

    @Column(name = "bonus_ai_tryon_credits", nullable = false, columnDefinition = "integer default 0")
    private int bonusAiTryonCredits;

    @Column(name = "bonus_live_tryon_credits", nullable = false, columnDefinition = "integer default 0")
    private int bonusLiveTryonCredits;
}
