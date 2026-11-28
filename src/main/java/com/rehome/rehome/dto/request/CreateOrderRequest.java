package com.rehome.rehome.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {
    private String shippingAddressId;

    private List<String> productIds;

    private String buyerNote;

    private String voucherCode;

    public CreateOrderRequest(String shippingAddressId, List<String> productIds, String buyerNote) {
        this(shippingAddressId, productIds, buyerNote, null);
    }
}
