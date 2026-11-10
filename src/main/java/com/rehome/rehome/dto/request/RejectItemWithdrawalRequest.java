package com.rehome.rehome.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RejectItemWithdrawalRequest {

    @NotBlank(message = "reason là bắt buộc.")
    private String reason;
}
