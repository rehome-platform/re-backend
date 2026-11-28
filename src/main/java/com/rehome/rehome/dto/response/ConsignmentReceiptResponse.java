package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.ReConsignCounterResponseAction;
import com.rehome.rehome.entity.enums.ReConsignNegotiationAction;
import com.rehome.rehome.entity.enums.ReConsignStatus;
import com.rehome.rehome.entity.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConsignmentReceiptResponse {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ClauseItem {
        private Integer displayOrder;
        private String title;
        private String body;
    }

    private List<ClauseItem> clauses;
    private UUID id;
    private com.rehome.rehome.entity.enums.ReceiptStatus status;
    private Integer version;
    private String editReason;
    private Instant editedAt;
    private String voidReason;
    private Instant voidedAt;
    private UUID voidedBy;
    private UUID previousReceiptId;
    private UUID replacedByReceiptId;
    private String receiptNo;
    private Instant createdAt;
    private String productName;
    private String brandName;
    private String size;
    private String sku;
    private String conditionNote;
    private String warehouseName;
    private String moderatorNote;
    private String productId;
    private BigDecimal previousPrice;
    private BigDecimal agreedPrice;
    private BigDecimal floorPrice;
    private Integer termLengthDays;
    private Integer maxRenewals;
    private Integer freeWithdrawalWindowDays;
    private BigDecimal commissionRateTerm1;
    private BigDecimal commissionRateTerm2;
    private BigDecimal commissionRateTerm3;
    private BigDecimal withdrawalFee;
    private BigDecimal degradedCompensationRate;
    private Instant confirmedAt;
    private boolean pdfAvailable;

    private ReConsignStatus reConsignStatus;
    private Instant proposalDeadline;
    private Integer proposalRound;
    private Integer counterRoundsUsed;
    private Integer maxCounterRounds;
    private BigDecimal memberCounterPrice;
    private String memberCounterNote;
    private ReConsignCounterResponseAction moderatorResponse;
    private String moderatorResponseNote;
    private List<NegotiationHistoryItem> negotiationHistory;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class NegotiationHistoryItem {
        private Role actor;
        private ReConsignNegotiationAction action;
        private BigDecimal price;
        private String note;
        private Instant createdAt;
    }
}