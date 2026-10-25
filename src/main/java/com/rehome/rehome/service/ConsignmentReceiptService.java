package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.CompleteInspectionRequest;
import com.rehome.rehome.dto.response.CompleteInspectionResponse;
import com.rehome.rehome.dto.response.ConsignmentReceiptConfirmResponse;
import com.rehome.rehome.dto.response.ConsignmentReceiptResponse;
import com.rehome.rehome.dto.response.PdfDownload;
import com.rehome.rehome.entity.auth.Account;

import java.util.UUID;

public interface ConsignmentReceiptService {
    ConsignmentReceiptResponse getMyReceipt(Account account, UUID id);
    ConsignmentReceiptConfirmResponse confirmReceipt(Account account, UUID id);
    CompleteInspectionResponse completeInspection(UUID formId, CompleteInspectionRequest request);

    /**
     * Tải PDF biên nhận đã xác nhận (F-023). Chỉ Member sở hữu biên nhận,
     * hoặc Moderator/Admin, mới lấy được - không có URL công khai nào thay thế được.
     */
    PdfDownload getReceiptPdf(Account account, UUID id);
}
