package com.rehome.rehome.client;

import com.rehome.rehome.dto.request.GhnCalculateFeeRequest;
import com.rehome.rehome.dto.response.GhnCalculateFeeResponse;
import com.rehome.rehome.entity.marketplace.Warehouse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

// Dùng chung cho mọi nơi cần phí ship thật theo tuyến kho -> địa chỉ nhận
// (GET /cart/checkout-preview và POST /orders). Lỗi/không có kết quả từ GHN thì rơi về phí cố định
// app.order.shipping-fee, không chặn luồng mua hàng vì GHN có thể sập/token hết hạn bất cứ lúc nào.
@Component
@RequiredArgsConstructor
@Slf4j
public class GhnShippingFeeCalculator {

    private final GhnApi ghnApi;

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

    public BigDecimal calculate(Warehouse fromWarehouse, String toWardName, String toProvinceName) {
        try {
            GhnCalculateFeeRequest feeRequest = GhnCalculateFeeRequest.builder()
                    .toWardName(trimToNull(toWardName))
                    .toProvinceName(trimToNull(toProvinceName))
                    .isNewToAddress(true)
                    .fromWardName(trimToNull(fromWarehouse.getWardName()))
                    .fromDistrictName(Boolean.TRUE.equals(fromWarehouse.getIsNewAddress())
                            ? null : trimToNull(fromWarehouse.getDistrictName()))
                    .fromProvinceName(trimToNull(fromWarehouse.getProvinceName()))
                    .isNewFromAddress(Boolean.TRUE.equals(fromWarehouse.getIsNewAddress()))
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

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
