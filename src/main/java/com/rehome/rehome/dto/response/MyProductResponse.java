package com.rehome.rehome.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.rehome.rehome.entity.enums.EndOfTermChoice;
import com.rehome.rehome.entity.enums.OrderStatus;
import com.rehome.rehome.entity.enums.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MyProductResponse {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private DonationInfo donation;

    public record DonationInfo(UUID id, UUID campaignId, String campaignTitle,
                               String organizationName, String status, Instant handedOverAt,
                               String reassignedFromCampaignTitle) {}

    private String id;
    private String productName;
    private ProductStatus status;
    private String photoUrl;
    private String brandName;
    private String size;
    private BigDecimal agreedPrice;
    private BigDecimal floorPrice;
    private BigDecimal currentPrice;
    private UUID warehouseId;
    private String warehouseName;
    private Short currentTerm;
    private Instant termStartedAt;
    private Instant currentTermEndsAt;
    private Instant endOfTermDeadline;
    private Instant lastPriceChangedAt;
    private EndOfTermDecisionInfo endOfTermDecision;
    private OrderRef order;

    private WithdrawalRef withdrawalRequest;
    private DegradedCaseRef degradedCase;
    private BigDecimal expectedPayout;

    private boolean featured;
    private Instant featuredUntil;

    private Integer termLengthDays;
    private Integer maxRenewals;
    private BigDecimal commissionRateTerm1;
    private BigDecimal commissionRateTerm2;
    private BigDecimal commissionRateTerm3;

    private UUID receiptId;

    private Integer freeWithdrawalWindowDays;
    private BigDecimal withdrawalFee;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<PriceChange> priceChanges;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<TermInfo> termHistory;

    public record EndOfTermDecisionInfo(UUID id, Instant deadline, EndOfTermChoice decision, UUID newReceiptId, Instant decidedAt, Instant takenBackAt, Instant pickupDeadline) {}
    public record OrderRef(UUID id, String orderNo, OrderStatus status,
                           BigDecimal commissionRateApplied, Short orderedTerm, Instant orderedAt,
                           Instant payoutDueAt, Instant payoutAt) {}
    public record WithdrawalRef(UUID id, String status, Instant pickupDeadline) {}
    public record DegradedCaseRef(UUID id, String status, Instant decisionDeadline, String decision, Instant pickedUpAt) {}
    public record PriceChange(Instant changedAt, BigDecimal oldPrice, BigDecimal newPrice) {}
    public record TermInfo(int term, BigDecimal price, BigDecimal commissionRate) {}
}