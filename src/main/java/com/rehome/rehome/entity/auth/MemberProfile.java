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
    @GeneratedMemberCode
    @Column(name = "member_code", nullable = false, unique = true, length = 255)
    private String memberCode;

    // Điểm tích lũy từ đơn hàng đã chốt (cộng ở payoutOrder). Chỉ đổi được ra lượt AI, không quy ra tiền.
    // columnDefinition có default 0 vì ddl-auto: update thêm cột NOT NULL vào bảng đã có dòng sẽ lỗi nếu thiếu default.
    @Column(name = "points_balance", nullable = false, columnDefinition = "integer default 0")
    private int pointsBalance;

    // Lượt phối đồ AI (ảnh tĩnh) mua bằng điểm, chỉ bị trừ khi quota gói trong ngày/tháng đã hết
    @Column(name = "bonus_ai_tryon_credits", nullable = false, columnDefinition = "integer default 0")
    private int bonusAiTryonCredits;

    // Lượt thử đồ AI real-time mua bằng điểm, cách dùng như trên
    @Column(name = "bonus_live_tryon_credits", nullable = false, columnDefinition = "integer default 0")
    private int bonusLiveTryonCredits;
}
