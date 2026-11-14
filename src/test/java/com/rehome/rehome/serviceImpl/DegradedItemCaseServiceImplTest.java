package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.request.DecideDegradedItemCaseRequest;
import com.rehome.rehome.dto.request.OpenDegradedItemCaseRequest;
import com.rehome.rehome.dto.response.DegradedItemCaseDetailResponse;
import com.rehome.rehome.dto.response.DegradedItemCaseResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.DegradedItemChoice;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.DegradedItemCase;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.ReturnRequest;
import com.rehome.rehome.entity.marketplace.ReturnRequestPhoto;
import com.rehome.rehome.entity.marketplace.Wallet;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ConflictException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.DegradedItemCaseRepository;
import com.rehome.rehome.repository.PlatformFundRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.repository.ReturnRequestPhotoRepository;
import com.rehome.rehome.repository.WalletRepository;
import com.rehome.rehome.repository.WalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DegradedItemCaseServiceImplTest {

    @Mock DegradedItemCaseRepository degradedItemCaseRepository;
    @Mock ReturnRequestPhotoRepository returnRequestPhotoRepository;
    @Mock ProductRepository productRepository;
    @Mock WalletRepository walletRepository;
    @Mock WalletTransactionRepository walletTransactionRepository;
    @Mock PlatformFundRepository platformFundRepository;
    @Mock com.rehome.rehome.service.SystemConfigReader systemConfigReader;

    @Mock private com.rehome.rehome.component.AuditLogger auditLogger;
    @InjectMocks
    DegradedItemCaseServiceImpl service;

    static final String PRODUCT_ID = "Q7-AK-000845";

    Account seller;
    Product product;
    ConsignmentReceipt receipt;
    UUID caseId;

    @BeforeEach
    void setup() {
        seller = Account.builder().id(UUID.randomUUID()).role(Role.MEMBER).build();
        receipt = ConsignmentReceipt.builder().degradedCompensationRate(new BigDecimal("0.50")).build();
        product = Product.builder().id(PRODUCT_ID).member(seller).consignmentReceipt(receipt)
                .status(ProductStatus.RETURNED).build();

        when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(product));

        caseId = UUID.randomUUID();
    }

    private DegradedItemCase degradedCase(BigDecimal compensationAmount, DegradedItemChoice choice) {
        DegradedItemCase c = DegradedItemCase.builder()
                .id(caseId)
                .product(product)
                .moderator(Account.builder().id(UUID.randomUUID()).role(Role.MODERATOR).build())
                .compensationAmount(compensationAmount)
                .choice(choice)
                .build();
        when(degradedItemCaseRepository.findByIdForUpdate(caseId)).thenReturn(Optional.of(c));
        return c;
    }

    private DecideDegradedItemCaseRequest request(String choice) {
        DecideDegradedItemCaseRequest req = new DecideDegradedItemCaseRequest();
        req.setChoice(choice);
        return req;
    }

    // ------------------------------------------------------------------
    // Moderator mở ca trực tiếp lúc kiểm kho (open) - không qua yêu cầu trả hàng
    // ------------------------------------------------------------------

    private Account moderatorWithWarehouse() {
        Warehouse warehouse = Warehouse.builder().id(UUID.randomUUID()).build();
        return Account.builder().id(UUID.randomUUID()).role(Role.MODERATOR).warehouse(warehouse).build();
    }

    private OpenDegradedItemCaseRequest openRequest(String productId, String notes, List<String> photos) {
        return new OpenDegradedItemCaseRequest(productId, notes, photos);
    }

    @Test
    @DisplayName("open: thiếu productId -> BadRequestException")
    void open_missingProductId_throws() {
        assertThatThrownBy(() -> service.open(moderatorWithWarehouse(), openRequest(null, null, null)))
                .isInstanceOf(BadRequestException.class);

        verify(productRepository, never()).findById(any());
    }

    @Test
    @DisplayName("open: Moderator chưa được gán kho -> MODERATOR_NO_WAREHOUSE")
    void open_moderatorNoWarehouse_throws() {
        Account moderatorNoWarehouse = Account.builder().id(UUID.randomUUID()).role(Role.MODERATOR).build();

        AppException ex = org.junit.jupiter.api.Assertions.assertThrows(AppException.class,
                () -> service.open(moderatorNoWarehouse, openRequest(PRODUCT_ID, null, null)));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.MODERATOR_NO_WAREHOUSE);
        verify(productRepository, never()).findById(any());
    }

    @Test
    @DisplayName("open: không tìm thấy sản phẩm -> PRODUCT_NOT_FOUND")
    void open_productNotFound_throws() {
        Account moderator = moderatorWithWarehouse();
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

        AppException ex = org.junit.jupiter.api.Assertions.assertThrows(AppException.class,
                () -> service.open(moderator, openRequest(PRODUCT_ID, null, null)));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("open: sản phẩm thuộc kho khác -> PRODUCT_NOT_IN_YOUR_WAREHOUSE")
    void open_wrongWarehouse_throws() {
        Account moderator = moderatorWithWarehouse();
        Product otherWarehouseProduct = Product.builder().id(PRODUCT_ID)
                .warehouse(Warehouse.builder().id(UUID.randomUUID()).build()).build();
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(otherWarehouseProduct));

        AppException ex = org.junit.jupiter.api.Assertions.assertThrows(AppException.class,
                () -> service.open(moderator, openRequest(PRODUCT_ID, null, null)));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_IN_YOUR_WAREHOUSE);
        verify(degradedItemCaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("open: sản phẩm đang có ca chưa quyết định -> ConflictException, không mở ca mới")
    void open_alreadyOpenCase_throws() {
        Account moderator = moderatorWithWarehouse();
        Product p = Product.builder().id(PRODUCT_ID).warehouse(moderator.getWarehouse()).build();
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(p));
        when(degradedItemCaseRepository.existsByProduct_IdAndChoiceIsNull(PRODUCT_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.open(moderator, openRequest(PRODUCT_ID, null, null)))
                .isInstanceOf(ConflictException.class);

        verify(degradedItemCaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("open: sản phẩm không ON_SALE -> DEGRADED_ITEM_CASE_PRODUCT_NOT_ON_SALE, không mở ca")
    void open_productNotOnSale_throws() {
        Account moderator = moderatorWithWarehouse();
        Product p = Product.builder().id(PRODUCT_ID).warehouse(moderator.getWarehouse())
                .status(ProductStatus.WITHDRAWN).build();
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(p));

        AppException ex = org.junit.jupiter.api.Assertions.assertThrows(AppException.class,
                () -> service.open(moderator, openRequest(PRODUCT_ID, null, null)));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DEGRADED_ITEM_CASE_PRODUCT_NOT_ON_SALE);
        verify(degradedItemCaseRepository, never()).save(any());
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("open: thành công -> snapshot giá đang bán - hoa hồng kỳ hiện tại, lưu notes/photos, "
            + "ẩn sản phẩm khỏi sàn (ON_SALE -> WAREHOUSE)")
    void open_success_computesCompensationFromCurrentPriceAndTerm() {
        Account moderator = moderatorWithWarehouse();
        ConsignmentReceipt r = ConsignmentReceipt.builder()
                .commissionRateTerm1(new BigDecimal("0.10"))
                .commissionRateTerm2(new BigDecimal("0.15"))
                .commissionRateTerm3(new BigDecimal("0.20"))
                .build();
        Product p = Product.builder().id(PRODUCT_ID).warehouse(moderator.getWarehouse())
                .status(ProductStatus.ON_SALE)
                .consignmentReceipt(r).currentPrice(new BigDecimal("300000")).currentTerm((short) 2).build();
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(p));
        when(degradedItemCaseRepository.existsByProduct_IdAndChoiceIsNull(PRODUCT_ID)).thenReturn(false);
        when(systemConfigReader.getInt(
                com.rehome.rehome.constant.SystemConfigKeys.DEGRADED_ITEM_DECISION_DAYS, 30)).thenReturn(30);
        // GenerationType.UUID: Hibernate gán id thật lúc persist(), mock save() giả lập lại việc đó
        when(degradedItemCaseRepository.save(any(DegradedItemCase.class))).thenAnswer(invocation -> {
            DegradedItemCase arg = invocation.getArgument(0);
            arg.setId(UUID.randomUUID());
            return arg;
        });

        Instant before = Instant.now();
        DegradedItemCaseDetailResponse res = service.open(moderator,
                openRequest(PRODUCT_ID, "Rách tay áo", List.of("https://cdn/a.jpg")));
        Instant after = Instant.now();

        ArgumentCaptor<DegradedItemCase> captor = ArgumentCaptor.forClass(DegradedItemCase.class);
        verify(degradedItemCaseRepository).save(captor.capture());
        DegradedItemCase saved = captor.getValue();

        assertThat(saved.getProduct()).isEqualTo(p);
        assertThat(saved.getModerator()).isEqualTo(moderator);
        // 300000 - 300000 * 0.15 (kỳ 2) = 255000
        assertThat(saved.getCompensationAmount()).isEqualByComparingTo("255000");
        assertThat(saved.getNotes()).isEqualTo("Rách tay áo");
        assertThat(saved.getPhotos()).containsExactly("https://cdn/a.jpg");
        assertThat(saved.getChoice()).isNull();
        // Hạn chọn phương án đền bù, mặc định 30 ngày (F-036)
        assertThat(saved.getDecisionDeadline())
                .isAfter(before.plus(29, java.time.temporal.ChronoUnit.DAYS))
                .isBefore(after.plus(31, java.time.temporal.ChronoUnit.DAYS));

        assertThat(res.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(res.getCompensationAmount()).isEqualByComparingTo("255000");
        assertThat(res.getModeratorNote()).isEqualTo("Rách tay áo");
        assertThat(res.getPhotos()).containsExactly("https://cdn/a.jpg");
        assertThat(res.getStatus()).isEqualTo("PENDING_DECISION");

        // Tạm ẩn khỏi sàn chờ Member quyết định - hàng vẫn nằm trong kho, không phải WITHDRAWN
        assertThat(p.getStatus()).isEqualTo(ProductStatus.WAREHOUSE);
        verify(productRepository).save(p);
    }

    @Test
    @DisplayName("thiếu choice -> BadRequestException")
    void decide_missingChoice_throws() {
        assertThatThrownBy(() -> service.decide(seller, caseId, request(null)))
                .isInstanceOf(BadRequestException.class);

        verify(degradedItemCaseRepository, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("choice không hợp lệ -> BadRequestException")
    void decide_invalidChoice_throws() {
        assertThatThrownBy(() -> service.decide(seller, caseId, request("REFUND_ALL")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("choice = UNCLAIMED -> BadRequestException (chỉ job mới được gán, Member không chọn được)")
    void decide_choiceUnclaimed_rejectedAsInvalid() {
        assertThatThrownBy(() -> service.decide(seller, caseId, request("UNCLAIMED")))
                .isInstanceOf(BadRequestException.class);

        verify(degradedItemCaseRepository, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("không tìm thấy ca -> DEGRADED_ITEM_CASE_NOT_FOUND")
    void decide_notFound_throws() {
        when(degradedItemCaseRepository.findByIdForUpdate(caseId)).thenReturn(Optional.empty());

        AppException ex = org.junit.jupiter.api.Assertions.assertThrows(AppException.class,
                () -> service.decide(seller, caseId, request("FULL_COMPENSATION")));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DEGRADED_ITEM_CASE_NOT_FOUND);
    }

    @Test
    @DisplayName("không phải chủ sản phẩm -> DEGRADED_ITEM_CASE_FORBIDDEN")
    void decide_notOwner_throws() {
        degradedCase(new BigDecimal("270000"), null);
        Account other = Account.builder().id(UUID.randomUUID()).role(Role.MEMBER).build();

        AppException ex = org.junit.jupiter.api.Assertions.assertThrows(AppException.class,
                () -> service.decide(other, caseId, request("FULL_COMPENSATION")));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DEGRADED_ITEM_CASE_FORBIDDEN);
        verify(walletRepository, never()).save(any());
    }

    @Test
    @DisplayName("ca đã được quyết định trước đó -> DEGRADED_ITEM_CASE_ALREADY_DECIDED")
    void decide_alreadyDecided_throws() {
        degradedCase(new BigDecimal("270000"), DegradedItemChoice.FULL_COMPENSATION);

        AppException ex = org.junit.jupiter.api.Assertions.assertThrows(AppException.class,
                () -> service.decide(seller, caseId, request("PARTIAL_WITH_ITEM_BACK")));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DEGRADED_ITEM_CASE_ALREADY_DECIDED);
        verify(walletRepository, never()).save(any());
    }

    @Test
    @DisplayName("job đã đánh dấu vô thừa nhận (UNCLAIMED) -> DECISION_EXPIRED, không ALREADY_DECIDED")
    void decide_alreadyUnclaimed_throwsExpired() {
        degradedCase(new BigDecimal("270000"), DegradedItemChoice.UNCLAIMED);

        AppException ex = org.junit.jupiter.api.Assertions.assertThrows(AppException.class,
                () -> service.decide(seller, caseId, request("FULL_COMPENSATION")));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DECISION_EXPIRED);
        verify(walletRepository, never()).save(any());
    }

    @Test
    @DisplayName("quá decisionDeadline nhưng job chưa kịp chạy -> DECISION_EXPIRED")
    void decide_pastDeadline_throwsExpired() {
        DegradedItemCase c = degradedCase(new BigDecimal("270000"), null);
        c.setDecisionDeadline(Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS));

        AppException ex = org.junit.jupiter.api.Assertions.assertThrows(AppException.class,
                () -> service.decide(seller, caseId, request("FULL_COMPENSATION")));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DECISION_EXPIRED);
        verify(walletRepository, never()).save(any());
    }

    @Test
    @DisplayName("FULL_COMPENSATION -> đền đủ, sản phẩm PLATFORM_OWNED, cộng ví người bán")
    void decide_fullCompensation_paysFullAndMarksPlatformOwned() {
        DegradedItemCase c = degradedCase(new BigDecimal("270000"), null);
        Wallet wallet = Wallet.builder().id(UUID.randomUUID()).member(seller).balance(new BigDecimal("100000")).build();
        when(walletRepository.findByMember_Id(seller.getId())).thenReturn(Optional.of(wallet));

        DegradedItemCaseResponse response = service.decide(seller, caseId, request("FULL_COMPENSATION"));

        assertThat(c.getChoice()).isEqualTo(DegradedItemChoice.FULL_COMPENSATION);
        assertThat(c.getPaidAmount()).isEqualByComparingTo(new BigDecimal("270000"));
        assertThat(c.getDecidedAt()).isNotNull();
        verify(degradedItemCaseRepository).save(c);

        assertThat(product.getStatus()).isEqualTo(ProductStatus.PLATFORM_OWNED);
        verify(productRepository).save(product);

        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("370000"));
        verify(walletRepository).save(wallet);

        assertThat(response.getId()).isEqualTo(caseId.toString());
        assertThat(response.getChoice()).isEqualTo("FULL_COMPENSATION");
        assertThat(response.getPaidAmount()).isEqualByComparingTo(new BigDecimal("270000"));
    }

    @Test
    @DisplayName("PARTIAL_WITH_ITEM_BACK -> đền theo degradedCompensationRate snapshot, sản phẩm WITHDRAWN")
    void decide_partialWithItemBack_paysRateAndWithdrawsProduct() {
        DegradedItemCase c = degradedCase(new BigDecimal("270000"), null);
        Wallet wallet = Wallet.builder().id(UUID.randomUUID()).member(seller).balance(BigDecimal.ZERO).build();
        when(walletRepository.findByMember_Id(seller.getId())).thenReturn(Optional.of(wallet));

        DegradedItemCaseResponse response = service.decide(seller, caseId, request("PARTIAL_WITH_ITEM_BACK"));

        assertThat(c.getChoice()).isEqualTo(DegradedItemChoice.PARTIAL_WITH_ITEM_BACK);
        // 270000 * 0.50 = 135000
        assertThat(c.getPaidAmount()).isEqualByComparingTo(new BigDecimal("135000"));

        assertThat(product.getStatus()).isEqualTo(ProductStatus.WITHDRAWN);
        verify(productRepository).save(product);

        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("135000"));
        assertThat(response.getPaidAmount()).isEqualByComparingTo(new BigDecimal("135000"));
    }

    @Test
    @DisplayName("phí đền dùng rate snapshot trên biên nhận, không phải giá trị khác")
    void decide_usesReceiptSnapshotRate() {
        receipt.setDegradedCompensationRate(new BigDecimal("0.30"));
        degradedCase(new BigDecimal("200000"), null);
        when(walletRepository.findByMember_Id(seller.getId()))
                .thenReturn(Optional.of(Wallet.builder().id(UUID.randomUUID()).member(seller)
                        .balance(BigDecimal.ZERO).build()));

        DegradedItemCaseResponse response = service.decide(seller, caseId, request("PARTIAL_WITH_ITEM_BACK"));

        assertThat(response.getPaidAmount()).isEqualByComparingTo(new BigDecimal("60000"));
    }

    @Test
    @DisplayName("cộng ví tạo bút toán DEGRADED_COMPENSATION kèm đối ứng quỹ nền tảng")
    void decide_recordsWalletTransactionWithPlatformCounterpart() {
        DegradedItemCase c = degradedCase(new BigDecimal("270000"), null);
        Wallet wallet = Wallet.builder().id(UUID.randomUUID()).member(seller).balance(BigDecimal.ZERO).build();
        when(walletRepository.findByMember_Id(seller.getId())).thenReturn(Optional.of(wallet));

        service.decide(seller, caseId, request("FULL_COMPENSATION"));

        ArgumentCaptor<com.rehome.rehome.entity.marketplace.WalletTransaction> captor =
                ArgumentCaptor.forClass(com.rehome.rehome.entity.marketplace.WalletTransaction.class);
        verify(walletTransactionRepository, org.mockito.Mockito.times(2)).save(captor.capture());

        com.rehome.rehome.entity.marketplace.WalletTransaction memberTx = captor.getAllValues().get(0);
        assertThat(memberTx.getType())
                .isEqualTo(com.rehome.rehome.entity.enums.WalletTransactionType.DEGRADED_COMPENSATION);
        assertThat(memberTx.getAmount()).isEqualByComparingTo(new BigDecimal("270000"));
        assertThat(memberTx.getRelatedDegradedItemCase()).isEqualTo(c);

        com.rehome.rehome.entity.marketplace.WalletTransaction platformTx = captor.getAllValues().get(1);
        assertThat(platformTx.getAmount()).isEqualByComparingTo(new BigDecimal("-270000"));
        assertThat(platformTx.getCounterpartTransaction()).isEqualTo(memberTx);
    }

    // ------------------------------------------------------------------
    // Member xem ca hàng xuống cấp của mình (chi tiết + danh sách)
    // ------------------------------------------------------------------

    private Account someMember() {
        return Account.builder().id(UUID.randomUUID()).role(Role.MEMBER).build();
    }

    private DegradedItemCase caseFor(Account seller, ReturnRequest returnRequest,
                                     DegradedItemChoice choice) {
        ConsignmentReceipt itemReceipt = ConsignmentReceipt.builder()
                .degradedCompensationRate(new BigDecimal("0.50")).build();
        Product item = Product.builder()
                .id("Q7-AK-000845")
                .name("Áo khoác Uniqlo")
                .member(seller)
                .consignmentReceipt(itemReceipt)
                .build();
        return DegradedItemCase.builder()
                .id(UUID.randomUUID())
                .product(item)
                .returnRequest(returnRequest)
                .choice(choice)
                .compensationAmount(new BigDecimal("360000"))
                .build();
    }

    private ReturnRequest returnRequestWith(String reason, String conditionNote) {
        return ReturnRequest.builder()
                .id(UUID.randomUUID())
                .reason(reason)
                .conditionNote(conditionNote)
                .build();
    }

    @Test
    void getMine_returnsPhotosReasonAndModeratorNote() {
        Account seller = someMember();
        ReturnRequest returnRequest = returnRequestWith("Áo bị rộng", "Về kho bị bung chỉ vai phải");
        DegradedItemCase degradedCase = caseFor(seller, returnRequest, null);
        degradedCase.setCreatedAt(Instant.parse("2026-10-20T00:00:00Z"));
        when(degradedItemCaseRepository.findDetailById(degradedCase.getId()))
                .thenReturn(Optional.of(degradedCase));
        when(returnRequestPhotoRepository.findAllByReturnRequest_IdOrderByIdAsc(returnRequest.getId()))
                .thenReturn(List.of(
                        ReturnRequestPhoto.builder().imageUrl("https://cdn/1.jpg").build(),
                        ReturnRequestPhoto.builder().imageUrl("https://cdn/2.jpg").build()));

        DegradedItemCaseDetailResponse res = service.getMine(seller, degradedCase.getId());

        assertThat(res.getId()).isEqualTo(degradedCase.getId().toString());
        assertThat(res.getProductId()).isEqualTo("Q7-AK-000845");
        assertThat(res.getProductName()).isEqualTo("Áo khoác Uniqlo");
        assertThat(res.getPhotos()).containsExactly("https://cdn/1.jpg", "https://cdn/2.jpg");
        assertThat(res.getReason()).isEqualTo("Áo bị rộng");
        assertThat(res.getModeratorNote()).isEqualTo("Về kho bị bung chỉ vai phải");
        assertThat(res.getCompensationAmount()).isEqualByComparingTo("360000");
        assertThat(res.getStatus()).isEqualTo("PENDING_DECISION");
        assertThat(res.getDecision()).isNull();
        // Hệ thống chưa có hạn quyết định/lấy hàng hay auto-decide cho F-036
        assertThat(res.getDecisionDeadline()).isNull();
        assertThat(res.getPickupDeadline()).isNull();
        assertThat(res.isAutoDecided()).isFalse();
        assertThat(res.getReceivedAt()).isEqualTo(Instant.parse("2026-10-20T00:00:00Z"));
        assertThat(res.getDegradedCompensationRate()).isEqualByComparingTo("0.50");
        assertThat(res.getPaidAmount()).isNull();
        assertThat(res.getDecidedAt()).isNull();
    }

    @Test
    void getMine_returnsDecidedStatus_afterMemberChose() {
        Account seller = someMember();
        DegradedItemCase degradedCase = caseFor(seller, returnRequestWith("x", "y"),
                DegradedItemChoice.PARTIAL_WITH_ITEM_BACK);
        degradedCase.setPaidAmount(new BigDecimal("180000"));
        degradedCase.setDecidedAt(Instant.parse("2026-11-01T00:00:00Z"));
        when(degradedItemCaseRepository.findDetailById(degradedCase.getId()))
                .thenReturn(Optional.of(degradedCase));

        DegradedItemCaseDetailResponse res = service.getMine(seller, degradedCase.getId());

        assertThat(res.getStatus()).isEqualTo("DECIDED");
        assertThat(res.getDecision()).isEqualTo("PARTIAL_WITH_ITEM_BACK");
        assertThat(res.getPaidAmount()).isEqualByComparingTo("180000");
        assertThat(res.getDecidedAt()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
    }

    @Test
    void getMine_handlesCaseWithoutReturnRequest() {
        Account seller = someMember();
        DegradedItemCase degradedCase = caseFor(seller, null, null);
        when(degradedItemCaseRepository.findDetailById(degradedCase.getId()))
                .thenReturn(Optional.of(degradedCase));

        DegradedItemCaseDetailResponse res = service.getMine(seller, degradedCase.getId());

        // Không có yêu cầu trả hàng thì không có ảnh và không có lý do để hiện
        assertThat(res.getPhotos()).isEmpty();
        assertThat(res.getReason()).isNull();
        assertThat(res.getModeratorNote()).isNull();
        verify(returnRequestPhotoRepository, never()).findAllByReturnRequest_IdOrderByIdAsc(any());
    }

    @Test
    void getMine_throwsNotFound_whenMissing() {
        UUID id = UUID.randomUUID();
        when(degradedItemCaseRepository.findDetailById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMine(someMember(), id))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DEGRADED_ITEM_CASE_NOT_FOUND);
    }

    @Test
    void getMine_throwsForbidden_whenCaseBelongsToAnotherMember() {
        DegradedItemCase degradedCase = caseFor(someMember(), null, null);
        when(degradedItemCaseRepository.findDetailById(degradedCase.getId()))
                .thenReturn(Optional.of(degradedCase));

        assertThatThrownBy(() -> service.getMine(someMember(), degradedCase.getId()))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DEGRADED_ITEM_CASE_FORBIDDEN);
    }

    // ------------------------------------------------------------------
    // Moderator xem ca thuộc kho mình quản lý (vd. ca Moderator khác mở lúc kiểm kho)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("getMine: Moderator xem được ca thuộc kho mình")
    void getMine_moderatorSameWarehouse_succeeds() {
        Account moderator = moderatorWithWarehouse();
        Product item = Product.builder().id("Q7-AK-000845").name("Áo khoác Uniqlo")
                .member(someMember()).warehouse(moderator.getWarehouse())
                .consignmentReceipt(ConsignmentReceipt.builder()
                        .degradedCompensationRate(new BigDecimal("0.50")).build())
                .build();
        DegradedItemCase degradedCase = DegradedItemCase.builder()
                .id(UUID.randomUUID()).product(item).compensationAmount(new BigDecimal("360000")).build();
        when(degradedItemCaseRepository.findDetailById(degradedCase.getId()))
                .thenReturn(Optional.of(degradedCase));

        DegradedItemCaseDetailResponse res = service.getMine(moderator, degradedCase.getId());

        assertThat(res.getProductId()).isEqualTo("Q7-AK-000845");
    }

    @Test
    @DisplayName("getMine: Moderator thuộc kho khác -> DEGRADED_ITEM_CASE_FORBIDDEN")
    void getMine_moderatorOtherWarehouse_throwsForbidden() {
        Account moderator = moderatorWithWarehouse();
        Product item = Product.builder().id("Q7-AK-000845")
                .member(someMember()).warehouse(Warehouse.builder().id(UUID.randomUUID()).build())
                .consignmentReceipt(ConsignmentReceipt.builder()
                        .degradedCompensationRate(new BigDecimal("0.50")).build())
                .build();
        DegradedItemCase degradedCase = DegradedItemCase.builder()
                .id(UUID.randomUUID()).product(item).compensationAmount(new BigDecimal("360000")).build();
        when(degradedItemCaseRepository.findDetailById(degradedCase.getId()))
                .thenReturn(Optional.of(degradedCase));

        assertThatThrownBy(() -> service.getMine(moderator, degradedCase.getId()))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DEGRADED_ITEM_CASE_FORBIDDEN);
    }

    @Test
    @DisplayName("getMine: Moderator chưa được gán kho -> MODERATOR_NO_WAREHOUSE")
    void getMine_moderatorNoWarehouse_throws() {
        Account moderator = Account.builder().id(UUID.randomUUID()).role(Role.MODERATOR).build();
        DegradedItemCase degradedCase = caseFor(someMember(), null, null);
        when(degradedItemCaseRepository.findDetailById(degradedCase.getId()))
                .thenReturn(Optional.of(degradedCase));

        assertThatThrownBy(() -> service.getMine(moderator, degradedCase.getId()))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.MODERATOR_NO_WAREHOUSE);
    }

    @Test
    void listMine_noFilter_asksForEverything() {
        Account seller = someMember();
        when(degradedItemCaseRepository.findDetailsByMember(seller.getId(), false, false))
                .thenReturn(List.of(caseFor(seller, null, DegradedItemChoice.FULL_COMPENSATION)));

        assertThat(service.listMine(seller, null)).hasSize(1);
        assertThat(service.listMine(seller, "   ")).hasSize(1);

        verify(degradedItemCaseRepository, times(2)).findDetailsByMember(seller.getId(), false, false);
    }

    @Test
    void listMine_pendingDecision_onlyAsksForUndecidedCases() {
        Account seller = someMember();
        when(degradedItemCaseRepository.findDetailsByMember(seller.getId(), true, false))
                .thenReturn(List.of(caseFor(seller, null, null)));

        // Đây là cái app dùng để hiện dải nhắc ở tab Ký gửi
        assertThat(service.listMine(seller, "pending_decision")).hasSize(1);

        verify(degradedItemCaseRepository).findDetailsByMember(seller.getId(), true, false);
    }

    @Test
    void listMine_decided_onlyAsksForDecidedCases() {
        Account seller = someMember();
        when(degradedItemCaseRepository.findDetailsByMember(seller.getId(), false, true))
                .thenReturn(List.of(caseFor(seller, null, DegradedItemChoice.FULL_COMPENSATION)));

        assertThat(service.listMine(seller, "DECIDED")).hasSize(1);

        verify(degradedItemCaseRepository).findDetailsByMember(seller.getId(), false, true);
    }

    @Test
    void listMine_throwsBadRequest_whenStatusIsUnknown() {
        Account seller = someMember();

        assertThatThrownBy(() -> service.listMine(seller, "CHO_DUYET"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("status");

        verify(degradedItemCaseRepository, never()).findDetailsByMember(any(), anyBoolean(), anyBoolean());
    }
}
