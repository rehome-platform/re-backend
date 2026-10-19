package com.rehome.rehome.client;

import com.rehome.rehome.dto.request.CreateMomoRequest;
import com.rehome.rehome.dto.request.QueryMomoRequest;
import com.rehome.rehome.dto.response.CreateMomoResponse;
import com.rehome.rehome.dto.response.QueryMomoResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "momo", url = "${momo.end-point}")
public interface MomoApi {

    @PostMapping("/create")
    CreateMomoResponse createMomoQR(@RequestBody CreateMomoRequest createMomoRequest);

    @PostMapping("/query")
    QueryMomoResponse queryTransaction(@RequestBody QueryMomoRequest queryMomoRequest);
}
