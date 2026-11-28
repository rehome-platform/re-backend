package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.AddressUnitResponse;
import com.rehome.rehome.dto.response.ResolvedAddressUnit;

import java.util.List;

public interface AddressUnitService {

    List<AddressUnitResponse> getProvinces();

    List<AddressUnitResponse> getWards(int provinceId);

    ResolvedAddressUnit resolveNewUnit(String provinceName, String wardName);
}
