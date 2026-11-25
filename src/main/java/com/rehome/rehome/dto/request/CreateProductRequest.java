package com.rehome.rehome.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class CreateProductRequest {

    @NotNull(message = "receiptId là bắt buộc.")
    private UUID receiptId;

    @NotBlank(message = "name là bắt buộc.")
    private String name;

    // Không truyền thì giữ danh mục/thương hiệu Member đã khai trên form ký gửi.
    private UUID categoryId;
    private UUID brandId;

    private String size;          // nullable theo entity
    private List<String> photoUrls;
}
