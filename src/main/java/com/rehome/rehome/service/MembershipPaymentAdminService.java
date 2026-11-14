package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.AdminMembershipPaymentResponse;
import com.rehome.rehome.dto.response.PageResponse;

public interface MembershipPaymentAdminService {

    // from/to dạng yyyy-MM-dd (giờ VN, gồm cả ngày "to"), from <= to; tier/method là tên enum, bỏ trống = tất cả
    PageResponse<AdminMembershipPaymentResponse> search(
            String from, String to, String tier, String method, int page, int size);
}
