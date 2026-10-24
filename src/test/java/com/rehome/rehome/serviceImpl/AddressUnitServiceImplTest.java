package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.client.GhnApi;
import com.rehome.rehome.dto.response.GhnMasterDataResponse;
import com.rehome.rehome.dto.response.GhnNewProvince;
import com.rehome.rehome.dto.response.GhnNewWard;
import com.rehome.rehome.dto.response.ResolvedAddressUnit;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.GhnException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddressUnitServiceImplTest {

    private static final int HCM_ID = 1000001;
    private static final String TOKEN = "ghn-token";
    private static final String SHOP_ID = "92837";

    @Mock GhnApi ghnApi;

    @InjectMocks
    AddressUnitServiceImpl addressUnitService;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(addressUnitService, "ghnToken", TOKEN);
        ReflectionTestUtils.setField(addressUnitService, "ghnShopId", SHOP_ID);
    }

    private void stubProvinces(GhnNewProvince... provinces) {
        when(ghnApi.getProvinces(eq(TOKEN), eq(SHOP_ID), anyInt(), anyInt()))
                .thenReturn(new GhnMasterDataResponse<>(200, "Success", List.of(provinces)));
    }

    private void stubWards(int provinceId, GhnNewWard... wards) {
        when(ghnApi.getWards(eq(TOKEN), eq(SHOP_ID), eq(provinceId), anyInt(), anyInt()))
                .thenReturn(new GhnMasterDataResponse<>(200, "Success", List.of(wards)));
    }

    private GhnNewProvince hoChiMinh() {
        return new GhnNewProvince(HCM_ID, "Hồ Chí Minh",
                List.of("hồ chí minh", "tp.hồ chí minh", "thành phố hồ chí minh", "hcm", "ho chi minh"));
    }

    private GhnNewWard benThanh() {
        return new GhnNewWard(1003646, "Phường Bến Thành",
                List.of("phường bến thành", "p.bến thành", "bến thành", "phuong ben thanh"));
    }

    @Test
    void resolveNewUnit_returnsCanonicalNames_whenNamesMatchExactly() {
        stubProvinces(hoChiMinh());
        stubWards(HCM_ID, benThanh());

        ResolvedAddressUnit unit = addressUnitService.resolveNewUnit("Hồ Chí Minh", "Phường Bến Thành");

        assertThat(unit.provinceId()).isEqualTo(HCM_ID);
        assertThat(unit.provinceName()).isEqualTo("Hồ Chí Minh");
        assertThat(unit.wardName()).isEqualTo("Phường Bến Thành");
    }

    // "TP. Hồ Chí Minh" là tên FE/người dùng hay gõ nhưng GHN gọi tỉnh này là "Hồ Chí Minh";
    // phải chuẩn hóa lại chứ không lưu nguyên văn, nếu không vận đơn bị TO_ADDRESS_CONFLICT
    @Test
    void resolveNewUnit_normalizesFreeTextNames_viaExtensionNames() {
        stubProvinces(hoChiMinh());
        stubWards(HCM_ID, benThanh());

        ResolvedAddressUnit unit = addressUnitService.resolveNewUnit("TP. Hồ Chí Minh", "P. Bến Thành");

        assertThat(unit.provinceName()).isEqualTo("Hồ Chí Minh");
        assertThat(unit.wardName()).isEqualTo("Phường Bến Thành");
    }

    @Test
    void resolveNewUnit_matchesIgnoringCaseAndExtraWhitespace() {
        stubProvinces(hoChiMinh());
        stubWards(HCM_ID, benThanh());

        ResolvedAddressUnit unit = addressUnitService.resolveNewUnit("  hcm  ", "PHƯỜNG BẾN THÀNH");

        assertThat(unit.provinceName()).isEqualTo("Hồ Chí Minh");
        assertThat(unit.wardName()).isEqualTo("Phường Bến Thành");
    }

    @Test
    void resolveNewUnit_throwsBadRequest_whenProvinceIsNotInCatalog() {
        stubProvinces(hoChiMinh());

        assertThatThrownBy(() -> addressUnitService.resolveNewUnit("Tỉnh Không Tồn Tại", "Phường Bến Thành"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("không có trong danh mục tỉnh/thành");

        verify(ghnApi, never()).getWards(eq(TOKEN), eq(SHOP_ID), anyInt(), anyInt(), anyInt());
    }

    // Phường/xã hệ cũ đã bị sáp nhập 01/07/2025 không còn trong danh mục 2 cấp - phải chặn tại đây
    @Test
    void resolveNewUnit_throwsBadRequest_whenWardDoesNotBelongToProvince() {
        stubProvinces(hoChiMinh());
        stubWards(HCM_ID, benThanh());

        assertThatThrownBy(() -> addressUnitService.resolveNewUnit("Hồ Chí Minh", "Phường 5"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("không thuộc Hồ Chí Minh");
    }

    @Test
    void resolveNewUnit_throwsBadRequest_whenProvinceNameIsBlank() {
        assertThatThrownBy(() -> addressUnitService.resolveNewUnit("  ", "Phường Bến Thành"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("provinceName là bắt buộc.");

        verify(ghnApi, never()).getProvinces(eq(TOKEN), eq(SHOP_ID), anyInt(), anyInt());
    }

    @Test
    void resolveNewUnit_throwsBadRequest_whenWardNameIsBlank() {
        assertThatThrownBy(() -> addressUnitService.resolveNewUnit("Hồ Chí Minh", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("wardName là bắt buộc.");

        verify(ghnApi, never()).getProvinces(eq(TOKEN), eq(SHOP_ID), anyInt(), anyInt());
    }

    @Test
    void resolveNewUnit_throwsGhnException_whenCatalogCallFails() {
        when(ghnApi.getProvinces(eq(TOKEN), eq(SHOP_ID), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("503 Service Unavailable"));

        assertThatThrownBy(() -> addressUnitService.resolveNewUnit("Hồ Chí Minh", "Phường Bến Thành"))
                .isInstanceOf(GhnException.class)
                .hasMessageContaining("GHN_ERROR");
    }
}
