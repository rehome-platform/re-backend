package com.rehome.rehome.service;

import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;

public interface ReceiptPdfService {

    /**
     * Sinh PDF snapshot của phiếu ký gửi (F-022) và trả về byte.
     * Không gọi ra ngoài mạng nên an toàn khi dùng trong transaction; người gọi
     * tự quyết lưu đi đâu ({@link com.rehome.rehome.entity.marketplace.ConsignmentReceiptPdf}).
     *
     * <p>Gọi sau khi đã set confirmedAt trên receipt, trong cùng transaction
     * (cần để đọc các quan hệ lazy: member, moderator, warehouse, consignmentForm).
     */
    byte[] generate(ConsignmentReceipt receipt);
}