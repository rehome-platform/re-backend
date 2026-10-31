package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.CreateReturnRequestRequest;
import com.rehome.rehome.dto.request.InvalidReturnChoiceRequest;
import com.rehome.rehome.dto.request.MarkLostRequest;
import com.rehome.rehome.dto.request.RejectReturnRequestRequest;
import com.rehome.rehome.dto.request.ResolveReturnRequestRequest;
import com.rehome.rehome.dto.request.StartInspectionRequest;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.ReturnRequestResponse;
import com.rehome.rehome.entity.auth.Account;

import java.util.UUID;

public interface ReturnRequestService {

    // F-067 - Member gửi yêu cầu trả hàng kèm lý do và ảnh
    ReturnRequestResponse create(String email, CreateReturnRequestRequest request);

    // Member xem yêu cầu trả hàng của mình
    PageResponse<ReturnRequestResponse> getMyReturnRequests(String email, int page, int size);

    ReturnRequestResponse getMyReturnRequest(String email, UUID id);

    // Hàng đợi xử lý của Moderator
    PageResponse<ReturnRequestResponse> getQueue(String status, int page, int size);

    ReturnRequestResponse getDetailForModerator(UUID id);

    // F-131 - Moderator duyệt và tạo vận đơn GHN chiều trả về kho
    ReturnRequestResponse approve(UUID id);

    // F-131 - Moderator từ chối yêu cầu
    ReturnRequestResponse reject(UUID id, RejectReturnRequestRequest request);

    // F-068 - Member xác nhận đã bàn giao hàng cho GHN
    ReturnRequestResponse confirmHandover(String email, UUID id);

    // F-132 - Moderator nhận hàng về kho, ghi nhận tình trạng hàng và bắt đầu kiểm
    ReturnRequestResponse startInspection(UUID id, StartInspectionRequest request);

    // API 88 (F-133) - Moderator chốt kết luận VALID/INVALID và hướng xử lý món hàng nếu VALID
    ReturnRequestResponse resolve(Account moderator, UUID id, ResolveReturnRequestRequest request);

    // F-070 - Member chọn cách nhận lại hàng trả không hợp lệ
    ReturnRequestResponse chooseInvalidReturn(String email, UUID id, InvalidReturnChoiceRequest request);

    // F-068/F-136 - webhook GHN cập nhật vận đơn chiều trả; trả về true nếu khớp một yêu cầu trả hàng
    boolean applyGhnReturnStatus(String ghnReturnTrackingCode, String ghnStatus, String reason);

    // API 89 (F-136) - Moderator tự tay đánh dấu hàng trả thất lạc khi webhook GHN không về
    ReturnRequestResponse markLost(UUID id, MarkLostRequest request);
}
