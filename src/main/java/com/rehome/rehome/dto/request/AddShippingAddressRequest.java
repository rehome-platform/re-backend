package com.rehome.rehome.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddShippingAddressRequest {
    private String recipientName;
    private String phone;
    private String addressDetail;
    private Boolean isDefault;
}
