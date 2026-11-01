package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.request.DecideDegradedItemCaseRequest;
import com.rehome.rehome.dto.response.DegradedItemCaseDetailResponse;
import com.rehome.rehome.dto.response.DegradedItemCaseResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.DegradedItemCaseStatus;
import com.rehome.rehome.entity.enums.DegradedItemChoice;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.TransactionStatus;
import com.rehome.rehome.entity.enums.WalletTransactionType;
import com.rehome.rehome.entity.enums.WalletType;
import com.rehome.rehome.entity.marketplace.DegradedItemCase;
import com.rehome.rehome.entity.marketplace.PlatformFund;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.ReturnRequest;
import com.rehome.rehome.entity.marketplace.ReturnRequestPhoto;
import com.rehome.rehome.entity.marketplace.Wallet;
import com.rehome.rehome.entity.marketplace.WalletTransaction;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.DegradedItemCaseRepository;
import com.rehome.rehome.repository.PlatformFundRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.repository.ReturnRequestPhotoRepository;
import com.rehome.rehome.repository.WalletRepository;
import com.rehome.rehome.repository.WalletTransactionRepository;
import com.rehome.rehome.service.DegradedItemCaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DegradedItemCaseServiceImpl implements DegradedItemCaseService {

    private final DegradedItemCaseRepository degradedItemCaseRepository;
    private final ProductRepository productRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final PlatformFundRepository platformFundRepository;
    private final ReturnRequestPhotoRepository returnRequestPhotoRepository;

    // API 95 (F-036): FULL_COMPENSATION -> đền đủ compensationAmount, hàng thành tài sản nền tảng;
    // PARTIAL_WITH_ITEM_BACK -> đền compensationAmount x degradedCompensationRate (snapshot biên
    // nhận), Member đến kho lấy hàng về (không mất phí rút). Cả hai đều cộng ví ngay (F-162).
    @Override
    @Transactional
    public DegradedItemCaseResponse decide(Account account, UUID id, DecideDegradedItemCaseRequest request) {

        if (request == null || !StringUtils.hasText(request.getChoice())) {
            throw new BadRequestException("choice là bắt buộc.");
        }

        DegradedItemChoice choice = parseChoice(request.getChoice());

        DegradedItemCase degradedCase = degradedItemCaseRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.DEGRADED_ITEM_CASE_NOT_FOUND));

        if (!degradedCase.getProduct().getMember().getId().equals(account.getId())) {
            throw new AppException(ErrorCode.DEGRADED_ITEM_CASE_FORBIDDEN);
        }

        if (degradedCase.getChoice() != null) {
            throw new AppException(ErrorCode.DEGRADED_ITEM_CASE_ALREADY_DECIDED);
        }

        Product product = productRepository.findByIdForUpdate(degradedCase.getProduct().getId())
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        BigDecimal paidAmount;
        if (choice == DegradedItemChoice.FULL_COMPENSATION) {
            paidAmount = degradedCase.getCompensationAmount();
            product.setStatus(ProductStatus.PLATFORM_OWNED);
        } else {
            BigDecimal rate = product.getConsignmentReceipt().getDegradedCompensationRate();
            paidAmount = degradedCase.getCompensationAmount()
                    .multiply(rate)
                    .setScale(2, RoundingMode.HALF_UP);
            // Member sẽ đến kho lấy hàng (không mất phí rút) - hàng ngừng bán ngay, giống F-032
            product.setStatus(ProductStatus.WITHDRAWN);
        }
        productRepository.save(product);

        degradedCase.setChoice(choice);
        degradedCase.setPaidAmount(paidAmount);
        degradedCase.setDecidedAt(Instant.now());
        degradedItemCaseRepository.save(degradedCase);

        creditSellerWallet(degradedCase, account, paidAmount);

        return toResponse(degradedCase);
    }

    private DegradedItemChoice parseChoice(String value) {
        try {
            return DegradedItemChoice.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("choice phải là FULL_COMPENSATION hoặc PARTIAL_WITH_ITEM_BACK.");
        }
    }

    // F-162: cộng tiền đền vào ví người bán như một bút toán, không phải chuyển tiền thật
    private void creditSellerWallet(DegradedItemCase degradedCase, Account seller, BigDecimal amount) {
        Wallet wallet = getOrCreateWallet(seller);
        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);

        WalletTransaction compensationTransaction = WalletTransaction.builder()
                .transactionNo(generateTransactionNo())
                .walletType(WalletType.MEMBER)
                .walletId(wallet.getId())
                .type(WalletTransactionType.DEGRADED_COMPENSATION)
                .amount(amount)
                .balanceAfter(wallet.getBalance())
                .relatedDegradedItemCase(degradedCase)
                .status(TransactionStatus.SUCCESS)
                .note("Đền bù hàng xuống cấp - sản phẩm " + degradedCase.getProduct().getId())
                .build();
        walletTransactionRepository.save(compensationTransaction);
        recordPlatformCounterpart(compensationTransaction);
    }

    private Wallet getOrCreateWallet(Account member) {
        return walletRepository.findByMember_Id(member.getId())
                .orElseGet(() -> Wallet.builder().member(member).build());
    }

    private String generateTransactionNo() {
        String prefix = "WT-" + Year.now(ZoneOffset.UTC).getValue() + "-";
        long seq = walletTransactionRepository.countByTransactionNoStartingWith(prefix) + 1;
        return prefix + String.format("%06d", seq);
    }

    private PlatformFund getOrCreatePlatformFund() {
        return platformFundRepository.findTopByOrderByIdAsc()
                .orElseGet(() -> PlatformFund.builder().build());
    }

    // F-039: mỗi bút toán ví Member phải có một bút toán đối ứng ở quỹ nền tảng, ngược dấu
    private void recordPlatformCounterpart(WalletTransaction memberTransaction) {
        PlatformFund fund = getOrCreatePlatformFund();
        BigDecimal counterpartAmount = memberTransaction.getAmount().negate();
        fund.setBalance(fund.getBalance().add(counterpartAmount));
        platformFundRepository.save(fund);

        WalletTransaction platformTransaction = WalletTransaction.builder()
                .transactionNo(generateTransactionNo())
                .walletType(WalletType.PLATFORM)
                .walletId(fund.getId())
                .type(memberTransaction.getType())
                .amount(counterpartAmount)
                .balanceAfter(fund.getBalance())
                .relatedDegradedItemCase(memberTransaction.getRelatedDegradedItemCase())
                .counterpartTransaction(memberTransaction)
                .status(TransactionStatus.SUCCESS)
                .note(memberTransaction.getNote())
                .build();
        walletTransactionRepository.save(platformTransaction);
    }

    private DegradedItemCaseResponse toResponse(DegradedItemCase degradedCase) {
        return DegradedItemCaseResponse.builder()
                .id(degradedCase.getId().toString())
                .productId(degradedCase.getProduct().getId())
                .choice(degradedCase.getChoice().name())
                .compensationAmount(degradedCase.getCompensationAmount())
                .paidAmount(degradedCase.getPaidAmount())
                .decidedAt(degradedCase.getDecidedAt())
                .build();
    }

    // ---------------------------------------------------------------- Member xem ca của mình

    @Override
    public DegradedItemCaseDetailResponse getMine(Account account, UUID id) {
        DegradedItemCase degradedCase = degradedItemCaseRepository.findDetailById(id)
                .orElseThrow(() -> new AppException(ErrorCode.DEGRADED_ITEM_CASE_NOT_FOUND));

        if (!degradedCase.getProduct().getMember().getId().equals(account.getId())) {
            throw new AppException(ErrorCode.DEGRADED_ITEM_CASE_FORBIDDEN);
        }
        return toDetailResponse(degradedCase);
    }

    @Override
    public List<DegradedItemCaseDetailResponse> listMine(Account account, String status) {
        DegradedItemCaseStatus parsed = StringUtils.hasText(status) ? parseStatus(status) : null;

        return degradedItemCaseRepository.findDetailsByMember(
                        account.getId(),
                        parsed == DegradedItemCaseStatus.PENDING_DECISION,
                        parsed == DegradedItemCaseStatus.DECIDED).stream()
                .map(this::toDetailResponse)
                .toList();
    }

    private DegradedItemCaseStatus parseStatus(String status) {
        try {
            return DegradedItemCaseStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("status phải là PENDING_DECISION hoặc DECIDED.");
        }
    }

    private DegradedItemCaseDetailResponse toDetailResponse(DegradedItemCase c) {
        ReturnRequest returnRequest = c.getReturnRequest();

        // Ảnh và lý do nằm ở yêu cầu trả hàng dẫn đến ca này; ca mở không qua trả hàng thì không có
        List<String> photos = returnRequest == null ? List.of()
                : returnRequestPhotoRepository
                        .findAllByReturnRequest_IdOrderByIdAsc(returnRequest.getId()).stream()
                        .map(ReturnRequestPhoto::getImageUrl)
                        .toList();

        return DegradedItemCaseDetailResponse.builder()
                .id(c.getId().toString())
                .productId(c.getProduct().getId())
                .productName(c.getProduct().getName())
                .photos(photos)
                .reason(returnRequest == null ? null : returnRequest.getReason())
                .moderatorNote(returnRequest == null ? null : returnRequest.getConditionNote())
                .compensationAmount(c.getCompensationAmount())
                // Chưa có hạn quyết định trong hệ thống - xem javadoc của DTO
                .decisionDeadline(null)
                .status(c.getChoice() == null
                        ? DegradedItemCaseStatus.PENDING_DECISION.name()
                        : DegradedItemCaseStatus.DECIDED.name())
                .decision(c.getChoice() == null ? null : c.getChoice().name())
                .build();
    }
}
