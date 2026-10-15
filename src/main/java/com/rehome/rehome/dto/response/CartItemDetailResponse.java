package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItemDetailResponse {
    private String id;
    private CartProductResponse product;
    private Instant addedAt;
    private String unavailableReason;
}
