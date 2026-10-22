package com.rehome.rehome.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PriceReductionRequest {
    @NotNull(message = "newPrice không được để trống")
    @Positive(message = "newPrice phải lớn hơn 0")
    private BigDecimal newPrice;
}

