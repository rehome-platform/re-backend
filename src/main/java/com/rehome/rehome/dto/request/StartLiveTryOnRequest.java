package com.rehome.rehome.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// POST /tryon/live/sessions - món sẽ làm garment reference cho Decart Lucy VTON
@Getter
@Setter
@NoArgsConstructor
public class StartLiveTryOnRequest {

    @NotBlank(message = "productId không được để trống.")
    @Size(max = 255, message = "productId tối đa 255 ký tự.")
    private String productId;
}
