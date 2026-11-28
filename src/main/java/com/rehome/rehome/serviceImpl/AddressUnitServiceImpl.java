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

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class AddressUnitServiceImpl implements AddressUnitService {

    private static final int MAX_PAGE_SIZE = 200;
    private static final long MASTER_DATA_CACHE_TTL_NANOS = Duration.ofHours(12).toNanos();
    private static final String PROVINCES_CACHE_KEY = "provinces";

    private final GhnApi ghnApi;
    private final Map<String, CachedMasterData> masterDataCache = new ConcurrentHashMap<>();

    @Value("${ghn.token}")
    private String ghnToken;

    @Value("${ghn.shop-id}")
    private String ghnShopId;

    @Override
    public List<AddressUnitResponse> getProvinces() {
        GhnMasterDataResponse<GhnNewProvince> response = cachedCall(PROVINCES_CACHE_KEY,
                () -> ghnApi.getProvinces(ghnToken, ghnShopId, 0, MAX_PAGE_SIZE), "danh sách tỉnh/thành");

        return response.getData().stream()
                .map(p -> AddressUnitResponse.builder()
                        .code(String.valueOf(p.getId()))
                        .name(p.getName())
                        .build())
                .toList();
    }

    @Override
    public List<AddressUnitResponse> getWards(int provinceId) {
        GhnMasterDataResponse<GhnNewWard> response = cachedCall(wardsCacheKey(provinceId),
                () -> ghnApi.getWards(ghnToken, ghnShopId, provinceId, 0, MAX_PAGE_SIZE), "danh sách phường/xã");

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

        GhnMasterDataResponse<GhnNewProvince> provinces = cachedCall(PROVINCES_CACHE_KEY,
                () -> ghnApi.getProvinces(ghnToken, ghnShopId, 0, MAX_PAGE_SIZE), "danh sách tỉnh/thành");

        GhnNewProvince province = provinces.getData().stream()
                .filter(p -> matches(p.getName(), p.getExtensionNames(), provinceName))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "provinceName \"" + provinceName + "\" không có trong danh mục tỉnh/thành của GHN."));

        GhnMasterDataResponse<GhnNewWard> wards = cachedCall(wardsCacheKey(province.getId()),
                () -> ghnApi.getWards(ghnToken, ghnShopId, province.getId(), 0, MAX_PAGE_SIZE),
                "danh sách phường/xã");

        GhnNewWard ward = wards.getData().stream()
                .filter(w -> matches(w.getName(), w.getExtensionNames(), wardName))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "wardName \"" + wardName + "\" không thuộc " + province.getName()
                                + " trong danh mục phường/xã (hệ 2 cấp, sau sáp nhập 01/07/2025) của GHN."));

        return new ResolvedAddressUnit(
                province.getId(), province.getName(), String.valueOf(ward.getId()), ward.getName());
    }

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

    private static String wardsCacheKey(Integer provinceId) {
        return "wards:" + provinceId;
    }

    private <T> GhnMasterDataResponse<T> cachedCall(String cacheKey, Supplier<GhnMasterDataResponse<T>> ghnCall,
                                                    String what) {
        long now = System.nanoTime();
        CachedMasterData cached = masterDataCache.get(cacheKey);
        if (cached != null && now - cached.loadedAtNanos() < MASTER_DATA_CACHE_TTL_NANOS) {
            @SuppressWarnings("unchecked")
            GhnMasterDataResponse<T> response = (GhnMasterDataResponse<T>) cached.response();
            return response;
        }
        GhnMasterDataResponse<T> response = call(ghnCall, what);
        masterDataCache.put(cacheKey, new CachedMasterData(response, now));
        return response;
    }

    private record CachedMasterData(GhnMasterDataResponse<?> response, long loadedAtNanos) {
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
