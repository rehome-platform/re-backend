package com.rehome.rehome.service;

import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;

public interface ReceiptPdfService {

    /**
     * Sinh PDF snapshot của phiếu ký gửi (F-022), lưu file và trả về URL.
     * Gọi sau khi đã set confirmedAt trên receipt, trong cùng transaction
     * (cần để đọc các quan hệ lazy: member, moderator, warehouse, consignmentForm).
     */
    String generate(ConsignmentReceipt receipt);
}