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

    @Size(max = 5, message = "imageUrls tối đa 5 ảnh.")
    private List<String> imageUrls;
}
