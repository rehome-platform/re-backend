package com.rehome.rehome.client;

import com.rehome.rehome.dto.response.GhnCalculateFeeResponse;
import com.rehome.rehome.dto.response.GhnFeeData;
import com.rehome.rehome.entity.marketplace.Warehouse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GhnShippingFeeCalculatorTest {

    @Mock
    GhnApi ghnApi;

    @InjectMocks
    GhnShippingFeeCalculator calculator;

    Warehouse warehouse;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(calculator, "ghnToken", "test-token");
        ReflectionTestUtils.setField(calculator, "ghnShopId", "123456");
        ReflectionTestUtils.setField(calculator, "defaultPackageWeightGrams", 500);
        ReflectionTestUtils.setField(calculator, "defaultPackageLengthCm", 30);
        ReflectionTestUtils.setField(calculator, "defaultPackageWidthCm", 20);
        ReflectionTestUtils.setField(calculator, "defaultPackageHeightCm", 10);
        ReflectionTestUtils.setField(calculator, "fallbackFee", new BigDecimal("25000"));

        warehouse = Warehouse.builder().id(UUID.randomUUID()).name("Kho Quận 7")
                .wardName("Phường Tân Phú").provinceName("TP.HCM").isNewAddress(true).build();
    }

    @Test
    void calculate_returnsGhnTotal_onSuccess() {
        GhnFeeData data = new GhnFeeData();
        data.setTotal(19500);
        GhnCalculateFeeResponse response = new GhnCalculateFeeResponse();
        response.setCode(200);
        response.setData(data);

        when(ghnApi.calculateFee(eq("test-token"), eq("123456"), any())).thenReturn(response);

        BigDecimal fee = calculator.calculate(warehouse, "Phường Bến Nghé", "TP.HCM");

        assertThat(fee).isEqualTo(new BigDecimal("19500"));
    }

    @Test
    void calculate_fallsBackToFixedFee_whenGhnCallThrows() {
        when(ghnApi.calculateFee(any(), any(), any())).thenThrow(new RuntimeException("GHN sập"));

        BigDecimal fee = calculator.calculate(warehouse, "Phường Bến Nghé", "TP.HCM");

        assertThat(fee).isEqualTo(new BigDecimal("25000"));
    }

    @Test
    void calculate_fallsBackToFixedFee_whenResponseHasNoData() {
        GhnCalculateFeeResponse emptyResponse = new GhnCalculateFeeResponse();
        emptyResponse.setCode(400);
        emptyResponse.setMessage("Lỗi tham số");

        when(ghnApi.calculateFee(any(), any(), any())).thenReturn(emptyResponse);

        BigDecimal fee = calculator.calculate(warehouse, "Phường Bến Nghé", "TP.HCM");

        assertThat(fee).isEqualTo(new BigDecimal("25000"));
    }

    @Test
    void calculate_fallsBackToFixedFee_whenGhnReturnsNullTotal() {
        GhnFeeData data = new GhnFeeData();
        data.setTotal(null);
        GhnCalculateFeeResponse response = new GhnCalculateFeeResponse();
        response.setCode(200);
        response.setData(data);

        when(ghnApi.calculateFee(any(), any(), any())).thenReturn(response);

        BigDecimal fee = calculator.calculate(warehouse, "Phường Bến Nghé", "TP.HCM");

        assertThat(fee).isEqualTo(new BigDecimal("25000"));
    }

    @Test
    void calculate_omitsFromDistrictName_whenWarehouseUsesNewAddress() {
        GhnFeeData data = new GhnFeeData();
        data.setTotal(19500);
        GhnCalculateFeeResponse response = new GhnCalculateFeeResponse();
        response.setData(data);
        when(ghnApi.calculateFee(any(), any(), any())).thenReturn(response);

        calculator.calculate(warehouse, "Phường Bến Nghé", "TP.HCM");

        org.mockito.ArgumentCaptor<com.rehome.rehome.dto.request.GhnCalculateFeeRequest> captor =
                org.mockito.ArgumentCaptor.forClass(com.rehome.rehome.dto.request.GhnCalculateFeeRequest.class);
        org.mockito.Mockito.verify(ghnApi).calculateFee(any(), any(), captor.capture());

        assertThat(captor.getValue().getFromDistrictName()).isNull();
        assertThat(captor.getValue().getIsNewFromAddress()).isTrue();
        assertThat(captor.getValue().getIsNewToAddress()).isTrue();
        assertThat(captor.getValue().getToWardName()).isEqualTo("Phường Bến Nghé");
        assertThat(captor.getValue().getToProvinceName()).isEqualTo("TP.HCM");
    }
}
