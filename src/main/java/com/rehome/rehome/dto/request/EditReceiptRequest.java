package com.rehome.rehome.dto.request;

import java.math.BigDecimal;
import jakarta.validation.constraints.*;

public record EditReceiptRequest(@NotNull @DecimalMin("0.01") BigDecimal agreedPrice,
        @NotBlank String conditionNote, String moderatorNote, @NotBlank String reason) {}
