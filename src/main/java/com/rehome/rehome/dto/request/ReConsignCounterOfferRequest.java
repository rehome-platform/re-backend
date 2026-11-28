package com.rehome.rehome.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ReConsignCounterOfferRequest {

    @NotNull(message = "price phải lớn hơn 0.")
    @Positive(message = "price phải lớn hơn 0.")
    private BigDecimal price;

    private String note;
}
