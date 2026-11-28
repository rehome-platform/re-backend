package com.rehome.rehome.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class StartLiveTryOnRequest {

    @NotBlank(message = "productId không được để trống.")
    @Size(max = 255, message = "productId tối đa 255 ký tự.")
    private String productId;
}
