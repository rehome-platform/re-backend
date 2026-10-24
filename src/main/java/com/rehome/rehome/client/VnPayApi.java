package com.rehome.rehome.client;

import com.rehome.rehome.dto.request.QueryVnPayRequest;
import com.rehome.rehome.dto.response.QueryVnPayResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "vnpay", url = "${vnpay.query-end-point}")
public interface VnPayApi {

    @PostMapping("/merchant_webapi/api/transaction")
    QueryVnPayResponse queryTransaction(@RequestBody QueryVnPayRequest queryVnPayRequest);
}
