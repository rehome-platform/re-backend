package com.rehome.rehome.dto.request;

import com.rehome.rehome.entity.enums.CatalogStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateBrandRequest {

    @NotBlank(message = "name là bắt buộc.")
    @Size(max = 255, message = "name tối đa 255 ký tự.")
    private String name;

    // null = giữ nguyên (dùng để ẩn/hiện thương hiệu)
    private CatalogStatus status;
}