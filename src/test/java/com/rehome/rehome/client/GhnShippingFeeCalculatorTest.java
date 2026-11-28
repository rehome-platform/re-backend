package com.rehome.rehome.client;

import com.rehome.rehome.dto.request.GhnCalculateFeeRequest;
import com.rehome.rehome.dto.response.GhnCalculateFeeResponse;
import com.rehome.rehome.dto.response.GhnFeeData;
import com.rehome.rehome.dto.response.ResolvedAddressUnit;
import com.rehome.rehome.entity.marketplace.ShippingAddress;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.service.AddressUnitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GhnShippingFeeCalculatorTest {

    @Mock
    GhnApi ghnApi;

    @Mock
    AddressUnitService addressUnitService;

    @InjectMocks
    GhnShippingFeeCalculator calculator;

    Warehouse warehouse;
    ShippingAddress address;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(calculator, "ghnToken", "test-token");
        ReflectionTestUtils.setField(calculator, "ghnShopId", "123456");
        ReflectionTestUtils.setField(calculator, "defaultPackageWeightGrams", 500);
        ReflectionTestUtils.setField(calculator, "defaultPackageLengthCm", 30);
        ReflectionTestUtils.setField(calculator, "defaultPackageWidthCm", 20);
        ReflectionTestUtils.setField(calculator, "defaultPackageHeightCm", 10);
        ReflectionTestUtils.setField(calculator, "fallbackFee", new BigDecimal("25000"));

        warehouse = Warehouse.builder().id(UUID.randomUUID()).name("Kho ReHome TP.HCM")
                .address("123 Nguyễn Thị Thập, Phường Tân Mỹ").wardName("Phường Tân Mỹ").wardCode("1003535")
                .provinceName("Hồ Chí Minh").isNewAddress(true).build();
        address = ShippingAddress.builder().addressDetail("12 Nguyễn Huệ, Căn hộ 8A")
                .wardName("Phường Sài Gòn").wardCode("1003506").provinceName("Hồ Chí Minh").build();
    }

    private static GhnCalculateFeeResponse response(Integer total) {
        GhnFeeData data = new GhnFeeData();
        data.setTotal(total);
        GhnCalculateFeeResponse response = new GhnCalculateFeeResponse();
        response.setCode(200);
        response.setData(data);
        return response;
    }

    private GhnCalculateFeeRequest sentRequest() {
        ArgumentCaptor<GhnCalculateFeeRequest> captor = ArgumentCaptor.forClass(GhnCalculateFeeRequest.class);
        verify(ghnApi).calculateFee(eq("test-token"), eq("123456"), captor.capture());
        return captor.getValue();
    }

    @Test
    void calculate_returnsGhnTotal_onSuccess() {
        when(ghnApi.calculateFee(eq("test-token"), eq("123456"), any())).thenReturn(response(20900));

        BigDecimal fee = calculator.calculate(warehouse, address);

        assertThat(fee).isEqualTo(new BigDecimal("20900"));
    }

    @Test
    void calculate_sendsNewModelWardIdsAndFullAddresses_fromStoredWardCodes() {
        when(ghnApi.calculateFee(any(), any(), any())).thenReturn(response(20900));

        calculator.calculate(warehouse, address);

        GhnCalculateFeeRequest request = sentRequest();
        assertThat(request.getIsNewToAddress()).isTrue();
        assertThat(request.getToWardIdV2()).isEqualTo(1003506);
        assertThat(request.getToAddressV2()).isEqualTo("12 Nguyễn Huệ, Căn hộ 8A, Phường Sài Gòn, Hồ Chí Minh");
        assertThat(request.getIsNewFromAddress()).isTrue();
        assertThat(request.getFromWardIdV2()).isEqualTo(1003535);
        assertThat(request.getFromAddressV2()).isEqualTo("123 Nguyễn Thị Thập, Phường Tân Mỹ, Hồ Chí Minh");
        assertThat(request.getWeight()).isEqualTo(500);
        assertThat(request.getLength()).isEqualTo(30);
        assertThat(request.getWidth()).isEqualTo(20);
        assertThat(request.getHeight()).isEqualTo(10);
        assertThat(request.getServiceTypeId()).isEqualTo(2);
        verifyNoInteractions(addressUnitService);
    }

    @Test
    void calculate_resolvesWardIdsByName_whenStoredCodesAreMissing() {
        Warehouse withoutCode = Warehouse.builder().id(UUID.randomUUID())
                .address("123 Nguyễn Thị Thập, Phường Tân Mỹ").wardName("Phường Tân Mỹ")
                .provinceName("Hồ Chí Minh").isNewAddress(true).build();
        when(addressUnitService.resolveNewUnit("Hồ Chí Minh", "Phường Tân Mỹ"))
                .thenReturn(new ResolvedAddressUnit(1000001, "Hồ Chí Minh", "1003535", "Phường Tân Mỹ"));
        when(addressUnitService.resolveNewUnit("Hồ Chí Minh", "Phường Tam Thắng"))
                .thenReturn(new ResolvedAddressUnit(1000001, "Hồ Chí Minh", "1003645", "Phường Tam Thắng"));
        when(ghnApi.calculateFee(any(), any(), any())).thenReturn(response(46200));

        BigDecimal fee = calculator.calculate(withoutCode, "Phường Tam Thắng", "Hồ Chí Minh");

        assertThat(fee).isEqualTo(new BigDecimal("46200"));
        GhnCalculateFeeRequest request = sentRequest();
        assertThat(request.getToWardIdV2()).isEqualTo(1003645);
        assertThat(request.getToAddressV2()).isEqualTo("Phường Tam Thắng, Hồ Chí Minh");
        assertThat(request.getFromWardIdV2()).isEqualTo(1003535);
    }

    @Test
    void calculate_fallsBackToFixedFee_whenGhnCallThrows() {
        when(ghnApi.calculateFee(any(), any(), any())).thenThrow(new RuntimeException("GHN sập"));

        BigDecimal fee = calculator.calculate(warehouse, address);

        assertThat(fee).isEqualTo(new BigDecimal("25000"));
    }

    @Test
    void calculate_fallsBackToFixedFee_whenResponseHasNoData() {
        GhnCalculateFeeResponse emptyResponse = new GhnCalculateFeeResponse();
        emptyResponse.setCode(400);
        emptyResponse.setMessage("mã phường mới không tồn tại: 0");
        when(ghnApi.calculateFee(any(), any(), any())).thenReturn(emptyResponse);

        BigDecimal fee = calculator.calculate(warehouse, address);

        assertThat(fee).isEqualTo(new BigDecimal("25000"));
    }

    @Test
    void calculate_fallsBackToFixedFee_whenGhnReturnsNullTotal() {
        when(ghnApi.calculateFee(any(), any(), any())).thenReturn(response(null));

        BigDecimal fee = calculator.calculate(warehouse, address);

        assertThat(fee).isEqualTo(new BigDecimal("25000"));
    }

    @Test
    void calculate_fallsBackWithoutCallingGhn_whenWardCannotBeResolved() {
        ShippingAddress unknownWard = ShippingAddress.builder()
                .wardName("Phường Không Tồn Tại").provinceName("Hồ Chí Minh").build();
        when(addressUnitService.resolveNewUnit("Hồ Chí Minh", "Phường Không Tồn Tại"))
                .thenThrow(new BadRequestException("wardName không có trong danh mục của GHN."));

        BigDecimal fee = calculator.calculate(warehouse, unknownWard);

        assertThat(fee).isEqualTo(new BigDecimal("25000"));
        verifyNoInteractions(ghnApi);
    }

    @Test
    void calculate_fallsBackWithoutCallingGhn_whenWarehouseUsesOldAddressModel() {
        Warehouse oldModel = Warehouse.builder().id(UUID.randomUUID()).wardName("Phường Tân Phú")
                .districtName("Quận 7").provinceName("TP.HCM").isNewAddress(false).build();

        BigDecimal fee = calculator.calculate(oldModel, address);

        assertThat(fee).isEqualTo(new BigDecimal("25000"));
        verifyNoInteractions(ghnApi, addressUnitService);
    }

    @Test
    void fullAddress_appendsWardAndProvinceOnlyWhenNotAlreadyInTheDetail() {
        assertThat(GhnShippingFeeCalculator.fullAddress(
                "123 Nguyễn Thị Thập, Phường Tân Mỹ", "Phường Tân Mỹ", "Hồ Chí Minh"))
                .isEqualTo("123 Nguyễn Thị Thập, Phường Tân Mỹ, Hồ Chí Minh");
        assertThat(GhnShippingFeeCalculator.fullAddress(null, "Phường Tam Thắng", "Hồ Chí Minh"))
                .isEqualTo("Phường Tam Thắng, Hồ Chí Minh");
        assertThat(GhnShippingFeeCalculator.fullAddress("5 Lê Lợi, Phường 12", "Phường 1", "Hồ Chí Minh"))
                .isEqualTo("5 Lê Lợi, Phường 12, Phường 1, Hồ Chí Minh");
    }
}
