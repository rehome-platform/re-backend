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
    @NotBlank(message = "productId không được để trống")
    private String productId;

    private String notes;

    private List<String> photos;
}
