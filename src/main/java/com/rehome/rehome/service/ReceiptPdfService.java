package com.rehome.rehome.service;

import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;

public interface ReceiptPdfService {

    byte[] generate(ConsignmentReceipt receipt);
}