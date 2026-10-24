package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.client.GhnApi;
import com.rehome.rehome.dto.response.AddressUnitResponse;
import com.rehome.rehome.dto.response.GhnMasterDataResponse;
import com.rehome.rehome.dto.response.GhnNewProvince;
import com.rehome.rehome.dto.response.GhnNewWard;
import com.rehome.rehome.dto.response.ResolvedAddressUnit;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.GhnException;
import com.rehome.rehome.service.AddressUnitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
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

    @Override
    public ResolvedAddressUnit resolveNewUnit(String provinceName, String wardName) {
        if (!StringUtils.hasText(provinceName)) {
            throw new BadRequestException("provinceName là bắt buộc.");
        }
        if (!StringUtils.hasText(wardName)) {
            throw new BadRequestException("wardName là bắt buộc.");
        }

        GhnMasterDataResponse<GhnNewProvince> provinces =
                call(() -> ghnApi.getProvinces(ghnToken, ghnShopId, 0, MAX_PAGE_SIZE), "danh sách tỉnh/thành");

        GhnNewProvince province = provinces.getData().stream()
                .filter(p -> matches(p.getName(), p.getExtensionNames(), provinceName))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "provinceName \"" + provinceName + "\" không có trong danh mục tỉnh/thành của GHN."));

        GhnMasterDataResponse<GhnNewWard> wards = call(
                () -> ghnApi.getWards(ghnToken, ghnShopId, province.getId(), 0, MAX_PAGE_SIZE),
                "danh sách phường/xã");

        GhnNewWard ward = wards.getData().stream()
                .filter(w -> matches(w.getName(), w.getExtensionNames(), wardName))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "wardName \"" + wardName + "\" không thuộc " + province.getName()
                                + " trong danh mục phường/xã (hệ 2 cấp, sau sáp nhập 01/07/2025) của GHN."));

        return new ResolvedAddressUnit(province.getId(), province.getName(), ward.getName());
    }

    // GHN trả tên chuẩn ("Hồ Chí Minh") kèm extension_names ("tp.hồ chí minh", "ho chi minh"...).
    // So khớp sau khi bỏ dấu câu và khoảng trắng để tên người dùng gõ ("TP. Hồ Chí Minh") vẫn nhận ra được.
    private static boolean matches(String canonicalName, List<String> extensionNames, String input) {
        String key = normalize(input);
        if (key.isEmpty()) {
            return false;
        }
        if (key.equals(normalize(canonicalName))) {
            return true;
        }
        return extensionNames != null
                && extensionNames.stream().anyMatch(alias -> key.equals(normalize(alias)));
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder normalized = new StringBuilder(value.length());
        for (char c : value.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                normalized.append(c);
            }
        }
        return normalized.toString();
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
