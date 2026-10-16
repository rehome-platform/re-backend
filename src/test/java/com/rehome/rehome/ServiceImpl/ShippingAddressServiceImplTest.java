package com.rehome.rehome.ServiceImpl;

import com.rehome.rehome.dto.response.ShippingAddressResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.marketplace.ShippingAddress;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.ShippingAddressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShippingAddressServiceImplTest {

    @Mock AccountRepository accountRepository;
    @Mock ShippingAddressRepository shippingAddressRepository;

    @InjectMocks
    ShippingAddressServiceImpl shippingAddressService;

    Account member;
    String email;

    @BeforeEach
    void setup() {
        email = "member@example.com";
        member = Account.builder().id(UUID.randomUUID()).email(email).build();
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
}
