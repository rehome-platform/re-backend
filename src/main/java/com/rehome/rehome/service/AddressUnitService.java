package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.AddressUnitResponse;

import java.util.List;

// Proxy danh mục địa chỉ GHN (hệ 2 cấp mới: tỉnh/thành -> phường/xã) để FE chọn đúng tên
// mà không cần biết Token GHN
public interface AddressUnitService {

    List<AddressUnitResponse> getProvinces();

    List<AddressUnitResponse> getWards(int provinceId);
}
