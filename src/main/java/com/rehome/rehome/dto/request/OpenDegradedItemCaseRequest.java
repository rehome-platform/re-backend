package com.rehome.rehome.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OpenDegradedItemCaseRequest {
    // SKU của sản phẩm bị phát hiện xuống cấp lúc kiểm kho
    @NotBlank(message = "productId không được để trống")
    private String productId;

    // Ghi chú của Moderator về tình trạng hàng
    private String notes;

    // Ảnh bằng chứng, URL đã tải lên qua /uploads
    private List<String> photos;
}
