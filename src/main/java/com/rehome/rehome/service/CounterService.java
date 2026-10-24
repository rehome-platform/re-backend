package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.CounterLookupRequest;
import com.rehome.rehome.dto.response.MemberLookupItemResponse;
import com.rehome.rehome.dto.response.MemberTasksResponse;
import java.util.List;
import java.util.UUID;

public interface CounterService {

    // API 25: tra Member bằng đúng 1 trong memberCode / phone / qrPayload
    List<MemberLookupItemResponse> lookup(CounterLookupRequest request);

    // API 27: việc chờ xử lý của Member đang đứng tại quầy
    MemberTasksResponse getTasks(UUID accountId);
}