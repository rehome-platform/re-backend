package com.rehome.rehome.dto.request;

import com.rehome.rehome.entity.enums.ReConsignCounterResponseAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ReConsignCounterResponseRequest {

    @NotNull(message = "action là bắt buộc.")
    private ReConsignCounterResponseAction action;

    @Positive(message = "agreedPrice phải lớn hơn 0.")
    private BigDecimal agreedPrice;

    @Positive(message = "floorPrice phải lớn hơn 0.")
    private BigDecimal floorPrice;

    private String note;
}
