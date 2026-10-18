package com.rehome.rehome.ServiceImpl;

import com.rehome.rehome.dto.request.AddShippingAddressRequest;
import com.rehome.rehome.dto.response.CreateShippingAddressResponse;
import com.rehome.rehome.dto.response.ShippingAddressResponse;
import com.rehome.rehome.dto.response.UpdateShippingAddressResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.marketplace.ShippingAddress;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.ShippingAddressRepository;
import com.rehome.rehome.service.ShippingAddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

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

    @Override
    @Transactional
    public CreateShippingAddressResponse createAddress(String email, AddShippingAddressRequest request) {

        if (!StringUtils.hasText(request.getRecipientName())) {
            throw new BadRequestException("recipientName là bắt buộc.");
        }
        if (!StringUtils.hasText(request.getPhone())) {
            throw new BadRequestException("phone là bắt buộc.");
        }
        if (!StringUtils.hasText(request.getAddressDetail())) {
            throw new BadRequestException("addressDetail là bắt buộc.");
        }

        Account member = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        boolean isDefault = Boolean.TRUE.equals(request.getIsDefault());
        if (isDefault) {
            shippingAddressRepository.clearDefaultForMember(member.getId());
        }

        ShippingAddress address = ShippingAddress.builder()
                .member(member)
                .recipientName(request.getRecipientName())
                .phone(request.getPhone())
                .addressDetail(request.getAddressDetail())
                .isDefault(isDefault)
                .build();
        shippingAddressRepository.save(address);

        return CreateShippingAddressResponse.builder()
                .id(address.getId().toString())
                .recipientName(address.getRecipientName())
                .isDefault(address.getIsDefault())
                .build();
    }

    @Override
    @Transactional
    public UpdateShippingAddressResponse updateAddress(String email, UUID id, AddShippingAddressRequest request) {

        if (!StringUtils.hasText(request.getRecipientName())) {
            throw new BadRequestException("recipientName là bắt buộc.");
        }
        if (!StringUtils.hasText(request.getPhone())) {
            throw new BadRequestException("phone là bắt buộc.");
        }
        if (!StringUtils.hasText(request.getAddressDetail())) {
            throw new BadRequestException("addressDetail là bắt buộc.");
        }

        Account member = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        ShippingAddress address = shippingAddressRepository.findById(id)
                .filter(a -> a.getMember().getId().equals(member.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy địa chỉ."));

        boolean isDefault = Boolean.TRUE.equals(request.getIsDefault());
        if (isDefault && !Boolean.TRUE.equals(address.getIsDefault())) {
            shippingAddressRepository.clearDefaultForMember(member.getId());
        }

        address.setRecipientName(request.getRecipientName());
        address.setPhone(request.getPhone());
        address.setAddressDetail(request.getAddressDetail());
        address.setIsDefault(isDefault);
        shippingAddressRepository.save(address);

        return UpdateShippingAddressResponse.builder()
                .id(address.getId().toString())
                .phone(address.getPhone())
                .build();
    }
}
