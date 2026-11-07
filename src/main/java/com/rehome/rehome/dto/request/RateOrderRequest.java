package com.rehome.rehome.dto.request;

import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RateOrderRequest {
    private Short serviceRating;

    @Size(max = 500, message = "comment tối đa 500 ký tự.")
    private String comment;

    // Ảnh bằng chứng kèm đánh giá, phải là ảnh đã upload qua hệ thống (Cloudinary của hệ
    // thống) — kiểm tra domain ở OrderServiceImpl.rateOrder, giống validateImageUrls của chat.
    @Size(max = 5, message = "imageUrls tối đa 5 ảnh.")
    private List<String> imageUrls;
}
