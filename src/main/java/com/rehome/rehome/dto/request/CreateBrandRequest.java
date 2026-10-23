package com.rehome.rehome.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateBrandRequest {

    @NotBlank(message = "name là bắt buộc.")
    @Size(max = 255, message = "name tối đa 255 ký tự.")
    private String name;
}