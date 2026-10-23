package com.rehome.rehome.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateShelfLocationRequest {

    @NotBlank(message = "shelfLocation là bắt buộc.")
    @Size(max = 255, message = "shelfLocation tối đa 255 ký tự.")
    private String shelfLocation;
}