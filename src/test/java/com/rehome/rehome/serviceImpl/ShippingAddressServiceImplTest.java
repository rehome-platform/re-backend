package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.request.AddShippingAddressRequest;
import com.rehome.rehome.dto.response.ResolvedAddressUnit;
import com.rehome.rehome.dto.response.ShippingAddressResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.marketplace.ShippingAddress;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ConflictException;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.OrderRepository;
import com.rehome.rehome.repository.ShippingAddressRepository;
import com.rehome.rehome.service.AddressUnitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShippingAddressServiceImplTest {

    @Mock AccountRepository accountRepository;
    @Mock ShippingAddressRepository shippingAddressRepository;
    @Mock OrderRepository orderRepository;
    @Mock AddressUnitService addressUnitService;

    @InjectMocks
    ShippingAddressServiceImpl shippingAddressService;

    Account member;
    String email;

    @BeforeEach
    void setup() {
        email = "member@example.com";
        member = Account.builder().id(UUID.randomUUID()).email(email).build();

        // Mặc định coi như phường/xã + tỉnh/thành khớp danh mục GHN; test nào cần ca không khớp
        // thì stub lại resolveNewUnit trong chính test đó
        lenient().when(addressUnitService.resolveNewUnit(any(), any()))
                .thenReturn(new ResolvedAddressUnit(1000001, "Hồ Chí Minh", "Phường Bến Thành"));
    }

    @Test
    void getAddresses_throwsNotFound_whenAccountDoesNotExist() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shippingAddressService.getAddresses(email))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account không tồn tại");
    }

    @Test
    void getAddresses_returnsEmptyList_whenMemberHasNoAddress() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(shippingAddressRepository.findByMember_IdOrderByIsDefaultDesc(member.getId()))
                .thenReturn(List.of());

        List<ShippingAddressResponse> result = shippingAddressService.getAddresses(email);

        assertThat(result).isEmpty();
    }

    @Test
    void getAddresses_returnsMappedAddresses_onSuccess() {
        ShippingAddress defaultAddress = ShippingAddress.builder()
                .id(UUID.randomUUID())
                .member(member)
                .recipientName("Tạ Thị Ngọc Anh")
                .phone("0912345678")
                .addressDetail("12 Nguyễn Trãi, Q.1, TP.HCM")
                .isDefault(true)
                .build();

        ShippingAddress otherAddress = ShippingAddress.builder()
                .id(UUID.randomUUID())
                .member(member)
                .recipientName("Nguyễn Văn A")
                .phone("0987654321")
                .addressDetail("45 Lê Lợi, Q.3, TP.HCM")
                .isDefault(false)
                .build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(shippingAddressRepository.findByMember_IdOrderByIsDefaultDesc(member.getId()))
                .thenReturn(List.of(defaultAddress, otherAddress));

        List<ShippingAddressResponse> result = shippingAddressService.getAddresses(email);

        assertThat(result).hasSize(2);

        ShippingAddressResponse first = result.get(0);
        assertThat(first.getId()).isEqualTo(defaultAddress.getId().toString());
        assertThat(first.getRecipientName()).isEqualTo("Tạ Thị Ngọc Anh");
        assertThat(first.getPhone()).isEqualTo("0912345678");
        assertThat(first.getAddressDetail()).isEqualTo("12 Nguyễn Trãi, Q.1, TP.HCM");
        assertThat(first.getIsDefault()).isTrue();

        ShippingAddressResponse second = result.get(1);
        assertThat(second.getId()).isEqualTo(otherAddress.getId().toString());
        assertThat(second.getIsDefault()).isFalse();
    }

    @Test
    void createAddress_throwsBadRequest_whenRecipientNameIsBlank() {
        AddShippingAddressRequest request = new AddShippingAddressRequest("  ", "0912345678", "12 Nguyễn Trãi",
                "Phường Bến Thành", "TP. Hồ Chí Minh", true);

        assertThatThrownBy(() -> shippingAddressService.createAddress(email, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("recipientName là bắt buộc.");

        verify(accountRepository, never()).findByEmail(any());
    }

    @Test
    void createAddress_throwsBadRequest_whenPhoneIsBlank() {
        AddShippingAddressRequest request = new AddShippingAddressRequest("Tạ Thị Ngọc Anh", "", "12 Nguyễn Trãi",
                "Phường Bến Thành", "TP. Hồ Chí Minh", true);

        assertThatThrownBy(() -> shippingAddressService.createAddress(email, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("phone là bắt buộc.");
    }

    @Test
    void createAddress_throwsBadRequest_whenAddressDetailIsBlank() {
        AddShippingAddressRequest request = new AddShippingAddressRequest("Tạ Thị Ngọc Anh", "0912345678", null,
                "Phường Bến Thành", "TP. Hồ Chí Minh", true);

        assertThatThrownBy(() -> shippingAddressService.createAddress(email, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("addressDetail là bắt buộc.");
    }

    @Test
    void createAddress_throwsBadRequest_whenWardNameIsBlank() {
        AddShippingAddressRequest request = new AddShippingAddressRequest("Tạ Thị Ngọc Anh", "0912345678",
                "12 Nguyễn Trãi", "  ", "Hồ Chí Minh", true);

        assertThatThrownBy(() -> shippingAddressService.createAddress(email, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("wardName là bắt buộc.");

        verify(addressUnitService, never()).resolveNewUnit(any(), any());
    }

    @Test
    void createAddress_throwsBadRequest_whenProvinceNameIsBlank() {
        AddShippingAddressRequest request = new AddShippingAddressRequest("Tạ Thị Ngọc Anh", "0912345678",
                "12 Nguyễn Trãi", "Phường Bến Thành", null, true);

        assertThatThrownBy(() -> shippingAddressService.createAddress(email, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("provinceName là bắt buộc.");

        verify(addressUnitService, never()).resolveNewUnit(any(), any());
    }

    @Test
    void createAddress_throwsBadRequest_whenUnitIsNotInGhnCatalog() {
        AddShippingAddressRequest request = new AddShippingAddressRequest(
                "Tạ Thị Ngọc Anh", "0912345678", "12 Nguyễn Trãi, Q.1, TP.HCM",
                "Phường Không Tồn Tại", "Hồ Chí Minh", true);

        when(addressUnitService.resolveNewUnit("Hồ Chí Minh", "Phường Không Tồn Tại"))
                .thenThrow(new BadRequestException("wardName \"Phường Không Tồn Tại\" không thuộc Hồ Chí Minh"
                        + " trong danh mục phường/xã (hệ 2 cấp, sau sáp nhập 01/07/2025) của GHN."));

        assertThatThrownBy(() -> shippingAddressService.createAddress(email, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("không thuộc Hồ Chí Minh");

        verify(accountRepository, never()).findByEmail(any());
        verify(shippingAddressRepository, never()).save(any());
    }

    // Tên tự do của FE phải được chuẩn hóa về đúng tên GHN trả về, nếu không vận đơn sẽ bị
    // TO_ADDRESS_CONFLICT ở bước POST /orders/{id}/shipment
    @Test
    void createAddress_storesCanonicalUnitNames_whenRequestUsesFreeTextProvince() {
        AddShippingAddressRequest request = new AddShippingAddressRequest(
                "Tạ Thị Ngọc Anh", "0912345678", "12 Nguyễn Trãi, Q.1, TP.HCM",
                "P. Bến Thành", "TP. Hồ Chí Minh", true);

        when(addressUnitService.resolveNewUnit("TP. Hồ Chí Minh", "P. Bến Thành"))
                .thenReturn(new ResolvedAddressUnit(1000001, "Hồ Chí Minh", "Phường Bến Thành"));
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(shippingAddressRepository.countByMember_Id(member.getId())).thenReturn(0L);
        when(shippingAddressRepository.save(any())).thenAnswer(invocation -> {
            ShippingAddress saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        ShippingAddressResponse response = shippingAddressService.createAddress(email, request);

        assertThat(response.getWardName()).isEqualTo("Phường Bến Thành");
        assertThat(response.getProvinceName()).isEqualTo("Hồ Chí Minh");

        ArgumentCaptor<ShippingAddress> captor = ArgumentCaptor.forClass(ShippingAddress.class);
        verify(shippingAddressRepository).save(captor.capture());
        assertThat(captor.getValue().getWardName()).isEqualTo("Phường Bến Thành");
        assertThat(captor.getValue().getProvinceName()).isEqualTo("Hồ Chí Minh");
    }

    @Test
    void createAddress_throwsNotFound_whenAccountDoesNotExist() {
        AddShippingAddressRequest request = new AddShippingAddressRequest(
                "Tạ Thị Ngọc Anh", "0912345678", "12 Nguyễn Trãi, Q.1, TP.HCM",
                "Phường Bến Thành", "TP. Hồ Chí Minh", true);

        when(accountRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shippingAddressService.createAddress(email, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account không tồn tại");

        verify(shippingAddressRepository, never()).save(any());
    }

    @Test
    void createAddress_clearsPreviousDefault_whenIsDefaultIsTrue() {
        AddShippingAddressRequest request = new AddShippingAddressRequest(
                "Tạ Thị Ngọc Anh", "0912345678", "12 Nguyễn Trãi, Q.1, TP.HCM",
                "Phường Bến Thành", "TP. Hồ Chí Minh", true);

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(shippingAddressRepository.countByMember_Id(member.getId())).thenReturn(1L);
        when(shippingAddressRepository.save(any())).thenAnswer(invocation -> {
            ShippingAddress saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        ShippingAddressResponse response = shippingAddressService.createAddress(email, request);

        assertThat(response.getId()).isNotNull();
        assertThat(response.getRecipientName()).isEqualTo("Tạ Thị Ngọc Anh");
        assertThat(response.getIsDefault()).isTrue();

        verify(shippingAddressRepository).clearDefaultForMember(member.getId());

        ArgumentCaptor<ShippingAddress> captor = ArgumentCaptor.forClass(ShippingAddress.class);
        verify(shippingAddressRepository).save(captor.capture());
        assertThat(captor.getValue().getMember()).isEqualTo(member);
        assertThat(captor.getValue().getPhone()).isEqualTo("0912345678");
        assertThat(captor.getValue().getAddressDetail()).isEqualTo("12 Nguyễn Trãi, Q.1, TP.HCM");
        assertThat(captor.getValue().getIsDefault()).isTrue();
    }

    @Test
    void createAddress_doesNotClearDefault_whenIsDefaultIsFalseOrNull_andNotFirstAddress() {
        AddShippingAddressRequest request = new AddShippingAddressRequest(
                "Nguyễn Văn A", "0987654321", "45 Lê Lợi, Q.3, TP.HCM",
                "Phường 5", "TP. Hồ Chí Minh", null);

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(shippingAddressRepository.countByMember_Id(member.getId())).thenReturn(1L);
        when(shippingAddressRepository.save(any())).thenAnswer(invocation -> {
            ShippingAddress saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        ShippingAddressResponse response = shippingAddressService.createAddress(email, request);

        assertThat(response.getIsDefault()).isFalse();
        verify(shippingAddressRepository, never()).clearDefaultForMember(any());
    }

    @Test
    void createAddress_forcesDefault_whenItIsMembersFirstAddress() {
        AddShippingAddressRequest request = new AddShippingAddressRequest(
                "Nguyễn Văn A", "0987654321", "45 Lê Lợi, Q.3, TP.HCM",
                "Phường 5", "TP. Hồ Chí Minh", null);

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(shippingAddressRepository.countByMember_Id(member.getId())).thenReturn(0L);
        when(shippingAddressRepository.save(any())).thenAnswer(invocation -> {
            ShippingAddress saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        ShippingAddressResponse response = shippingAddressService.createAddress(email, request);

        assertThat(response.getIsDefault()).isTrue();
        verify(shippingAddressRepository).clearDefaultForMember(member.getId());
    }

    @Test
    void createAddress_throwsConflict_whenMemberAlreadyHasMaxAddresses() {
        AddShippingAddressRequest request = new AddShippingAddressRequest(
                "Nguyễn Văn A", "0987654321", "45 Lê Lợi, Q.3, TP.HCM",
                "Phường 5", "TP. Hồ Chí Minh", null);

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(shippingAddressRepository.countByMember_Id(member.getId())).thenReturn(5L);

        assertThatThrownBy(() -> shippingAddressService.createAddress(email, request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Đã đạt số lượng tối đa 5 địa chỉ.");

        verify(shippingAddressRepository, never()).save(any());
    }

    @Test
    void updateAddress_throwsBadRequest_whenRecipientNameIsBlank() {
        AddShippingAddressRequest request = new AddShippingAddressRequest("  ", "0912345678", "12 Nguyễn Trãi",
                "Phường Bến Thành", "TP. Hồ Chí Minh", true);
        UUID addressId = UUID.randomUUID();

        assertThatThrownBy(() -> shippingAddressService.updateAddress(email, addressId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("recipientName là bắt buộc.");

        verify(accountRepository, never()).findByEmail(any());
    }

    @Test
    void updateAddress_throwsBadRequest_whenPhoneIsBlank() {
        AddShippingAddressRequest request = new AddShippingAddressRequest("Tạ Thị Ngọc Anh", "", "12 Nguyễn Trãi",
                "Phường Bến Thành", "TP. Hồ Chí Minh", true);
        UUID addressId = UUID.randomUUID();

        assertThatThrownBy(() -> shippingAddressService.updateAddress(email, addressId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("phone là bắt buộc.");
    }

    @Test
    void updateAddress_throwsBadRequest_whenAddressDetailIsBlank() {
        AddShippingAddressRequest request = new AddShippingAddressRequest("Tạ Thị Ngọc Anh", "0912345678", null,
                "Phường Bến Thành", "TP. Hồ Chí Minh", true);
        UUID addressId = UUID.randomUUID();

        assertThatThrownBy(() -> shippingAddressService.updateAddress(email, addressId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("addressDetail là bắt buộc.");
    }

    @Test
    void updateAddress_throwsNotFound_whenAccountDoesNotExist() {
        AddShippingAddressRequest request = new AddShippingAddressRequest(
                "Tạ Thị Ngọc Anh", "0912345679", "12 Nguyễn Trãi, Q.1, TP.HCM",
                "Phường Bến Thành", "TP. Hồ Chí Minh", true);
        UUID addressId = UUID.randomUUID();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shippingAddressService.updateAddress(email, addressId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account không tồn tại");

        verify(shippingAddressRepository, never()).save(any());
    }

    @Test
    void updateAddress_throwsNotFound_whenAddressDoesNotExist() {
        AddShippingAddressRequest request = new AddShippingAddressRequest(
                "Tạ Thị Ngọc Anh", "0912345679", "12 Nguyễn Trãi, Q.1, TP.HCM",
                "Phường Bến Thành", "TP. Hồ Chí Minh", true);
        UUID addressId = UUID.randomUUID();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(shippingAddressRepository.findById(addressId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shippingAddressService.updateAddress(email, addressId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy địa chỉ.");

        verify(shippingAddressRepository, never()).save(any());
    }

    @Test
    void updateAddress_throwsNotFound_whenAddressBelongsToAnotherMember() {
        AddShippingAddressRequest request = new AddShippingAddressRequest(
                "Tạ Thị Ngọc Anh", "0912345679", "12 Nguyễn Trãi, Q.1, TP.HCM",
                "Phường Bến Thành", "TP. Hồ Chí Minh", true);
        UUID addressId = UUID.randomUUID();

        Account anotherMember = Account.builder().id(UUID.randomUUID()).email("other@example.com").build();
        ShippingAddress existing = ShippingAddress.builder()
                .id(addressId)
                .member(anotherMember)
                .recipientName("Nguyễn Văn A")
                .phone("0987654321")
                .addressDetail("45 Lê Lợi, Q.3, TP.HCM")
                .isDefault(false)
                .build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(shippingAddressRepository.findById(addressId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> shippingAddressService.updateAddress(email, addressId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy địa chỉ.");

        verify(shippingAddressRepository, never()).save(any());
    }

    @Test
    void updateAddress_updatesFieldsAndClearsPreviousDefault_whenIsDefaultBecomesTrue() {
        AddShippingAddressRequest request = new AddShippingAddressRequest(
                "Tạ Thị Ngọc Anh", "0912345679", "12 Nguyễn Trãi, Q.1, TP.HCM",
                "Phường Bến Thành", "TP. Hồ Chí Minh", true);
        UUID addressId = UUID.randomUUID();

        ShippingAddress existing = ShippingAddress.builder()
                .id(addressId)
                .member(member)
                .recipientName("Tên cũ")
                .phone("0900000000")
                .addressDetail("Địa chỉ cũ")
                .isDefault(false)
                .build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(shippingAddressRepository.findById(addressId)).thenReturn(Optional.of(existing));

        ShippingAddressResponse response = shippingAddressService.updateAddress(email, addressId, request);

        assertThat(response.getId()).isEqualTo(addressId.toString());
        assertThat(response.getPhone()).isEqualTo("0912345679");

        verify(shippingAddressRepository).clearDefaultForMember(member.getId());

        ArgumentCaptor<ShippingAddress> captor = ArgumentCaptor.forClass(ShippingAddress.class);
        verify(shippingAddressRepository).save(captor.capture());
        assertThat(captor.getValue().getRecipientName()).isEqualTo("Tạ Thị Ngọc Anh");
        assertThat(captor.getValue().getPhone()).isEqualTo("0912345679");
        assertThat(captor.getValue().getAddressDetail()).isEqualTo("12 Nguyễn Trãi, Q.1, TP.HCM");
        assertThat(captor.getValue().getIsDefault()).isTrue();
    }

    @Test
    void updateAddress_storesCanonicalUnitNames_whenRequestUsesFreeTextProvince() {
        AddShippingAddressRequest request = new AddShippingAddressRequest(
                "Tạ Thị Ngọc Anh", "0912345679", "12 Nguyễn Trãi, Q.1, TP.HCM",
                "P. Bến Thành", "TP.HCM", true);
        UUID addressId = UUID.randomUUID();

        ShippingAddress existing = ShippingAddress.builder()
                .id(addressId)
                .member(member)
                .recipientName("Tên cũ")
                .phone("0900000000")
                .addressDetail("Địa chỉ cũ")
                .wardName("Phường 5")
                .provinceName("TP. Hồ Chí Minh")
                .isDefault(true)
                .build();

        when(addressUnitService.resolveNewUnit("TP.HCM", "P. Bến Thành"))
                .thenReturn(new ResolvedAddressUnit(1000001, "Hồ Chí Minh", "Phường Bến Thành"));
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(shippingAddressRepository.findById(addressId)).thenReturn(Optional.of(existing));

        ShippingAddressResponse response = shippingAddressService.updateAddress(email, addressId, request);

        assertThat(response.getWardName()).isEqualTo("Phường Bến Thành");
        assertThat(response.getProvinceName()).isEqualTo("Hồ Chí Minh");
        assertThat(existing.getWardName()).isEqualTo("Phường Bến Thành");
        assertThat(existing.getProvinceName()).isEqualTo("Hồ Chí Minh");
    }

    @Test
    void updateAddress_throwsBadRequest_whenUnitIsNotInGhnCatalog() {
        AddShippingAddressRequest request = new AddShippingAddressRequest(
                "Tạ Thị Ngọc Anh", "0912345679", "12 Nguyễn Trãi, Q.1, TP.HCM",
                "Phường Bến Thành", "Tỉnh Không Tồn Tại", true);
        UUID addressId = UUID.randomUUID();

        when(addressUnitService.resolveNewUnit("Tỉnh Không Tồn Tại", "Phường Bến Thành"))
                .thenThrow(new BadRequestException("provinceName \"Tỉnh Không Tồn Tại\""
                        + " không có trong danh mục tỉnh/thành của GHN."));

        assertThatThrownBy(() -> shippingAddressService.updateAddress(email, addressId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("không có trong danh mục tỉnh/thành");

        verify(shippingAddressRepository, never()).save(any());
    }

    @Test
    void updateAddress_doesNotClearDefault_whenAddressIsAlreadyDefault() {
        AddShippingAddressRequest request = new AddShippingAddressRequest(
                "Tạ Thị Ngọc Anh", "0912345679", "12 Nguyễn Trãi, Q.1, TP.HCM",
                "Phường Bến Thành", "TP. Hồ Chí Minh", true);
        UUID addressId = UUID.randomUUID();

        ShippingAddress existing = ShippingAddress.builder()
                .id(addressId)
                .member(member)
                .recipientName("Tên cũ")
                .phone("0900000000")
                .addressDetail("Địa chỉ cũ")
                .isDefault(true)
                .build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(shippingAddressRepository.findById(addressId)).thenReturn(Optional.of(existing));

        shippingAddressService.updateAddress(email, addressId, request);

        verify(shippingAddressRepository, never()).clearDefaultForMember(any());
    }

    @Test
    void updateAddress_doesNotClearDefault_whenIsDefaultIsFalseOrNull() {
        AddShippingAddressRequest request = new AddShippingAddressRequest(
                "Nguyễn Văn A", "0987654321", "45 Lê Lợi, Q.3, TP.HCM",
                "Phường 5", "TP. Hồ Chí Minh", null);
        UUID addressId = UUID.randomUUID();

        ShippingAddress existing = ShippingAddress.builder()
                .id(addressId)
                .member(member)
                .recipientName("Tên cũ")
                .phone("0900000000")
                .addressDetail("Địa chỉ cũ")
                .isDefault(false)
                .build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(shippingAddressRepository.findById(addressId)).thenReturn(Optional.of(existing));

        ShippingAddressResponse response = shippingAddressService.updateAddress(email, addressId, request);

        assertThat(response.getPhone()).isEqualTo("0987654321");
        verify(shippingAddressRepository, never()).clearDefaultForMember(any());
    }

    @Test
    void deleteAddress_throwsNotFound_whenAccountDoesNotExist() {
        UUID addressId = UUID.randomUUID();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shippingAddressService.deleteAddress(email, addressId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account không tồn tại");

        verify(shippingAddressRepository, never()).delete(any());
    }

    @Test
    void deleteAddress_throwsNotFound_whenAddressDoesNotExist() {
        UUID addressId = UUID.randomUUID();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(shippingAddressRepository.findById(addressId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shippingAddressService.deleteAddress(email, addressId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy địa chỉ.");

        verify(shippingAddressRepository, never()).delete(any());
    }

    @Test
    void deleteAddress_throwsNotFound_whenAddressBelongsToAnotherMember() {
        UUID addressId = UUID.randomUUID();

        Account anotherMember = Account.builder().id(UUID.randomUUID()).email("other@example.com").build();
        ShippingAddress existing = ShippingAddress.builder()
                .id(addressId)
                .member(anotherMember)
                .recipientName("Nguyễn Văn A")
                .phone("0987654321")
                .addressDetail("45 Lê Lợi, Q.3, TP.HCM")
                .isDefault(false)
                .build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(shippingAddressRepository.findById(addressId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> shippingAddressService.deleteAddress(email, addressId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy địa chỉ.");

        verify(shippingAddressRepository, never()).delete(any());
    }

    @Test
    void deleteAddress_deletesAddress_onSuccess() {
        UUID addressId = UUID.randomUUID();

        ShippingAddress existing = ShippingAddress.builder()
                .id(addressId)
                .member(member)
                .recipientName("Tạ Thị Ngọc Anh")
                .phone("0912345678")
                .addressDetail("12 Nguyễn Trãi, Q.1, TP.HCM")
                .isDefault(false)
                .build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(shippingAddressRepository.findById(addressId)).thenReturn(Optional.of(existing));

        shippingAddressService.deleteAddress(email, addressId);

        verify(shippingAddressRepository).delete(existing);
    }

    @Test
    void deleteAddress_throwsConflict_whenAddressIsUsedByAnOrder() {
        UUID addressId = UUID.randomUUID();

        ShippingAddress existing = ShippingAddress.builder()
                .id(addressId)
                .member(member)
                .recipientName("Tạ Thị Ngọc Anh")
                .phone("0912345678")
                .addressDetail("12 Nguyễn Trãi, Q.1, TP.HCM")
                .isDefault(false)
                .build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(shippingAddressRepository.findById(addressId)).thenReturn(Optional.of(existing));
        when(orderRepository.existsByShippingAddress_Id(addressId)).thenReturn(true);

        assertThatThrownBy(() -> shippingAddressService.deleteAddress(email, addressId))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Địa chỉ đã được dùng cho đơn hàng, không thể xóa.");

        verify(shippingAddressRepository, never()).delete(any());
    }

    @Test
    void deleteAddress_throwsConflict_whenDefaultAndOtherAddressesExist() {
        UUID addressId = UUID.randomUUID();

        ShippingAddress existing = ShippingAddress.builder()
                .id(addressId)
                .member(member)
                .recipientName("Tạ Thị Ngọc Anh")
                .phone("0912345678")
                .addressDetail("12 Nguyễn Trãi, Q.1, TP.HCM")
                .isDefault(true)
                .build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(shippingAddressRepository.findById(addressId)).thenReturn(Optional.of(existing));
        when(shippingAddressRepository.countByMember_Id(member.getId())).thenReturn(2L);

        assertThatThrownBy(() -> shippingAddressService.deleteAddress(email, addressId))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Không thể xóa địa chỉ mặc định khi còn địa chỉ khác. Vui lòng đặt địa chỉ khác làm mặc định trước.");

        verify(shippingAddressRepository, never()).delete(any());
    }

    @Test
    void deleteAddress_allowsDeletingDefault_whenItIsTheOnlyAddress() {
        UUID addressId = UUID.randomUUID();

        ShippingAddress existing = ShippingAddress.builder()
                .id(addressId)
                .member(member)
                .recipientName("Tạ Thị Ngọc Anh")
                .phone("0912345678")
                .addressDetail("12 Nguyễn Trãi, Q.1, TP.HCM")
                .isDefault(true)
                .build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(shippingAddressRepository.findById(addressId)).thenReturn(Optional.of(existing));
        when(shippingAddressRepository.countByMember_Id(member.getId())).thenReturn(1L);

        shippingAddressService.deleteAddress(email, addressId);

        verify(shippingAddressRepository).delete(existing);
    }
}
