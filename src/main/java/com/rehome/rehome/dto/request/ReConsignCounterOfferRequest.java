package com.rehome.rehome.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

// Member đề xuất một giá khác với giá Moderator vừa chốt (POST /end-of-term-decisions/{id}/counter-offer)
@Getter
@Setter
public class ReConsignCounterOfferRequest {

    @NotNull(message = "price phải lớn hơn 0.")
    @Positive(message = "price phải lớn hơn 0.")
    private BigDecimal price;

    private String note;
}
