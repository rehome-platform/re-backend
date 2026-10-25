package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConsignmentReceiptConfirmResponse {
    private UUID id;
    private Instant confirmedAt;
    // Không còn URL: tải PDF qua GET /consignment-receipts/{id}/pdf (cần JWT)
    private boolean pdfAvailable;
}
