package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.AddShippingAddressRequest;
import com.rehome.rehome.dto.response.CreateShippingAddressResponse;
import com.rehome.rehome.dto.response.ShippingAddressResponse;

import java.util.List;

public interface ShippingAddressService {
    List<ShippingAddressResponse> getAddresses(String email);

    CreateShippingAddressResponse createAddress(String email, AddShippingAddressRequest request);
}
