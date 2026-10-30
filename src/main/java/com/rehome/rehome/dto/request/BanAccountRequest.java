package com.rehome.rehome.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BanAccountRequest {

    @NotBlank(message = "reason không được để trống")
    @Size(max = 1000, message = "reason tối đa 1000 ký tự")
    private String reason;
}
