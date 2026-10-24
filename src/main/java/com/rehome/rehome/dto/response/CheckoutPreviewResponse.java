package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutPreviewResponse {
    private List<CheckoutPreviewOrderResponse> orders;
    private List<CheckoutPreviewUnavailableItemResponse> unavailableItems;
}
