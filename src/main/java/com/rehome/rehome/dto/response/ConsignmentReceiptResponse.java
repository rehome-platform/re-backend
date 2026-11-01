package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConsignmentReceiptResponse {
    private UUID id;
    private String receiptNo;
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
}