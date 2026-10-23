package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.request.AddShippingAddressRequest;
import com.rehome.rehome.dto.response.ShippingAddressResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.marketplace.ShippingAddress;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ConflictException;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.OrderRepository;
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

    private static final int MAX_ADDRESSES_PER_MEMBER = 5;

    private final AccountRepository accountRepository;
    private final ShippingAddressRepository shippingAddressRepository;
    private final OrderRepository orderRepository;

    @Override
    public List<ShippingAddressResponse> getAddresses(String email) {

        Account member = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        List<ShippingAddress> addresses = shippingAddressRepository
                .findByMember_IdOrderByIsDefaultDesc(member.getId());

        return addresses.stream()
                .map(ShippingAddressServiceImpl::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ShippingAddressResponse createAddress(String email, AddShippingAddressRequest request) {

        if (!StringUtils.hasText(request.getRecipientName())) {
            throw new BadRequestException("recipientName là bắt buộc.");
        }
        if (!StringUtils.hasText(request.getPhone())) {
            throw new BadRequestException("phone là bắt buộc.");
        }
        if (!StringUtils.hasText(request.getAddressDetail())) {
            throw new BadRequestException("addressDetail là bắt buộc.");
        }
        validateAddressUnit(request);

        Account member = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        long existingCount = shippingAddressRepository.countByMember_Id(member.getId());
        if (existingCount >= MAX_ADDRESSES_PER_MEMBER) {
            throw new ConflictException("Đã đạt số lượng tối đa " + MAX_ADDRESSES_PER_MEMBER + " địa chỉ.",
                    "MAX_ADDRESSES_REACHED");
        }

        // Địa chỉ đầu tiên của member luôn là mặc định, không phụ thuộc request
        boolean isDefault = existingCount == 0 || Boolean.TRUE.equals(request.getIsDefault());
        if (isDefault) {
            shippingAddressRepository.clearDefaultForMember(member.getId());
        }

        ShippingAddress address = ShippingAddress.builder()
                .member(member)
                .recipientName(request.getRecipientName())
                .phone(request.getPhone())
                .addressDetail(request.getAddressDetail())
                .wardName(request.getWardName())
                .districtName(request.getDistrictName())
                .provinceName(request.getProvinceName())
                .isNewAddress(Boolean.TRUE.equals(request.getIsNewAddress()))
                .isDefault(isDefault)
                .build();
        shippingAddressRepository.save(address);

        return toResponse(address);
    }

    @Override
    @Transactional
    public ShippingAddressResponse updateAddress(String email, UUID id, AddShippingAddressRequest request) {

        if (!StringUtils.hasText(request.getRecipientName())) {
            throw new BadRequestException("recipientName là bắt buộc.");
        }
        if (!StringUtils.hasText(request.getPhone())) {
            throw new BadRequestException("phone là bắt buộc.");
        }
        if (!StringUtils.hasText(request.getAddressDetail())) {
            throw new BadRequestException("addressDetail là bắt buộc.");
        }
        validateAddressUnit(request);

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
        address.setWardName(request.getWardName());
        address.setDistrictName(request.getDistrictName());
        address.setProvinceName(request.getProvinceName());
        address.setIsNewAddress(Boolean.TRUE.equals(request.getIsNewAddress()));
        address.setIsDefault(isDefault);
        shippingAddressRepository.save(address);

        return toResponse(address);
    }

    // Phường/xã + tỉnh/thành luôn bắt buộc; quận/huyện chỉ bắt buộc khi còn dùng đơn vị hành chính cũ
    // (is_new_to_address = false của GHN) — xem docs/ghnapi.md mục "Tạo đơn"
    private void validateAddressUnit(AddShippingAddressRequest request) {
        if (!StringUtils.hasText(request.getWardName())) {
            throw new BadRequestException("wardName là bắt buộc.");
        }
        if (!StringUtils.hasText(request.getProvinceName())) {
            throw new BadRequestException("provinceName là bắt buộc.");
        }
        if (!Boolean.TRUE.equals(request.getIsNewAddress()) && !StringUtils.hasText(request.getDistrictName())) {
            throw new BadRequestException("districtName là bắt buộc khi không dùng địa chỉ hành chính mới.");
        }
    }

    @Override
    @Transactional
    public void deleteAddress(String email, UUID id) {

        Account member = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        ShippingAddress address = shippingAddressRepository.findById(id)
                .filter(a -> a.getMember().getId().equals(member.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy địa chỉ."));

        if (orderRepository.existsByShippingAddress_Id(id)) {
            throw new ConflictException(
                    "Địa chỉ đã được dùng cho đơn hàng, không thể xóa.",
                    "ADDRESS_IN_USE");
        }

        if (Boolean.TRUE.equals(address.getIsDefault())
                && shippingAddressRepository.countByMember_Id(member.getId()) > 1) {
            throw new ConflictException(
                    "Không thể xóa địa chỉ mặc định khi còn địa chỉ khác. Vui lòng đặt địa chỉ khác làm mặc định trước.",
                    "DEFAULT_ADDRESS_DELETE_BLOCKED");
        }

        shippingAddressRepository.delete(address);
    }

    private static ShippingAddressResponse toResponse(ShippingAddress address) {
        return ShippingAddressResponse.builder()
                .id(address.getId().toString())
                .recipientName(address.getRecipientName())
                .phone(address.getPhone())
                .addressDetail(address.getAddressDetail())
                .wardName(address.getWardName())
                .districtName(address.getDistrictName())
                .provinceName(address.getProvinceName())
                .isNewAddress(address.getIsNewAddress())
                .isDefault(address.getIsDefault())
                .build();
    }
}
