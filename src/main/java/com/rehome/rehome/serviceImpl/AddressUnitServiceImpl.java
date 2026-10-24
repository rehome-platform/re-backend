package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.client.GhnApi;
import com.rehome.rehome.dto.response.AddressUnitResponse;
import com.rehome.rehome.dto.response.GhnMasterDataResponse;
import com.rehome.rehome.dto.response.GhnNewProvince;
import com.rehome.rehome.dto.response.GhnNewWard;
import com.rehome.rehome.exception.GhnException;
import com.rehome.rehome.service.AddressUnitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class AddressUnitServiceImpl implements AddressUnitService {

    // GHN hiện có 34 tỉnh/thành và tối đa 168 phường/xã/tỉnh (TP.HCM) sau sáp nhập - 200 đủ lấy hết trong 1 lần gọi
    private static final int MAX_PAGE_SIZE = 200;

    private final GhnApi ghnApi;

    @Value("${ghn.token}")
    private String ghnToken;

    @Value("${ghn.shop-id}")
    private String ghnShopId;

    @Override
    public List<AddressUnitResponse> getProvinces() {
        GhnMasterDataResponse<GhnNewProvince> response =
                call(() -> ghnApi.getProvinces(ghnToken, ghnShopId, 0, MAX_PAGE_SIZE), "danh sách tỉnh/thành");

        return response.getData().stream()
                .map(p -> AddressUnitResponse.builder()
                        .code(String.valueOf(p.getId()))
                        .name(p.getName())
                        .build())
                .toList();
    }

    @Override
    public List<AddressUnitResponse> getWards(int provinceId) {
        GhnMasterDataResponse<GhnNewWard> response =
                call(() -> ghnApi.getWards(ghnToken, ghnShopId, provinceId, 0, MAX_PAGE_SIZE), "danh sách phường/xã");

        return response.getData().stream()
                .map(w -> AddressUnitResponse.builder()
                        .code(String.valueOf(w.getId()))
                        .name(w.getName())
                        .build())
                .toList();
    }

    private <T> GhnMasterDataResponse<T> call(Supplier<GhnMasterDataResponse<T>> ghnCall, String what) {
        GhnMasterDataResponse<T> response;
        try {
            response = ghnCall.get();
        } catch (Exception ex) {
            log.error(">>>> Loi goi GHN lay {}: {}", what, ex.getMessage());
            throw new GhnException("GHN_ERROR: không thể lấy " + what + ", vui lòng thử lại.");
        }

        if (response == null || response.getData() == null) {
            log.error(">>>> GHN tra ve rong khi lay {}", what);
            throw new GhnException("GHN_ERROR: không thể lấy " + what + ", vui lòng thử lại.");
        }

        return response;
    }
}
