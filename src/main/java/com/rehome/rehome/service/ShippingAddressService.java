package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.AddShippingAddressRequest;
import com.rehome.rehome.dto.response.ShippingAddressResponse;

import java.util.List;
import java.util.UUID;

public interface ShippingAddressService {
    List<ShippingAddressResponse> getAddresses(String email);

    ShippingAddressResponse createAddress(String email, AddShippingAddressRequest request);

    ShippingAddressResponse updateAddress(String email, UUID id, AddShippingAddressRequest request);

    void deleteAddress(String email, UUID id);
}
