package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.AddressUnitResponse;

import java.util.List;

// Proxy danh mục địa chỉ GHN để FE chọn đúng tên phường/quận/tỉnh mà không cần biết Token GHN
public interface AddressUnitService {

    List<AddressUnitResponse> getProvinces();

    List<AddressUnitResponse> getDistricts(int provinceId);

    List<AddressUnitResponse> getWards(int districtId);
}
