package com.rehome.rehome.controller;

import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.WarehouseHistoryResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.service.WarehouseHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/moderator/warehouse-history")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MODERATOR')")
@Tag(name = "Moderator - Lịch sử kho")
public class ModeratorWarehouseHistoryController {

    private final WarehouseHistoryService warehouseHistoryService;

    @GetMapping
    @Operation(summary = "Lịch sử thao tác của kho Moderator đang thuộc về, mới nhất trước. "
            + "type = PACKING / INSPECTION / INVENTORY, size tối đa 50")
    public ApiResponse<PageResponse<WarehouseHistoryResponse>> list(
            @RequestParam String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Account moderator) {
        return ApiResponse.success(warehouseHistoryService.list(moderator, type, page, size));
    }
}
