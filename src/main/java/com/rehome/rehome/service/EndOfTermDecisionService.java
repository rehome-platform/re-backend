package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.DecideEndOfTermRequest;
import com.rehome.rehome.dto.request.ReConsignCounterOfferRequest;
import com.rehome.rehome.dto.request.ReConsignCounterResponseRequest;
import com.rehome.rehome.dto.request.ReConsignDeclineRequest;
import com.rehome.rehome.dto.request.ReConsignTermsRequest;
import com.rehome.rehome.dto.response.DecideEndOfTermResponse;
import com.rehome.rehome.dto.response.PendingReConsignResponse;
import com.rehome.rehome.dto.response.ReConsignCounterOfferResponse;
import com.rehome.rehome.dto.response.ReConsignTermsResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.marketplace.EndOfTermDecision;
import com.rehome.rehome.entity.marketplace.Product;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EndOfTermDecisionService {
    DecideEndOfTermResponse decide(Account account, UUID decisionId, DecideEndOfTermRequest request);

    // Moderator: các yêu cầu ký gửi lại đang chờ chốt giá (AWAITING_PRICE) hoặc chờ phản hồi đề xuất
    // lại giá của Member (COUNTER_OFFERED), chỉ của kho mình phụ trách
    List<PendingReConsignResponse> listPendingPricing(Account moderator);

    // Moderator chốt giá và điều khoản ký gửi lại lần đầu: tạo biên nhận mới (PROPOSED), chờ Member
    // xác nhận/đề xuất lại/từ chối trong re_consign_confirm_days ngày
    ReConsignTermsResponse setReConsignTerms(Account moderator, UUID decisionId, ReConsignTermsRequest request);

    // Member đề xuất một giá khác với giá Moderator vừa chốt (PROPOSED -> COUNTER_OFFERED)
    ReConsignCounterOfferResponse counterOffer(Account member, UUID decisionId, ReConsignCounterOfferRequest request);

    // Member không đồng ý đề xuất hiện tại -> huỷ biên nhận nháp, chuyển TAKE_BACK/WITHDRAWN
    DecideEndOfTermResponse decline(Account member, UUID decisionId, ReConsignDeclineRequest request);

    // Moderator phản hồi đề xuất giá của Member (ACCEPT/REPROPOSE/KEEP) -> mở lại vòng PROPOSED mới
    ReConsignTermsResponse counterResponse(Account moderator, UUID decisionId, ReConsignCounterResponseRequest request);

    // Mở quyết định cuối kỳ cho sản phẩm: hạn chọn = now + end_of_term_decision_days (cấu hình Admin).
    // Đã có quyết định đang chờ thì trả lại chính nó, không tạo trùng.
    EndOfTermDecision createDecision(Product product, Instant now);
}