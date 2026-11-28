package com.rehome.rehome.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ReConsignTermsRequest {

    @NotNull(message = "agreedPrice phải lớn hơn 0.")
    @Positive(message = "agreedPrice phải lớn hơn 0.")
    private BigDecimal agreedPrice;

    @Positive(message = "floorPrice phải lớn hơn 0.")
    private BigDecimal floorPrice;

    @Positive(message = "termLengthDays phải lớn hơn 0.")
    private Integer termLengthDays;

    @PositiveOrZero(message = "maxRenewals không được âm.")
    private Integer maxRenewals;

    @DecimalMin(value = "0", message = "commissionRateTerm1 phải từ 0 đến 1.")
    @DecimalMax(value = "1", message = "commissionRateTerm1 phải từ 0 đến 1.")
    private BigDecimal commissionRateTerm1;

    @DecimalMin(value = "0", message = "commissionRateTerm2 phải từ 0 đến 1.")
    @DecimalMax(value = "1", message = "commissionRateTerm2 phải từ 0 đến 1.")
    private BigDecimal commissionRateTerm2;

    @DecimalMin(value = "0", message = "commissionRateTerm3 phải từ 0 đến 1.")
    @DecimalMax(value = "1", message = "commissionRateTerm3 phải từ 0 đến 1.")
    private BigDecimal commissionRateTerm3;

    private String conditionNote;

    private String moderatorNote;
}