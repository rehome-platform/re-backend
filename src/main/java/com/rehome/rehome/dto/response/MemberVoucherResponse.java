package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.MemberVoucherStatus;
import com.rehome.rehome.entity.enums.VoucherDiscountKind;
import com.rehome.rehome.entity.enums.VoucherType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberVoucherResponse {

    private String id;
    private String code;
    private String name;
    private String description;
    private VoucherType type;
    private VoucherDiscountKind discountKind;
    private BigDecimal discountValue;
    private BigDecimal maxDiscountAmount;
    private BigDecimal minOrderAmount;
    private MemberVoucherStatus status;
    private Instant issuedAt;
    private Instant expiresAt;
    private int daysRemaining;
    private Instant usedAt;
    @Builder.Default
    private List<String> usedOrderNos = List.of();
}
