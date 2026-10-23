package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.client.GhnApi;
import com.rehome.rehome.dto.response.AddressUnitResponse;
import com.rehome.rehome.dto.response.GhnDistrict;
import com.rehome.rehome.dto.response.GhnMasterDataResponse;
import com.rehome.rehome.dto.response.GhnProvince;
import com.rehome.rehome.dto.response.GhnWard;
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

    private final GhnApi ghnApi;

    @Value("${ghn.token}")
    private String ghnToken;

    @Value("${ghn.shop-id}")
    private String ghnShopId;

    @Override
    public List<AddressUnitResponse> getProvinces() {
        GhnMasterDataResponse<GhnProvince> response =
                call(() -> ghnApi.getProvinces(ghnToken, ghnShopId), "danh sách tỉnh/thành");

        return response.getData().stream()
                .map(p -> AddressUnitResponse.builder()
                        .code(String.valueOf(p.getProvinceId()))
                        .name(p.getProvinceName())
                        .build())
                .toList();
    }

    @Override
    public List<AddressUnitResponse> getDistricts(int provinceId) {
        GhnMasterDataResponse<GhnDistrict> response =
                call(() -> ghnApi.getDistricts(ghnToken, ghnShopId, provinceId), "danh sách quận/huyện");

        return response.getData().stream()
                .map(d -> AddressUnitResponse.builder()
                        .code(String.valueOf(d.getDistrictId()))
                        .name(d.getDistrictName())
                        .build())
                .toList();
    }

    @Override
    public List<AddressUnitResponse> getWards(int districtId) {
        GhnMasterDataResponse<GhnWard> response =
                call(() -> ghnApi.getWards(ghnToken, ghnShopId, districtId), "danh sách phường/xã");

        return response.getData().stream()
                .map(w -> AddressUnitResponse.builder()
                        .code(w.getWardCode())
                        .name(w.getWardName())
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
