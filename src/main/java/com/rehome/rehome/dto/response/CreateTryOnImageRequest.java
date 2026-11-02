package com.rehome.rehome.dto.request;

import com.rehome.rehome.entity.enums.TryOnModelType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// API 97 - POST /tryon/images
// Hợp lệ khi đúng 1 trong 2 bộ: {topProductId + bottomProductId} hoặc {dressProductId}.
// "Đúng 1 bộ" KHÔNG kiểm bằng annotation vì spec trả 409 INCOMPLETE_OUTFIT (không phải 400) -> kiểm ở service.
@Getter
@Setter
@NoArgsConstructor
public class CreateTryOnImageRequest {

    @NotNull(message = "modelType phải là MALE hoặc FEMALE.")
    private TryOnModelType modelType;

    @Size(max = 255, message = "topProductId tối đa 255 ký tự.")
    private String topProductId;

    @Size(max = 255, message = "bottomProductId tối đa 255 ký tự.")
    private String bottomProductId;

    @Size(max = 255, message = "dressProductId tối đa 255 ký tự.")
    private String dressProductId;
}