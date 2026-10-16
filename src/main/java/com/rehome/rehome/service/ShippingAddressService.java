package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.ShippingAddressResponse;

import java.util.List;

public interface ShippingAddressService {
    List<ShippingAddressResponse> getAddresses(String email);
}
