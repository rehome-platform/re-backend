package com.rehome.rehome.client;

import com.rehome.rehome.dto.request.GhnCalculateFeeRequest;
import com.rehome.rehome.dto.response.GhnCalculateFeeResponse;
import com.rehome.rehome.entity.marketplace.ShippingAddress;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.service.AddressUnitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class GhnShippingFeeCalculator {

    private final GhnApi ghnApi;
    private final AddressUnitService addressUnitService;

    @Value("${ghn.token}")
    private String ghnToken;

    @Value("${ghn.shop-id}")
    private String ghnShopId;

    @Value("${ghn.default-package.weight-grams}")
    private int defaultPackageWeightGrams;

    @Value("${ghn.default-package.length-cm}")
    private int defaultPackageLengthCm;

    @Value("${ghn.default-package.width-cm}")
    private int defaultPackageWidthCm;

    @Value("${ghn.default-package.height-cm}")
    private int defaultPackageHeightCm;

    @Value("${app.order.shipping-fee}")
    private BigDecimal fallbackFee;

    public BigDecimal getFallbackFee() {
        return fallbackFee;
    }

    public BigDecimal calculate(Warehouse fromWarehouse, String toWardName, String toProvinceName) {
        return calculate(fromWarehouse, toWardName, toProvinceName, null, null);
    }

    public BigDecimal calculate(Warehouse fromWarehouse, ShippingAddress toAddress) {
        if (toAddress == null) {
            return calculate(fromWarehouse, null, null, null, null);
        }
        return calculate(fromWarehouse, toAddress.getWardName(), toAddress.getProvinceName(),
                toAddress.getWardCode(), toAddress.getAddressDetail());
    }

    private BigDecimal calculate(Warehouse fromWarehouse, String toWardName, String toProvinceName,
                                 String toWardCode, String toAddressDetail) {
        try {
            if (!Boolean.TRUE.equals(fromWarehouse.getIsNewAddress())) {
                throw new IllegalStateException("kho chưa dùng địa chỉ hệ 2 cấp (sau 01/07/2025)");
            }
            int fromWardId = resolveWardId(fromWarehouse.getWardCode(),
                    fromWarehouse.getProvinceName(), fromWarehouse.getWardName());
            int toWardId = resolveWardId(toWardCode, toProvinceName, toWardName);

            GhnCalculateFeeRequest feeRequest = GhnCalculateFeeRequest.builder()
                    .isNewToAddress(true)
                    .toWardIdV2(toWardId)
                    .toAddressV2(fullAddress(toAddressDetail, toWardName, toProvinceName))
                    .isNewFromAddress(true)
                    .fromWardIdV2(fromWardId)
                    .fromAddressV2(fullAddress(fromWarehouse.getAddress(),
                            fromWarehouse.getWardName(), fromWarehouse.getProvinceName()))
                    .weight(defaultPackageWeightGrams)
                    .length(defaultPackageLengthCm)
                    .width(defaultPackageWidthCm)
                    .height(defaultPackageHeightCm)
                    .serviceTypeId(2)
                    .build();

            GhnCalculateFeeResponse response = ghnApi.calculateFee(ghnToken, ghnShopId, feeRequest);

            if (response != null && response.getData() != null && response.getData().getTotal() != null) {
                return BigDecimal.valueOf(response.getData().getTotal());
            }
            log.warn(">>>> GHN khong tra ve phi ship, dung phi mac dinh. warehouseId={}, message={}",
                    fromWarehouse.getId(), response != null ? response.getMessage() : null);
        } catch (Exception ex) {
            log.warn(">>>> Loi goi GHN tinh phi ship, dung phi mac dinh. warehouseId={}, error={}",
                    fromWarehouse.getId(), ex.getMessage());
        }
        return fallbackFee;
    }

    private int resolveWardId(String storedWardCode, String provinceName, String wardName) {
        Integer stored = parseWardId(storedWardCode);
        if (stored != null) {
            return stored;
        }
        Integer resolved = parseWardId(addressUnitService.resolveNewUnit(provinceName, wardName).wardCode());
        if (resolved == null) {
            throw new IllegalStateException(
                    "không tra được mã phường mới của \"" + wardName + "\", \"" + provinceName + "\"");
        }
        return resolved;
    }

    private static Integer parseWardId(String wardCode) {
        if (!StringUtils.hasText(wardCode)) {
            return null;
        }
        try {
            int id = Integer.parseInt(wardCode.trim());
            return id > 0 ? id : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static String fullAddress(String detail, String wardName, String provinceName) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(detail)) {
            for (String segment : detail.split(",")) {
                if (StringUtils.hasText(segment)) {
                    parts.add(segment.trim());
                }
            }
        }
        addIfMissing(parts, wardName);
        addIfMissing(parts, provinceName);
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private static void addIfMissing(List<String> parts, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        String trimmed = value.trim();
        if (parts.stream().noneMatch(part -> part.equalsIgnoreCase(trimmed))) {
            parts.add(trimmed);
        }
    }
}
