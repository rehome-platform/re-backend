package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShippingAddressResponse {
    private String id;
    private String recipientName;
    private String phone;
    private String addressDetail;
    private String wardName;
    private String districtName;
    private String provinceName;
    private Boolean isNewAddress;
    private Boolean isDefault;
}
