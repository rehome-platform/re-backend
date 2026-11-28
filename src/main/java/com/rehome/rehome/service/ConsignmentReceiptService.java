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
    ConsignmentReceiptResponse editReceipt(Account account, UUID id,
            com.rehome.rehome.dto.request.EditReceiptRequest request);
    ConsignmentReceiptResponse voidReceipt(Account account, UUID id, String reason);
    ConsignmentReceiptConfirmResponse confirmReceipt(Account account, UUID id, Integer version);

    ConsignmentReceiptConfirmResponse confirmReceipt(Account account, UUID id);
    CompleteInspectionResponse completeInspection(UUID formId, CompleteInspectionRequest request);

    PdfDownload getReceiptPdf(Account account, UUID id);
}
