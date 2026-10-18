package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.AddShippingAddressRequest;
import com.rehome.rehome.dto.response.CreateShippingAddressResponse;
import com.rehome.rehome.dto.response.ShippingAddressResponse;
import com.rehome.rehome.dto.response.UpdateShippingAddressResponse;

import java.util.List;
import java.util.UUID;

public interface ShippingAddressService {
    List<ShippingAddressResponse> getAddresses(String email);

    CreateShippingAddressResponse createAddress(String email, AddShippingAddressRequest request);

    UpdateShippingAddressResponse updateAddress(String email, UUID id, AddShippingAddressRequest request);

    void deleteAddress(String email, UUID id);
}
