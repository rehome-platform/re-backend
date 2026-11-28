package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.client.GhnShippingFeeCalculator;
import com.rehome.rehome.component.VoucherCalculator;
import com.rehome.rehome.dto.response.ApplicableVouchersResponse;
import com.rehome.rehome.dto.response.MemberVoucherResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.MemberVoucherStatus;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.marketplace.Cart;
import com.rehome.rehome.entity.marketplace.CartItem;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.ShippingAddress;
import com.rehome.rehome.entity.membership.MemberVoucher;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.repository.CartItemRepository;
import com.rehome.rehome.repository.CartRepository;
import com.rehome.rehome.repository.MemberVoucherRepository;
import com.rehome.rehome.repository.OrderRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.repository.ShippingAddressRepository;
import com.rehome.rehome.service.VoucherService;
import com.rehome.rehome.util.ConsignmentCommission;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VoucherServiceImpl implements VoucherService {

    private final MemberVoucherRepository memberVoucherRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ShippingAddressRepository shippingAddressRepository;
    private final GhnShippingFeeCalculator ghnShippingFeeCalculator;
    private final VoucherCalculator voucherCalculator;

    @Override
    public PageResponse<MemberVoucherResponse> getMyVouchers(Account account, MemberVoucherStatus status,
                                                              int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1));
        var result = status == null
                ? memberVoucherRepository.findWallet(account.getId(), pageable)
                : memberVoucherRepository.findByMember_IdAndStatusOrderByExpiresAtAsc(
                        account.getId(), status, pageable);

        Map<UUID, List<String>> orderNosByVoucherId = loadUsedOrderNos(
                result.getContent().stream().map(MemberVoucher::getId).toList());

        Instant now = Instant.now();
        return PageResponse.from(result.map(v -> toResponse(v, orderNosByVoucherId, now)));
    }

    private Map<UUID, List<String>> loadUsedOrderNos(List<UUID> voucherIds) {
        Map<UUID, List<String>> byVoucher = new LinkedHashMap<>();
        if (voucherIds.isEmpty()) {
            return byVoucher;
        }
        for (Object[] row : orderRepository.findVoucherIdAndOrderNoByVoucherIds(voucherIds)) {
            byVoucher.computeIfAbsent((UUID) row[0], k -> new ArrayList<>()).add((String) row[1]);
        }
        return byVoucher;
    }

    private MemberVoucherResponse toResponse(MemberVoucher v, Map<UUID, List<String>> orderNos, Instant now) {
        return MemberVoucherResponse.builder()
                .id(v.getId().toString())
                .code(v.getCode())
                .name(v.getVoucherTemplate().getName())
                .description(v.getVoucherTemplate().getDescription())
                .type(v.getType())
                .discountKind(v.getDiscountKind())
                .discountValue(v.getDiscountValue())
                .maxDiscountAmount(v.getMaxDiscountAmount())
                .minOrderAmount(v.getMinOrderAmount())
                .status(v.getStatus())
                .issuedAt(v.getIssuedAt())
                .expiresAt(v.getExpiresAt())
                .daysRemaining(daysRemaining(v.getExpiresAt(), now))
                .usedAt(v.getUsedAt())
                .usedOrderNos(orderNos.getOrDefault(v.getId(), List.of()))
                .build();
    }

    private static int daysRemaining(Instant expiresAt, Instant now) {
        if (expiresAt == null || !expiresAt.isAfter(now)) {
            return 0;
        }
        long hours = Duration.between(now, expiresAt).toHours();
        return (int) ((hours + 23) / 24);
    }

    @Override
    public ApplicableVouchersResponse getApplicableVouchers(Account account, List<String> productIds,
                                                             UUID shippingAddressId) {
        if (shippingAddressId == null) {
            throw new BadRequestException("shippingAddressId là bắt buộc.");
        }
        ShippingAddress address = shippingAddressRepository.findById(shippingAddressId)
                .filter(a -> a.getMember().getId().equals(account.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy địa chỉ."));

        List<VoucherCalculator.OrderLine> lines = buildLines(account, productIds, address);
        BigDecimal subtotal = lines.stream()
                .map(VoucherCalculator.OrderLine::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal shippingFee = lines.stream()
                .map(VoucherCalculator.OrderLine::shippingFee)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Instant now = Instant.now();
        List<ApplicableVouchersResponse.Item> items = new ArrayList<>();

        for (MemberVoucher v : memberVoucherRepository.findUsable(account.getId(), now)) {
            String reason = voucherCalculator.describeIneligibility(v, subtotal, now);
            BigDecimal discount = BigDecimal.ZERO;

            if (reason == null) {
                discount = voucherCalculator.calculate(v, lines, false).totalDiscount();
                if (discount.signum() <= 0) {
                    reason = "Mã không giảm được cho đơn này";
                }
            }

            items.add(ApplicableVouchersResponse.Item.builder()
                    .id(v.getId().toString())
                    .code(v.getCode())
                    .name(v.getVoucherTemplate().getName())
                    .description(v.getVoucherTemplate().getDescription())
                    .type(v.getType())
                    .minOrderAmount(v.getMinOrderAmount())
                    .expiresAt(v.getExpiresAt())
                    .applicable(reason == null)
                    .discountAmount(reason == null ? discount : BigDecimal.ZERO)
                    .reason(reason)
                    .build());
        }

        items.sort((a, b) -> {
            if (a.isApplicable() != b.isApplicable()) {
                return a.isApplicable() ? -1 : 1;
            }
            return b.getDiscountAmount().compareTo(a.getDiscountAmount());
        });

        return ApplicableVouchersResponse.builder()
                .subtotal(subtotal)
                .shippingFee(shippingFee)
                .vouchers(items)
                .build();
    }

    private List<VoucherCalculator.OrderLine> buildLines(Account account, List<String> productIds,
                                                          ShippingAddress address) {
        List<String> ids = new ArrayList<>();
        if (productIds != null && !productIds.isEmpty()) {
            ids.addAll(productIds);
        } else {
            Cart cart = cartRepository.findByMember_Id(account.getId())
                    .orElseThrow(() -> new BadRequestException("Giỏ hàng trống."));
            for (CartItem ci : cartItemRepository.findAllByCartIdWithProduct(cart.getId())) {
                ids.add(ci.getProduct().getId());
            }
        }
        if (ids.isEmpty()) {
            throw new BadRequestException("Giỏ hàng trống.");
        }

        List<VoucherCalculator.OrderLine> lines = new ArrayList<>(ids.size());
        for (Product product : productRepository.findAllById(ids)) {
            if (product.getStatus() != ProductStatus.ON_SALE) {
                continue;
            }
            if (product.getMember().getId().equals(account.getId())) {
                continue;
            }
            BigDecimal shippingFee = ghnShippingFeeCalculator.calculate(
                    product.getWarehouse(), address.getWardName(), address.getProvinceName());
            lines.add(new VoucherCalculator.OrderLine(
                    product.getId(),
                    product.getCurrentPrice(),
                    shippingFee,
                    product.getCurrentPrice(),
                    ConsignmentCommission.rateForTerm(
                            product.getConsignmentReceipt(), product.getCurrentTerm())));
        }
        if (lines.isEmpty()) {
            throw new BadRequestException("Không còn món nào đặt được trong danh sách này.");
        }
        return lines;
    }
}
