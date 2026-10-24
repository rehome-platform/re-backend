package com.rehome.rehome.controller;

import com.rehome.rehome.dto.response.AddressUnitResponse;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.service.AddressUnitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Danh mục địa chỉ GHN (hệ 2 cấp mới, sau sáp nhập 01/07/2025: tỉnh/thành -> phường/xã thẳng,
// không còn quận/huyện), dùng khi Member thêm/sửa địa chỉ giao hàng và khi khai địa chỉ kho.
// Tên trả về chính là giá trị gửi lại cho wardName/provinceName để tạo vận đơn GHN với
// is_new_to_address/is_new_from_address: true mà không bị "ward not found".
@RestController
@RequestMapping("/address-units")
@RequiredArgsConstructor
public class AddressUnitController {

    private final AddressUnitService addressUnitService;

    @GetMapping("/provinces")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<AddressUnitResponse>>> getProvinces() {
        return ResponseEntity.ok(ApiResponse.success(addressUnitService.getProvinces()));
    }

    @GetMapping("/wards")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<AddressUnitResponse>>> getWards(
            @RequestParam int provinceId) {
        return ResponseEntity.ok(ApiResponse.success(addressUnitService.getWards(provinceId)));
    }
}
