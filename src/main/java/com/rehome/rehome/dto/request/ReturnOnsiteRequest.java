package com.rehome.rehome.dto.request;

import com.rehome.rehome.entity.enums.ReturnOnsiteResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReturnOnsiteRequest {

    @NotNull(message = "result là bắt buộc.")
    private ReturnOnsiteResult result;

    @NotBlank(message = "reason là bắt buộc.")
    private String reason;
}
