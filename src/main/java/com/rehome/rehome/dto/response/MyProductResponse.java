package com.rehome.rehome.dto.response;

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
    private Instant currentTermEndsAt;       // hết kỳ hiện tại (xem câu hỏi bên dưới)
    private Instant endOfTermDeadline;       // hết kỳ cuối (ngày 90)
    private Instant lastPriceChangedAt;
    private EndOfTermDecisionInfo endOfTermDecision;   // null khi chưa tới cuối kỳ
    private OrderRef order;                            // null khi chưa bán
    private BigDecimal expectedPayout;

    // Điều khoản kỳ ký gửi, lấy từ biên nhận của món (mục 22)
    private Integer termLengthDays;
    private Integer maxRenewals;
    private BigDecimal commissionRateTerm1;
    private BigDecimal commissionRateTerm2;
    private BigDecimal commissionRateTerm3;

    // Biên nhận đang áp dụng: biên nhận ký gửi lại đã xác nhận nếu có, không thì biên nhận gốc (mục 24)
    private UUID receiptId;

    // Chỉ có ở /mine/{id}, danh sách thì null và không xuất hiện trong JSON
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<PriceChange> priceChanges;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<TermInfo> termHistory;

    // newReceiptId: null = chờ Moderator chốt giá (khi decision = RE_CONSIGN) hoặc chưa chọn;
    // có giá trị = biên nhận ký gửi lại đã sẵn sàng, chờ Member xác nhận
    public record EndOfTermDecisionInfo(UUID id, Instant deadline, EndOfTermChoice decision, UUID newReceiptId) {}
    // payoutDueAt = mốc dự kiến tiền vào ví (deliveredAt + hạn trả hàng), payoutAt = đã trả lúc
    // nào, null nếu chưa trả. App hiện "Tiền vào ví sau dd/MM HH:mm" dựa trên 2 mốc này.
    // status/commissionRateApplied/orderedTerm/orderedAt: tiến trình đơn và hoa hồng đã chốt lúc đặt (mục 23)
    public record OrderRef(UUID id, String orderNo, OrderStatus status,
                           BigDecimal commissionRateApplied, Short orderedTerm, Instant orderedAt,
                           Instant payoutDueAt, Instant payoutAt) {}
    public record PriceChange(Instant changedAt, BigDecimal oldPrice, BigDecimal newPrice) {}
    public record TermInfo(int term, BigDecimal price, BigDecimal commissionRate) {}
}