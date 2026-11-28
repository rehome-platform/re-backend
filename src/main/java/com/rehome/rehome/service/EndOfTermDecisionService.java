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

    List<PendingReConsignResponse> listPendingPricing(Account moderator);

    ReConsignTermsResponse setReConsignTerms(Account moderator, UUID decisionId, ReConsignTermsRequest request);

    ReConsignCounterOfferResponse counterOffer(Account member, UUID decisionId, ReConsignCounterOfferRequest request);

    DecideEndOfTermResponse decline(Account member, UUID decisionId, ReConsignDeclineRequest request);

    ReConsignTermsResponse counterResponse(Account moderator, UUID decisionId, ReConsignCounterResponseRequest request);

    EndOfTermDecision createDecision(Product product, Instant now);
}