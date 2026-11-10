package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.DecideEndOfTermRequest;
import com.rehome.rehome.dto.request.ReConsignTermsRequest;
import com.rehome.rehome.dto.response.DecideEndOfTermResponse;
import com.rehome.rehome.dto.response.PendingReConsignResponse;
import com.rehome.rehome.dto.response.ReConsignTermsResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.marketplace.EndOfTermDecision;
import com.rehome.rehome.entity.marketplace.Product;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EndOfTermDecisionService {
    DecideEndOfTermResponse decide(Account account, UUID decisionId, DecideEndOfTermRequest request);

    // Moderator: các yêu cầu ký gửi lại đang chờ chốt giá, chỉ của kho mình phụ trách
    List<PendingReConsignResponse> listPendingPricing(Account moderator);

    // Moderator chốt giá và điều khoản ký gửi lại: tạo biên nhận mới, chờ Member xác nhận
    ReConsignTermsResponse setReConsignTerms(Account moderator, UUID decisionId, ReConsignTermsRequest request);

    // Mở quyết định cuối kỳ cho sản phẩm: hạn chọn = now + end_of_term_decision_days (cấu hình Admin).
    // Đã có quyết định đang chờ thì trả lại chính nó, không tạo trùng.
    EndOfTermDecision createDecision(Product product, Instant now);
}