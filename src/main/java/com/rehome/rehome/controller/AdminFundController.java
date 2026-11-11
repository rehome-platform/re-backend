package com.rehome.rehome.controller;

import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.FundLedgerEntryResponse;
import com.rehome.rehome.dto.response.FundReconciliationResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.enums.WalletTransactionType;
import com.rehome.rehome.service.AdminFundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// /api/admin/** đã được SecurityConfig giới hạn cho ADMIN; @PreAuthorize là lớp bảo vệ thứ hai
@RestController
@RequestMapping("/api/admin/fund")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Fund")
public class AdminFundController {

    private final AdminFundService adminFundService;

    @GetMapping("/ledger")
    @Operation(summary = "Sổ quỹ nền tảng: các bút toán đối ứng (thanh toán, rút tiền, trả người bán...), lọc from/to yyyy-MM-dd và type, phân trang")
    public ResponseEntity<ApiResponse<PageResponse<FundLedgerEntryResponse>>> ledger(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) WalletTransactionType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(adminFundService.ledger(from, to, type, page, size)));
    }

    @GetMapping("/reconciliation")
    @Operation(summary = "Đối soát quỹ nền tảng với sổ bút toán: trạng thái, số chênh lệch, thời điểm kiểm tra")
    public ResponseEntity<ApiResponse<FundReconciliationResponse>> reconciliation() {
        return ResponseEntity.ok(ApiResponse.success(adminFundService.reconciliation()));
    }
}
