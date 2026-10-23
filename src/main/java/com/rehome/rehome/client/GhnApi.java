package com.rehome.rehome.client;

import com.rehome.rehome.dto.request.GhnCreateOrderRequest;
import com.rehome.rehome.dto.response.GhnCreateOrderResponse;
import com.rehome.rehome.dto.response.GhnDistrict;
import com.rehome.rehome.dto.response.GhnMasterDataResponse;
import com.rehome.rehome.dto.response.GhnProvince;
import com.rehome.rehome.dto.response.GhnWard;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "ghn", url = "${ghn.base-url}")
public interface GhnApi {

    @PostMapping("/v2/shipping-order/create")
    GhnCreateOrderResponse createOrder(
            @RequestHeader("Token") String token,
            @RequestHeader("ShopId") String shopId,
            @RequestBody GhnCreateOrderRequest request);

    @GetMapping("/master-data/province")
    GhnMasterDataResponse<GhnProvince> getProvinces(
            @RequestHeader("Token") String token,
            @RequestHeader("ShopId") String shopId);

    @GetMapping("/master-data/district")
    GhnMasterDataResponse<GhnDistrict> getDistricts(
            @RequestHeader("Token") String token,
            @RequestHeader("ShopId") String shopId,
            @RequestParam("province_id") int provinceId);

    @GetMapping("/master-data/ward")
    GhnMasterDataResponse<GhnWard> getWards(
            @RequestHeader("Token") String token,
            @RequestHeader("ShopId") String shopId,
            @RequestParam("district_id") int districtId);
}
