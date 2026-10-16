package com.rehome.rehome.ServiceImpl;

import com.rehome.rehome.dto.response.ShippingAddressResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.marketplace.ShippingAddress;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.ShippingAddressRepository;
import com.rehome.rehome.service.ShippingAddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShippingAddressServiceImpl implements ShippingAddressService {

    private final AccountRepository accountRepository;
    private final ShippingAddressRepository shippingAddressRepository;

    @Override
    public List<ShippingAddressResponse> getAddresses(String email) {

        Account member = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        List<ShippingAddress> addresses = shippingAddressRepository
                .findByMember_IdOrderByIsDefaultDesc(member.getId());

        return addresses.stream()
                .map(address -> ShippingAddressResponse.builder()
                        .id(address.getId().toString())
                        .recipientName(address.getRecipientName())
                        .phone(address.getPhone())
                        .addressDetail(address.getAddressDetail())
                        .isDefault(address.getIsDefault())
                        .build())
                .toList();
    }
}
