package com.rehome.rehome.client;

import com.rehome.rehome.dto.request.GhnCalculateFeeRequest;
import com.rehome.rehome.dto.request.GhnCreateOrderRequest;
import com.rehome.rehome.dto.response.GhnCalculateFeeResponse;
import com.rehome.rehome.dto.response.GhnCreateOrderResponse;
import com.rehome.rehome.dto.response.GhnMasterDataResponse;
import com.rehome.rehome.dto.response.GhnNewProvince;
import com.rehome.rehome.dto.response.GhnNewWard;
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

    // F-054: tính phí ship thật theo tuyến kho -> địa chỉ nhận, dùng cho GET /cart/checkout-preview
    @PostMapping("/v2/shipping-order/fee")
    GhnCalculateFeeResponse calculateFee(
            @RequestHeader("Token") String token,
            @RequestHeader("ShopId") String shopId,
            @RequestBody GhnCalculateFeeRequest request);

    // Hệ hành chính 2 cấp (mới, sau sáp nhập 01/07/2025) - danh mục này mới khớp với is_new_*_address: true
    // dùng khi Tạo đơn. Xem docs/ghnapi.md "Lấy Tỉnh/Thành (Mới)".
    @GetMapping("/v3/master-data/province/all")
    GhnMasterDataResponse<GhnNewProvince> getProvinces(
            @RequestHeader("Token") String token,
            @RequestHeader("ShopId") String shopId,
            @RequestParam("offset") int offset,
            @RequestParam("limit") int limit);

    // Phường/xã thuộc thẳng tỉnh/thành (không còn quận/huyện) - xem docs/ghnapi.md "Lấy Phường/Xã (Mới)".
    @GetMapping("/v3/master-data/ward/all-by-province-id")
    GhnMasterDataResponse<GhnNewWard> getWards(
            @RequestHeader("Token") String token,
            @RequestHeader("ShopId") String shopId,
            @RequestParam("province_id") int provinceId,
            @RequestParam("offset") int offset,
            @RequestParam("limit") int limit);
}
