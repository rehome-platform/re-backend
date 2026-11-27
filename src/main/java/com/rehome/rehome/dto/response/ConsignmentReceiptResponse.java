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

    // Bản điều khoản dạng đoạn văn (Điều 4,5,6,7,9,10) đã chụp lúc tạo biên nhận — không đổi
    // theo template hiện tại của Admin
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
    // Biên nhận mới đã thay thế biên nhận này sau khi bị huỷ, null nếu chưa bị thay thế
    private UUID replacedByReceiptId;
    private String receiptNo;
    private Instant createdAt;
    private String productName;
    private String brandName;
    private String size;
    // null cho tới khi Moderator nhập kho (sau khi Member xác nhận)
    private String sku;
    private String conditionNote;
    private String warehouseName;
    private String moderatorNote;
    // Chỉ có với biên nhận ký gửi lại (sinh từ quyết định RE_CONSIGN cuối kỳ), còn lại null
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
    private Instant confirmedAt;  // null = chưa xác nhận
    // Không còn URL: tải PDF qua GET /consignment-receipts/{id}/pdf (cần JWT)
    private boolean pdfAvailable; // false cho tới khi xác nhận

    // ===== Thương lượng giá ký gửi lại: chỉ có với biên nhận sinh từ EndOfTermDecision (RE_CONSIGN),
    // còn lại (biên nhận ký gửi lần đầu) để null =====
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