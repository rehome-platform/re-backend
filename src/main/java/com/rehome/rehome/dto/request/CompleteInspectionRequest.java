package com.rehome.rehome.dto.request;

import com.rehome.rehome.entity.enums.InspectionResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CompleteInspectionRequest {

    @NotNull(message = "result là bắt buộc.")
    private InspectionResult result;

    @NotBlank(message = "conditionNote là bắt buộc.")
    private String conditionNote;

    @NotNull(message = "agreedPrice phải lớn hơn 0.")
    @Positive(message = "agreedPrice phải lớn hơn 0.")
    private BigDecimal agreedPrice;

    @Positive(message = "termLengthDays phải lớn hơn 0.")
    private Integer termLengthDays;   // không truyền thì lấy mặc định hệ thống
}
