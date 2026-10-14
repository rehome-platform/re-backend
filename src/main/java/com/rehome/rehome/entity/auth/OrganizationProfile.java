package com.rehome.rehome.entity.auth;

import com.rehome.rehome.entity.Account;
import com.rehome.rehome.entity.enums.VerificationStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "organization_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationProfile {
    // Dùng chung khóa với Account, quan hệ 1-1
    @Id
    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    // Tên đầy đủ theo giấy tờ, để đối chiếu với ảnh giấy tờ
    @Column(name = "legal_name", nullable = false, length = 255)
    private String legalName;

    // Tên người dùng nhìn thấy trên nền tảng
    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    // Loại hình tổ chức, quyết định checklist giấy tờ lúc xác minh
    // The design does not yet define organization types; retain the supplied code as text.
    @Column(name = "org_type", nullable = false, length = 255)
    private String orgType;

    // Mã số thuế nếu có; CLB trường học thường không có
    @Column(name = "tax_code", nullable = true, length = 255)
    private String taxCode;

    // Phạm vi hoạt động: toàn quốc, tỉnh, huyện hay xã
    @Column(name = "operation_scope", nullable = false, length = 255)
    private String operationScope;

    // Địa chỉ trụ sở, đối chiếu với địa chỉ trên giấy tờ
    @Column(name = "address", nullable = false, length = 255)
    private String address;

    // Số liên hệ, Moderator gọi kiểm chứng
    @Column(name = "contact_phone", nullable = false, length = 255)
    private String contactPhone;

    // Email liên hệ, Moderator gửi mail kiểm chứng
    @Column(name = "contact_email", nullable = false, length = 255)
    private String contactEmail;

    // Website hoặc fanpage, dùng tra dấu hiệu bất thường
    @Column(name = "website", nullable = true, length = 2048)
    private String website;

    // Mô tả hoạt động, hiển thị trên hồ sơ công khai
    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    // Logo tổ chức
    @Column(name = "logo_url", nullable = true, length = 2048)
    private String logoUrl;

    // PENDING / VERIFIED / REJECTED — quyết định tổ chức được vào màn hình nào
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false)
    @ColumnDefault("'PENDING'")
    @Builder.Default
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    // Lý do từ chối, để tổ chức sửa rồi gửi lại
    @Column(name = "rejection_reason", nullable = true, columnDefinition = "text")
    private String rejectionReason;

    // Moderator đã kết luận hồ sơ
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "verified_by", nullable = true)
    private Account verifiedBy;

    // Thời điểm kết luận, cũng là mốc tính hạn tự xóa ảnh CCCD
    @Column(name = "verified_at", nullable = true)
    private Instant verifiedAt;

    // Tên điểm nhận đồ (gộp từ OrganizationDonationPoint vì mỗi tổ chức chỉ có 1 điểm nhận)
    @Column(name = "donation_point_name", nullable = false, length = 255)
    private String donationPointName;

    // Địa chỉ đầy đủ của điểm nhận đồ
    @Column(name = "donation_point_address", nullable = false, length = 255)
    private String donationPointAddress;

    // Vĩ độ điểm nhận đồ, để ghim lên bản đồ
    @Column(name = "donation_point_latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal donationPointLatitude;

    // Kinh độ điểm nhận đồ, để ghim lên bản đồ
    @Column(name = "donation_point_longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal donationPointLongitude;

    // Khung giờ nhận đồ trong tuần
    @Column(name = "donation_point_receiving_hours", nullable = false, length = 255)
    private String donationPointReceivingHours;

    // Số điện thoại tại điểm nhận, có thể khác contact_phone chung của tổ chức
    @Column(name = "donation_point_contact_phone", nullable = true, length = 255)
    private String donationPointContactPhone;
}
