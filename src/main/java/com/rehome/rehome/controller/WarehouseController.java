package com.rehome.rehome.controller;

import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.WarehouseNearestResponse;
import com.rehome.rehome.service.WarehouseService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API 14 - GET /warehouses/nearest (F-015, Role: Member).
 * Trả về danh sách kho ACTIVE sắp xếp theo khoảng cách tăng dần so với vị trí Member.
 */
@RestController
@RequestMapping("/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;

    @GetMapping("/nearest")
    @PreAuthorize("hasRole('MEMBER')")
    public ApiResponse<List<WarehouseNearestResponse>> getNearest(
            @RequestParam double lat,
            @RequestParam double lng) {
        return ApiResponse.success(warehouseService.getNearest(lat, lng));
    }
}