package com.rehome.rehome.dto.request;

import com.rehome.rehome.entity.enums.TryOnSlot;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateCategoryRequest {

    @NotBlank(message = "name là bắt buộc.")
    @Size(max = 255, message = "name tối đa 255 ký tự.")
    private String name;

    @NotNull(message = "tryonSlot phải là TOP, BOTTOM hoặc DRESS.")
    private TryOnSlot tryonSlot;
}