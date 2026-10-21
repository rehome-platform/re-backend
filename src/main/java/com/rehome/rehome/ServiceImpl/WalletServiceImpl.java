package com.rehome.rehome.ServiceImpl;

import com.rehome.rehome.dto.response.OrderRevenueResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.WalletTransactionResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.WalletTransactionType;
import com.rehome.rehome.entity.enums.WalletType;
import com.rehome.rehome.entity.marketplace.OrderItem;
import com.rehome.rehome.entity.marketplace.Wallet;
import com.rehome.rehome.entity.marketplace.WalletTransaction;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ForbiddenException;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.WalletRepository;
import com.rehome.rehome.repository.WalletTransactionRepository;
import com.rehome.rehome.repository.spec.WalletTransactionSpecifications;
import com.rehome.rehome.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WalletServiceImpl implements WalletService {

    private final AccountRepository accountRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    @Override
    public PageResponse<WalletTransactionResponse> getTransactions(
            String email, String type, String direction, int page, int size) {

        Account member = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        List<WalletTransactionType> types = parseTypes(type);
        validateDirection(direction);

        Wallet wallet = walletRepository.findByMember_Id(member.getId()).orElse(null);
        if (wallet == null) {
            return PageResponse.from(Page.empty());
        }

        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<WalletTransaction> result = walletTransactionRepository.findAll(
                WalletTransactionSpecifications.filter(wallet.getId(), types, direction), pageable);

        return PageResponse.from(result.map(this::toResponse));
    }

    @Override
    public WalletTransactionResponse getTransactionDetail(String email, UUID transactionId) {

        Account member = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        Wallet wallet = walletRepository.findByMember_Id(member.getId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy giao dịch."));

        WalletTransaction transaction = walletTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy giao dịch."));

        if (transaction.getWalletType() != WalletType.MEMBER || !transaction.getWalletId().equals(wallet.getId())) {
            throw new ForbiddenException("Bạn không có quyền xem giao dịch này.");
        }

        return toResponse(transaction);
    }

    @Override
    public PageResponse<OrderRevenueResponse> getRevenue(String email, int page, int size) {

        Account member = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        Wallet wallet = walletRepository.findByMember_Id(member.getId()).orElse(null);
        if (wallet == null) {
            return PageResponse.from(Page.empty());
        }

        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<WalletTransaction> result = walletTransactionRepository.findAll(
                WalletTransactionSpecifications.filter(wallet.getId(), List.of(WalletTransactionType.ORDER_PAYOUT), null),
                pageable);

        return PageResponse.from(result.map(this::toRevenueResponse));
    }

    private List<WalletTransactionType> parseTypes(String type) {
        if (!StringUtils.hasText(type)) {
            return List.of();
        }

        List<WalletTransactionType> types = new ArrayList<>();
        for (String token : type.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                types.add(WalletTransactionType.valueOf(trimmed.toUpperCase()));
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("type không hợp lệ: " + trimmed);
            }
        }
        return types;
    }

    private void validateDirection(String direction) {
        if (StringUtils.hasText(direction)
                && !"IN".equalsIgnoreCase(direction) && !"OUT".equalsIgnoreCase(direction)) {
            throw new BadRequestException("direction phải là IN hoặc OUT.");
        }
    }

    private WalletTransactionResponse toResponse(WalletTransaction tx) {
        return WalletTransactionResponse.builder()
                .id(tx.getId().toString())
                .transactionNo(tx.getTransactionNo())
                .type(tx.getType().name())
                .amount(tx.getAmount())
                .balanceAfter(tx.getBalanceAfter())
                .relatedOrderId(tx.getRelatedOrder() != null ? tx.getRelatedOrder().getId().toString() : null)
                .relatedOrderNo(tx.getRelatedOrder() != null ? tx.getRelatedOrder().getOrderNo() : null)
                .itemName(tx.getRelatedOrderItem() != null ? tx.getRelatedOrderItem().getProduct().getName() : null)
                .status(tx.getStatus().name())
                .note(tx.getNote())
                .createdAt(tx.getCreatedAt())
                .build();
    }

    private OrderRevenueResponse toRevenueResponse(WalletTransaction tx) {
        OrderItem item = tx.getRelatedOrderItem();
        BigDecimal salePrice = item != null ? item.getPriceAtOrder() : null;
        BigDecimal commission = item != null
                ? item.getPriceAtOrder().multiply(item.getCommissionRateApplied())
                : null;
        BigDecimal tax = item != null
                ? item.getPriceAtOrder().multiply(item.getTaxRateApplied())
                : null;

        return OrderRevenueResponse.builder()
                .orderId(tx.getRelatedOrder() != null ? tx.getRelatedOrder().getId().toString() : null)
                .orderNo(tx.getRelatedOrder() != null ? tx.getRelatedOrder().getOrderNo() : null)
                .salePrice(salePrice)
                .commission(commission)
                .tax(tax)
                .netAmount(tx.getAmount())
                .completedAt(tx.getRelatedOrder() != null ? tx.getRelatedOrder().getCompletedAt() : null)
                .build();
    }
}
