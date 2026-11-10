package com.rehome.rehome.service;

public interface EndOfTermLifecycleService {

    // F-029: sang kỳ (currentTerm++), hết 90 ngày (mở EndOfTermDecision, gỡ khỏi sàn), và
    // TAKE_BACK quá hạn đến kho nhận hàng (đánh dấu vô thừa nhận) - Điều 8 hợp đồng
    void processTermsAndDeadlines();
}
