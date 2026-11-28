package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.CreateReturnRequestRequest;
import com.rehome.rehome.dto.request.InvalidReturnChoiceRequest;
import com.rehome.rehome.dto.request.MarkLostRequest;
import com.rehome.rehome.dto.request.RejectReturnRequestRequest;
import com.rehome.rehome.dto.request.ResolveReturnRequestRequest;
import com.rehome.rehome.dto.request.StartInspectionRequest;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.ReturnRequestResponse;
import com.rehome.rehome.dto.response.ShipBackQuoteResponse;
import com.rehome.rehome.entity.auth.Account;

import java.util.UUID;

public interface ReturnRequestService {

    ReturnRequestResponse create(String email, CreateReturnRequestRequest request);

    PageResponse<ReturnRequestResponse> getMyReturnRequests(String email, int page, int size);

    ReturnRequestResponse getMyReturnRequest(String email, UUID id);

    PageResponse<ReturnRequestResponse> getQueue(Account moderator, String status, int page, int size);

    ReturnRequestResponse getDetailForModerator(UUID id);

    ReturnRequestResponse approve(UUID id);
    ReturnRequestResponse approve(Account moderator, UUID id);
    ReturnRequestResponse applyItemAction(Account moderator, UUID id,
            com.rehome.rehome.dto.request.ApplyReturnItemActionRequest request);

    ReturnRequestResponse reject(UUID id, RejectReturnRequestRequest request);

    ReturnRequestResponse confirmHandover(String email, UUID id);

    ReturnRequestResponse startInspection(UUID id, StartInspectionRequest request);

    ReturnRequestResponse resolve(Account moderator, UUID id, ResolveReturnRequestRequest request);

    ShipBackQuoteResponse getShipBackQuote(String email, UUID id, UUID addressId);

    ReturnRequestResponse chooseInvalidReturn(String email, UUID id, InvalidReturnChoiceRequest request);

    boolean applyGhnReturnStatus(String ghnReturnTrackingCode, String ghnStatus, String reason);

    ReturnRequestResponse markLost(UUID id, MarkLostRequest request);
}
