package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.MemberProfile;
import com.rehome.rehome.entity.enums.AccountStatus;
import com.rehome.rehome.entity.enums.ConsignmentFormStatus;
import com.rehome.rehome.entity.marketplace.ConsignmentForm;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** API 25 - tra cứu Member tại quầy. Email trả nguyên văn (không mask) vì chỉ Moderator gọi được. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberLookupItemResponse {
    private UUID accountId;
    private String fullName;
    private String avatarUrl;
    private String memberCode;

    private String email;
    private String phone;
    // Ngày tạo tài khoản
    private Instant joinedAt;
    // Lần gần nhất Moderator xác nhận danh tính Member tại quầy (CounterIdentityConfirmation); null nếu chưa từng
    private Instant lastWarehouseVisit;
    // Số lần bị xử lý vi phạm (đếm audit log BAN_ACCOUNT) + trạng thái khoá hiện tại
    private ViolationSummary violationSummary;
    // Form ký gửi còn hiệu lực (PENDING / APPROVED / INSPECTING) tại kho của Moderator đang tra
    private List<ActiveConsignmentItem> activeConsignments;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ViolationSummary {
        // Số lần admin khoá tài khoản (số dòng audit log BAN_ACCOUNT của member)
        private long totalViolations;
        private Boolean isCurrentlyBanned;
        // Chỉ có giá trị khi đang bị khoá
        private String banReason;
        // Lần khoá gần nhất; null nếu chưa từng bị khoá
        private Instant lastViolationAt;

        public static ViolationSummary empty(Account account) {
            return fromAccount(account, 0, null);
        }

        public static ViolationSummary fromAccount(Account account, long total, Instant lastViolationAt) {
            boolean banned = account != null && account.getStatus() == AccountStatus.BANNED;
            return ViolationSummary.builder()
                    .totalViolations(total)
                    .isCurrentlyBanned(banned)
                    .banReason(banned ? account.getBanReason() : null)
                    .lastViolationAt(lastViolationAt)
                    .build();
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ActiveConsignmentItem {
        private UUID id;
        private String formNo;
        private String productName;
        private ConsignmentFormStatus status;
        private BigDecimal desiredPrice;
        private LocalDate bringInDeadline;
        private Instant createdAt;

        public static ActiveConsignmentItem from(ConsignmentForm f) {
            return ActiveConsignmentItem.builder()
                    .id(f.getId())
                    .formNo(f.getFormNo())
                    .productName(f.getProductName())
                    .status(f.getStatus())
                    .desiredPrice(f.getDesiredPrice())
                    .bringInDeadline(f.getBringInDeadline())
                    .createdAt(f.getCreatedAt())
                    .build();
        }
    }

    /** Chỉ phần thông tin hồ sơ; các phần tổng hợp để null. */
    public static MemberLookupItemResponse from(MemberProfile p) {
        return from(p, null, null, List.of());
    }

    public static MemberLookupItemResponse from(MemberProfile p, Instant lastWarehouseVisit,
                                                ViolationSummary violationSummary,
                                                List<ActiveConsignmentItem> activeConsignments) {
        Account account = p.getAccount();
        return MemberLookupItemResponse.builder()
                .accountId(p.getAccountId())
                .fullName(p.getFullName())
                .avatarUrl(p.getAvatarUrl())
                .memberCode(p.getMemberCode())
                .email(account != null ? account.getEmail() : null)
                .phone(account != null ? account.getPhone() : null)
                .joinedAt(account != null ? account.getCreatedAt() : null)
                .lastWarehouseVisit(lastWarehouseVisit)
                .violationSummary(violationSummary)
                .activeConsignments(activeConsignments)
                .build();
    }
}
