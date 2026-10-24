package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.AddressUnitResponse;
import com.rehome.rehome.dto.response.ResolvedAddressUnit;

import java.util.List;

// Proxy danh mục địa chỉ GHN (hệ 2 cấp mới: tỉnh/thành -> phường/xã) để FE chọn đúng tên
// mà không cần biết Token GHN
public interface AddressUnitService {

    List<AddressUnitResponse> getProvinces();

    List<AddressUnitResponse> getWards(int provinceId);

    // Đối chiếu tên phường/xã + tỉnh/thành với danh mục GHN và trả về tên chuẩn.
    // Ném BadRequestException nếu không khớp, hoặc nếu phường/xã không thuộc tỉnh/thành đã chọn.
    ResolvedAddressUnit resolveNewUnit(String provinceName, String wardName);
}
