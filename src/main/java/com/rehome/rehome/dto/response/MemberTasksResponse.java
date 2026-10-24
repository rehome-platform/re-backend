package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.ConsignmentFormStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberTasksResponse {
    private List<PendingFormItem> pendingForms;
    private List<PendingWithdrawalItem> pendingWithdrawals;
    private List<PendingEndOfTermItem> pendingEndOfTerm;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PendingFormItem {
        private UUID id;
        private String productName;
        private BigDecimal desiredPrice;
        private ConsignmentFormStatus status;
        private Instant createdAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PendingWithdrawalItem {
        private UUID id;
        private String productId;
        private String productName;
        private BigDecimal feeCharged;
        private Instant pickupDeadline;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PendingEndOfTermItem {
        private UUID id;
        private String productId;
        private String productName;
        private Instant deadline;
    }
}