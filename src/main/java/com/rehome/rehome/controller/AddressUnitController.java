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

// Danh mục địa chỉ GHN (hệ cũ: tỉnh -> quận/huyện -> phường/xã), dùng khi Member thêm/sửa
// địa chỉ giao hàng và khi khai địa chỉ kho. Tên trả về chính là giá trị gửi lại cho
// wardName/districtName/provinceName để tạo vận đơn GHN không bị ROUTE_NOT_FOUND_SERVICE.
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

    @GetMapping("/districts")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<AddressUnitResponse>>> getDistricts(
            @RequestParam int provinceId) {
        return ResponseEntity.ok(ApiResponse.success(addressUnitService.getDistricts(provinceId)));
    }

    @GetMapping("/wards")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<AddressUnitResponse>>> getWards(
            @RequestParam int districtId) {
        return ResponseEntity.ok(ApiResponse.success(addressUnitService.getWards(districtId)));
    }
}
