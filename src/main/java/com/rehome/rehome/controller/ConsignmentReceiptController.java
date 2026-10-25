package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.CompleteInspectionRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.CompleteInspectionResponse;
import com.rehome.rehome.dto.response.ConsignmentReceiptConfirmResponse;
import com.rehome.rehome.dto.response.ConsignmentReceiptResponse;
import com.rehome.rehome.dto.response.PdfDownload;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.repository.ConsignmentFormRepository;
import com.rehome.rehome.service.ConsignmentFormService;
import com.rehome.rehome.service.ConsignmentReceiptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/consignment-receipts")
@RequiredArgsConstructor
public class ConsignmentReceiptController {
    private final ConsignmentReceiptService consignmentReceiptService;
    @GetMapping("/{id}")
    public ApiResponse<ConsignmentReceiptResponse> getReceipt(
            @AuthenticationPrincipal Account account,
            @PathVariable UUID id) {
        return ApiResponse.success(consignmentReceiptService.getMyReceipt(account, id));
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<ConsignmentReceiptConfirmResponse> confirm(
            @AuthenticationPrincipal Account account,
            @PathVariable UUID id) {
        return ApiResponse.success(
                consignmentReceiptService.confirmReceipt(account, id),
                "Xác nhận ký gửi thành công, sản phẩm đã lên sàn");
    }

    /**
     * F-023: tải PDF biên nhận. Trả binary chứ không trả URL - biên nhận là hợp đồng,
     * quyền xem phải do BE quyết mỗi lần tải, không phải do ai đang giữ cái link.
     */
    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAnyRole('MEMBER','MODERATOR','ADMIN')")
    public ResponseEntity<byte[]> getReceiptPdf(
            @AuthenticationPrincipal Account account,
            @PathVariable UUID id) {

        PdfDownload pdf = consignmentReceiptService.getReceiptPdf(account, id);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(pdf.fileName()).build().toString())
                .contentLength(pdf.content().length)
                .body(pdf.content());
    }

    @PostMapping("/{id}/inspection/complete")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<ApiResponse<CompleteInspectionResponse>> completeInspection(
            @PathVariable UUID id,
            @Valid @RequestBody CompleteInspectionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                consignmentReceiptService.completeInspection(id, request), 200, "Đã tạo phiếu ký gửi"));
    }


}
