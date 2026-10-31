package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.request.CounterHandoverRequest;
import com.rehome.rehome.dto.request.CounterLookupRequest;
import com.rehome.rehome.dto.response.MemberLookupItemResponse;
import com.rehome.rehome.dto.response.MemberTasksResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.MemberProfile;
import com.rehome.rehome.entity.counter.CounterIdentityConfirmation;
import com.rehome.rehome.entity.enums.ConsignmentFormStatus;
import com.rehome.rehome.entity.enums.EndOfTermChoice;
import com.rehome.rehome.entity.enums.InvalidReturnChoice;
import com.rehome.rehome.entity.enums.ItemWithdrawalStatus;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.ReturnRequestStatus;
import com.rehome.rehome.entity.enums.StockMovementType;
import com.rehome.rehome.entity.marketplace.ConsignmentForm;
import com.rehome.rehome.entity.marketplace.EndOfTermDecision;
import com.rehome.rehome.entity.marketplace.ItemWithdrawalRequest;
import com.rehome.rehome.entity.marketplace.Order;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.ReturnRequest;
import com.rehome.rehome.entity.marketplace.StockMovementLog;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.ConsignmentFormRepository;
import com.rehome.rehome.repository.CounterIdentityConfirmationRepository;
import com.rehome.rehome.repository.EndOfTermDecisionRepository;
import com.rehome.rehome.repository.ItemWithdrawalRequestRepository;
import com.rehome.rehome.repository.MemberProfileRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.repository.ReturnRequestRepository;
import com.rehome.rehome.repository.StockMovementLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CounterServiceImplTest {

    @Mock private MemberProfileRepository memberProfileRepository;
    @Mock private ConsignmentFormRepository consignmentFormRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private CounterIdentityConfirmationRepository identityConfirmationRepository;
    @Mock private ItemWithdrawalRequestRepository itemWithdrawalRequestRepository;
    @Mock private EndOfTermDecisionRepository endOfTermDecisionRepository;
    @Mock private ReturnRequestRepository returnRequestRepository;
    @Mock private ProductRepository productRepository;
    @Mock private StockMovementLogRepository stockMovementLogRepository;

    @InjectMocks private CounterServiceImpl service;

    private final UUID moderatorId = UUID.randomUUID();
    private final UUID memberId = UUID.randomUUID();
    private final UUID refId = UUID.randomUUID();

    private Account moderator;
    private Account member;
    private CounterIdentityConfirmation validConfirmation;

    @BeforeEach
    void setUp() {
        moderator = Account.builder().id(moderatorId).build();
        member = Account.builder().id(memberId).build();

        validConfirmation = CounterIdentityConfirmation.builder()
                .accountId(memberId)
                .confirmedAt(Instant.now())
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .confirmedBy(moderator)
                .build();

        // Mặc định: danh tính đã được xác nhận và còn hạn. Từng test bàn giao sẽ ghi đè khi cần.
        lenient().when(identityConfirmationRepository.findById(memberId)).thenReturn(Optional.of(validConfirmation));
    }

    private CounterHandoverRequest request(String context, UUID accountId, String refId) {
        CounterHandoverRequest r = new CounterHandoverRequest();
        r.setContext(context);
        r.setAccountId(accountId);
        r.setRefId(refId);
        return r;
    }

    private CounterLookupRequest lookupRequest(String memberCode, String phone, String qrPayload) {
        CounterLookupRequest r = new CounterLookupRequest();
        r.setMemberCode(memberCode);
        r.setPhone(phone);
        r.setQrPayload(qrPayload);
        return r;
    }

    private MemberProfile profile(UUID accountId, String memberCode) {
        return MemberProfile.builder()
                .accountId(accountId)
                .fullName("Tạ Thị Ngọc Anh")
                .avatarUrl("https://cdn.rehome.vn/uploads/avatar.jpg")
                .memberCode(memberCode)
                .build();
    }

    private void assertErrorCode(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
    }

    private void verifyNoRecordRepoTouched() {
        verifyNoInteractions(itemWithdrawalRequestRepository);
        verifyNoInteractions(endOfTermDecisionRepository);
        verifyNoInteractions(returnRequestRepository);
    }

    // ---------------------------------------------------------------- lookup (API 25)

    @Test
    @DisplayName("lookup: tra theo phone -> trả về danh sách có Member")
    void lookup_byPhone_found() {
        when(memberProfileRepository.findByAccount_Phone("0912345678"))
                .thenReturn(List.of(profile(memberId, "MB-000123")));

        List<MemberLookupItemResponse> result = service.lookup(lookupRequest(null, "0912345678", null));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAccountId()).isEqualTo(memberId);
        assertThat(result.get(0).getFullName()).isEqualTo("Tạ Thị Ngọc Anh");
        assertThat(result.get(0).getAvatarUrl()).isEqualTo("https://cdn.rehome.vn/uploads/avatar.jpg");
        assertThat(result.get(0).getMemberCode()).isEqualTo("MB-000123");
    }

    @Test
    @DisplayName("lookup: tra theo memberCode -> đúng 1 phần tử")
    void lookup_byMemberCode_found() {
        when(memberProfileRepository.findByMemberCode("MB-000123"))
                .thenReturn(Optional.of(profile(memberId, "MB-000123")));

        List<MemberLookupItemResponse> result = service.lookup(lookupRequest("MB-000123", null, null));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMemberCode()).isEqualTo("MB-000123");
    }

    @Test
    @DisplayName("lookup: tra theo qrPayload -> coi payload là memberCode")
    void lookup_byQrPayload_usesMemberCodeLookup() {
        when(memberProfileRepository.findByMemberCode("MB-000123"))
                .thenReturn(Optional.of(profile(memberId, "MB-000123")));

        List<MemberLookupItemResponse> result = service.lookup(lookupRequest(null, null, "MB-000123"));

        assertThat(result).hasSize(1);
        verify(memberProfileRepository).findByMemberCode("MB-000123");
    }

    @Test
    @DisplayName("lookup: input được trim trước khi tra")
    void lookup_inputIsTrimmed() {
        when(memberProfileRepository.findByAccount_Phone("0912345678"))
                .thenReturn(List.of(profile(memberId, "MB-000123")));

        service.lookup(lookupRequest(null, "  0912345678  ", null));

        verify(memberProfileRepository).findByAccount_Phone("0912345678");
    }

    @Test
    @DisplayName("lookup: không gửi field nào -> LOOKUP_INVALID_INPUT")
    void lookup_noneProvided_throws() {
        assertErrorCode(() -> service.lookup(lookupRequest(null, null, null)), ErrorCode.LOOKUP_INVALID_INPUT);

        verifyNoInteractions(memberProfileRepository);
    }

    @Test
    @DisplayName("lookup: field rỗng hoặc toàn khoảng trắng coi như không gửi -> LOOKUP_INVALID_INPUT")
    void lookup_blankFields_throws() {
        assertErrorCode(() -> service.lookup(lookupRequest("", "   ", null)), ErrorCode.LOOKUP_INVALID_INPUT);

        verifyNoInteractions(memberProfileRepository);
    }

    @Test
    @DisplayName("lookup: gửi 2 field -> LOOKUP_INVALID_INPUT")
    void lookup_twoProvided_throws() {
        assertErrorCode(() -> service.lookup(lookupRequest("MB-000123", "0912345678", null)),
                ErrorCode.LOOKUP_INVALID_INPUT);

        verifyNoInteractions(memberProfileRepository);
    }

    @Test
    @DisplayName("lookup: gửi đủ 3 field -> LOOKUP_INVALID_INPUT")
    void lookup_threeProvided_throws() {
        assertErrorCode(() -> service.lookup(lookupRequest("MB-000123", "0912345678", "MB-000123")),
                ErrorCode.LOOKUP_INVALID_INPUT);

        verifyNoInteractions(memberProfileRepository);
    }

    @Test
    @DisplayName("lookup: phone không có Member -> MEMBER_NOT_FOUND")
    void lookup_phoneNotFound_throws() {
        when(memberProfileRepository.findByAccount_Phone("0900000000")).thenReturn(List.of());

        assertErrorCode(() -> service.lookup(lookupRequest(null, "0900000000", null)), ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("lookup: memberCode không tồn tại -> MEMBER_NOT_FOUND")
    void lookup_memberCodeNotFound_throws() {
        when(memberProfileRepository.findByMemberCode("MB-999999")).thenReturn(Optional.empty());

        assertErrorCode(() -> service.lookup(lookupRequest("MB-999999", null, null)), ErrorCode.MEMBER_NOT_FOUND);
    }

    // ---------------------------------------------------------------- getTasks (API 27)

    @Test
    @DisplayName("getTasks: Member không tồn tại -> MEMBER_NOT_FOUND, không query các bảng việc")
    void getTasks_memberNotFound_throws() {
        when(memberProfileRepository.existsById(memberId)).thenReturn(false);

        assertErrorCode(() -> service.getTasks(memberId), ErrorCode.MEMBER_NOT_FOUND);

        verifyNoInteractions(consignmentFormRepository);
        verifyNoRecordRepoTouched();
    }

    @Test
    @DisplayName("getTasks: chưa có việc nào -> 3 mảng rỗng, form được lấy với PENDING và APPROVED")
    void getTasks_noTasks_returnsEmptyLists() {
        List<ConsignmentFormStatus> openStatuses =
                List.of(ConsignmentFormStatus.PENDING, ConsignmentFormStatus.APPROVED);
        when(memberProfileRepository.existsById(memberId)).thenReturn(true);
        when(consignmentFormRepository.findByMember_IdAndStatusInOrderByCreatedAtAsc(eq(memberId), eq(openStatuses)))
                .thenReturn(List.of());
        when(itemWithdrawalRequestRepository.findByMember_IdAndStatusOrderByCreatedAtAsc(
                memberId, ItemWithdrawalStatus.PENDING_PICKUP)).thenReturn(List.of());
        when(endOfTermDecisionRepository.findUndecidedByMemberId(memberId)).thenReturn(List.of());

        MemberTasksResponse result = service.getTasks(memberId);

        assertThat(result.getPendingForms()).isEmpty();
        assertThat(result.getPendingWithdrawals()).isEmpty();
        assertThat(result.getPendingEndOfTerm()).isEmpty();
    }

    @Test
    @DisplayName("getTasks: map đúng dữ liệu của cả 3 nhóm việc")
    void getTasks_mapsAllThreeGroups() {
        Instant now = Instant.parse("2026-10-13T08:00:00Z");
        Product product = Product.builder().id("Q7-AK-000845").name("Áo khoác denim").member(member).build();

        ConsignmentForm form = ConsignmentForm.builder()
                .id(UUID.randomUUID())
                .productName("Váy hoa")
                .desiredPrice(new BigDecimal("250000"))
                .status(ConsignmentFormStatus.APPROVED)
                .createdAt(now)
                .build();
        ItemWithdrawalRequest withdrawal = ItemWithdrawalRequest.builder()
                .id(UUID.randomUUID())
                .product(product)
                .member(member)
                .feeCharged(new BigDecimal("30000"))
                .pickupDeadline(now.plusSeconds(3600))
                .build();
        EndOfTermDecision decision = EndOfTermDecision.builder()
                .id(UUID.randomUUID())
                .product(product)
                .deadline(now.plusSeconds(7200))
                .build();

        when(memberProfileRepository.existsById(memberId)).thenReturn(true);
        when(consignmentFormRepository.findByMember_IdAndStatusInOrderByCreatedAtAsc(eq(memberId), any()))
                .thenReturn(List.of(form));
        when(itemWithdrawalRequestRepository.findByMember_IdAndStatusOrderByCreatedAtAsc(
                memberId, ItemWithdrawalStatus.PENDING_PICKUP)).thenReturn(List.of(withdrawal));
        when(endOfTermDecisionRepository.findUndecidedByMemberId(memberId)).thenReturn(List.of(decision));

        MemberTasksResponse result = service.getTasks(memberId);

        assertThat(result.getPendingForms()).hasSize(1);
        MemberTasksResponse.PendingFormItem f = result.getPendingForms().get(0);
        assertThat(f.getId()).isEqualTo(form.getId());
        assertThat(f.getProductName()).isEqualTo("Váy hoa");
        assertThat(f.getDesiredPrice()).isEqualByComparingTo("250000");
        assertThat(f.getStatus()).isEqualTo(ConsignmentFormStatus.APPROVED);
        assertThat(f.getCreatedAt()).isEqualTo(now);

        assertThat(result.getPendingWithdrawals()).hasSize(1);
        MemberTasksResponse.PendingWithdrawalItem w = result.getPendingWithdrawals().get(0);
        assertThat(w.getId()).isEqualTo(withdrawal.getId());
        assertThat(w.getProductId()).isEqualTo("Q7-AK-000845");
        assertThat(w.getProductName()).isEqualTo("Áo khoác denim");
        assertThat(w.getFeeCharged()).isEqualByComparingTo("30000");
        assertThat(w.getPickupDeadline()).isEqualTo(now.plusSeconds(3600));

        assertThat(result.getPendingEndOfTerm()).hasSize(1);
        MemberTasksResponse.PendingEndOfTermItem e = result.getPendingEndOfTerm().get(0);
        assertThat(e.getId()).isEqualTo(decision.getId());
        assertThat(e.getProductId()).isEqualTo("Q7-AK-000845");
        assertThat(e.getProductName()).isEqualTo("Áo khoác denim");
        assertThat(e.getDeadline()).isEqualTo(now.plusSeconds(7200));
    }

    // ---------------------------------------------------------------- confirmIdentity

    @Test
    @DisplayName("confirmIdentity: account không tồn tại -> ACCOUNT_NOT_FOUND")
    void confirmIdentity_accountNotFound_throws() {
        when(accountRepository.findById(memberId)).thenReturn(Optional.empty());

        assertErrorCode(() -> service.confirmIdentity(moderator, memberId), ErrorCode.ACCOUNT_NOT_FOUND);

        verify(identityConfirmationRepository, never()).save(any());
    }

    @Test
    @DisplayName("confirmIdentity: chưa có bản ghi -> tạo mới, hết hạn sau 15 phút")
    void confirmIdentity_firstTime_createsConfirmation() {
        when(accountRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(identityConfirmationRepository.findById(memberId)).thenReturn(Optional.empty());

        service.confirmIdentity(moderator, memberId);

        ArgumentCaptor<CounterIdentityConfirmation> captor = ArgumentCaptor.forClass(CounterIdentityConfirmation.class);
        verify(identityConfirmationRepository).save(captor.capture());
        CounterIdentityConfirmation saved = captor.getValue();

        assertThat(saved.getAccountId()).isEqualTo(memberId);
        assertThat(saved.getConfirmedBy()).isSameAs(moderator);
        assertThat(saved.getExpiresAt()).isAfter(saved.getConfirmedAt());
        assertThat(saved.getExpiresAt()).isEqualTo(saved.getConfirmedAt().plus(15, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("confirmIdentity: đã có bản ghi cũ -> ghi đè lại thời điểm xác nhận")
    void confirmIdentity_existing_overwrites() {
        when(accountRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(identityConfirmationRepository.findById(memberId)).thenReturn(Optional.of(validConfirmation));

        service.confirmIdentity(moderator, memberId);

        ArgumentCaptor<CounterIdentityConfirmation> captor = ArgumentCaptor.forClass(CounterIdentityConfirmation.class);
        verify(identityConfirmationRepository).save(captor.capture());

        assertThat(captor.getValue()).isSameAs(validConfirmation);
        assertThat(captor.getValue().getConfirmedBy()).isSameAs(moderator);
    }

    // ---------------------------------------------------------------- handover - validation chung

    @Test
    @DisplayName("handover: context không hợp lệ -> COUNTER_CONTEXT_INVALID, không đụng tới identity/record")
    void handover_invalidContext_throws() {
        CounterHandoverRequest request = request("NOT_A_CONTEXT", memberId, refId.toString());

        assertErrorCode(() -> service.handover(moderator, request), ErrorCode.COUNTER_CONTEXT_INVALID);

        verifyNoInteractions(identityConfirmationRepository);
        verifyNoRecordRepoTouched();
    }

    @Test
    @DisplayName("handover: chưa xác nhận danh tính -> COUNTER_IDENTITY_NOT_CONFIRMED")
    void handover_identityNotConfirmed_throws() {
        when(identityConfirmationRepository.findById(memberId)).thenReturn(Optional.empty());
        CounterHandoverRequest request = request("ITEM_WITHDRAWAL", memberId, refId.toString());

        assertErrorCode(() -> service.handover(moderator, request), ErrorCode.COUNTER_IDENTITY_NOT_CONFIRMED);

        verifyNoRecordRepoTouched();
    }

    @Test
    @DisplayName("handover: xác nhận danh tính đã hết hạn -> COUNTER_IDENTITY_NOT_CONFIRMED")
    void handover_identityExpired_throws() {
        CounterIdentityConfirmation expired = CounterIdentityConfirmation.builder()
                .accountId(memberId)
                .confirmedAt(Instant.now().minus(20, ChronoUnit.MINUTES))
                .expiresAt(Instant.now().minus(5, ChronoUnit.MINUTES))
                .confirmedBy(moderator)
                .build();
        when(identityConfirmationRepository.findById(memberId)).thenReturn(Optional.of(expired));
        CounterHandoverRequest request = request("ITEM_WITHDRAWAL", memberId, refId.toString());

        assertErrorCode(() -> service.handover(moderator, request), ErrorCode.COUNTER_IDENTITY_NOT_CONFIRMED);

        verifyNoRecordRepoTouched();
    }

    @Test
    @DisplayName("handover: refId không phải UUID hợp lệ -> COUNTER_RECORD_NOT_FOUND")
    void handover_malformedRefId_throws() {
        CounterHandoverRequest request = request("ITEM_WITHDRAWAL", memberId, "iwr-001");

        assertErrorCode(() -> service.handover(moderator, request), ErrorCode.COUNTER_RECORD_NOT_FOUND);

        verifyNoRecordRepoTouched();
    }

    // ---------------------------------------------------------------- handover - ITEM_WITHDRAWAL

    private static final String ITEM_WITHDRAWAL_PRODUCT_ID = "Q7-AK-000845";

    private ItemWithdrawalRequest itemWithdrawalRequest(Account owner, ItemWithdrawalStatus status) {
        Warehouse warehouse = Warehouse.builder().id(UUID.randomUUID()).name("Kho Quận 7").build();
        // Sản phẩm đã WITHDRAWN từ lúc tạo yêu cầu (F-032 - ngừng bán ngay), chưa rời kho nên vẫn còn shelfLocation
        Product product = Product.builder()
                .id(ITEM_WITHDRAWAL_PRODUCT_ID)
                .member(owner)
                .warehouse(warehouse)
                .status(ProductStatus.WITHDRAWN)
                .shelfLocation("A1-01")
                .build();

        return ItemWithdrawalRequest.builder()
                .id(refId)
                .member(owner)
                .product(product)
                .status(status)
                .pickupDeadline(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();
    }

    @Test
    @DisplayName("handover ITEM_WITHDRAWAL: thành công -> COMPLETED + completedAt, gỡ sản phẩm khỏi sàn")
    void handover_itemWithdrawal_success() {
        ItemWithdrawalRequest withdrawal = itemWithdrawalRequest(member, ItemWithdrawalStatus.PENDING_PICKUP);
        when(itemWithdrawalRequestRepository.findById(refId)).thenReturn(Optional.of(withdrawal));
        when(productRepository.findByIdForUpdate(ITEM_WITHDRAWAL_PRODUCT_ID))
                .thenReturn(Optional.of(withdrawal.getProduct()));
        CounterHandoverRequest request = request("ITEM_WITHDRAWAL", memberId, refId.toString());

        service.handover(moderator, request);

        assertThat(withdrawal.getStatus()).isEqualTo(ItemWithdrawalStatus.COMPLETED);
        assertThat(withdrawal.getCompletedAt()).isNotNull();
        verify(itemWithdrawalRequestRepository).save(withdrawal);

        Product product = withdrawal.getProduct();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.WITHDRAWN);
        assertThat(product.getShelfLocation()).isNull();
        verify(productRepository).save(product);

        ArgumentCaptor<StockMovementLog> logCaptor = ArgumentCaptor.forClass(StockMovementLog.class);
        verify(stockMovementLogRepository).save(logCaptor.capture());
        StockMovementLog log = logCaptor.getValue();
        assertThat(log.getProduct()).isEqualTo(product);
        assertThat(log.getWarehouse()).isEqualTo(product.getWarehouse());
        assertThat(log.getMovementType()).isEqualTo(StockMovementType.WITHDRAWAL_OUT);
        assertThat(log.getFromLocation()).isEqualTo("A1-01");
        assertThat(log.getModerator()).isEqualTo(moderator);
    }

    @Test
    @DisplayName("handover ITEM_WITHDRAWAL: sản phẩm không còn WITHDRAWN (vd đã bị xử lý khác) -> vẫn đóng yêu cầu, không ghi log")
    void handover_itemWithdrawal_productNotWithdrawn_skipsProductUpdate() {
        ItemWithdrawalRequest withdrawal = itemWithdrawalRequest(member, ItemWithdrawalStatus.PENDING_PICKUP);
        withdrawal.getProduct().setStatus(ProductStatus.DESTROYED);
        when(itemWithdrawalRequestRepository.findById(refId)).thenReturn(Optional.of(withdrawal));
        when(productRepository.findByIdForUpdate(ITEM_WITHDRAWAL_PRODUCT_ID))
                .thenReturn(Optional.of(withdrawal.getProduct()));
        CounterHandoverRequest request = request("ITEM_WITHDRAWAL", memberId, refId.toString());

        service.handover(moderator, request);

        assertThat(withdrawal.getStatus()).isEqualTo(ItemWithdrawalStatus.COMPLETED);
        verify(productRepository, never()).save(any());
        verify(stockMovementLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("handover ITEM_WITHDRAWAL: không tìm thấy refId -> COUNTER_RECORD_NOT_FOUND")
    void handover_itemWithdrawal_notFound_throws() {
        when(itemWithdrawalRequestRepository.findById(refId)).thenReturn(Optional.empty());
        CounterHandoverRequest request = request("ITEM_WITHDRAWAL", memberId, refId.toString());

        assertErrorCode(() -> service.handover(moderator, request), ErrorCode.COUNTER_RECORD_NOT_FOUND);

        verify(itemWithdrawalRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("handover ITEM_WITHDRAWAL: refId thuộc member khác -> COUNTER_RECORD_NOT_FOUND")
    void handover_itemWithdrawal_wrongOwner_throws() {
        Account other = Account.builder().id(UUID.randomUUID()).build();
        ItemWithdrawalRequest withdrawal = itemWithdrawalRequest(other, ItemWithdrawalStatus.PENDING_PICKUP);
        when(itemWithdrawalRequestRepository.findById(refId)).thenReturn(Optional.of(withdrawal));
        CounterHandoverRequest request = request("ITEM_WITHDRAWAL", memberId, refId.toString());

        assertErrorCode(() -> service.handover(moderator, request), ErrorCode.COUNTER_RECORD_NOT_FOUND);

        verify(itemWithdrawalRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("handover ITEM_WITHDRAWAL: đã COMPLETED trước đó -> COUNTER_RECORD_NOT_FOUND")
    void handover_itemWithdrawal_alreadyCompleted_throws() {
        ItemWithdrawalRequest withdrawal = itemWithdrawalRequest(member, ItemWithdrawalStatus.COMPLETED);
        when(itemWithdrawalRequestRepository.findById(refId)).thenReturn(Optional.of(withdrawal));
        CounterHandoverRequest request = request("ITEM_WITHDRAWAL", memberId, refId.toString());

        assertErrorCode(() -> service.handover(moderator, request), ErrorCode.COUNTER_RECORD_NOT_FOUND);

        verify(itemWithdrawalRequestRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- handover - END_OF_TERM_TAKE_BACK

    private EndOfTermDecision endOfTermDecision(Account owner, EndOfTermChoice choice, Instant takenBackAt) {
        Product product = Product.builder().id("SKU-001").member(owner).build();
        return EndOfTermDecision.builder()
                .id(refId)
                .product(product)
                .decision(choice)
                .decidedAt(choice != null ? Instant.now() : null)
                .deadline(Instant.now().plus(1, ChronoUnit.DAYS))
                .takenBackAt(takenBackAt)
                .build();
    }

    @Test
    @DisplayName("handover END_OF_TERM_TAKE_BACK: thành công -> takenBackAt được set")
    void handover_endOfTerm_success() {
        EndOfTermDecision decision = endOfTermDecision(member, EndOfTermChoice.TAKE_BACK, null);
        when(endOfTermDecisionRepository.findById(refId)).thenReturn(Optional.of(decision));
        CounterHandoverRequest request = request("END_OF_TERM_TAKE_BACK", memberId, refId.toString());

        service.handover(moderator, request);

        assertThat(decision.getTakenBackAt()).isNotNull();
        verify(endOfTermDecisionRepository).save(decision);
    }

    @Test
    @DisplayName("handover END_OF_TERM_TAKE_BACK: không tìm thấy refId -> COUNTER_RECORD_NOT_FOUND")
    void handover_endOfTerm_notFound_throws() {
        when(endOfTermDecisionRepository.findById(refId)).thenReturn(Optional.empty());
        CounterHandoverRequest request = request("END_OF_TERM_TAKE_BACK", memberId, refId.toString());

        assertErrorCode(() -> service.handover(moderator, request), ErrorCode.COUNTER_RECORD_NOT_FOUND);

        verify(endOfTermDecisionRepository, never()).save(any());
    }

    @Test
    @DisplayName("handover END_OF_TERM_TAKE_BACK: quyết định không phải TAKE_BACK -> COUNTER_RECORD_NOT_FOUND")
    void handover_endOfTerm_notTakeBack_throws() {
        EndOfTermDecision decision = endOfTermDecision(member, EndOfTermChoice.RE_CONSIGN, null);
        when(endOfTermDecisionRepository.findById(refId)).thenReturn(Optional.of(decision));
        CounterHandoverRequest request = request("END_OF_TERM_TAKE_BACK", memberId, refId.toString());

        assertErrorCode(() -> service.handover(moderator, request), ErrorCode.COUNTER_RECORD_NOT_FOUND);

        verify(endOfTermDecisionRepository, never()).save(any());
    }

    @Test
    @DisplayName("handover END_OF_TERM_TAKE_BACK: đã bàn giao trước đó -> COUNTER_RECORD_NOT_FOUND")
    void handover_endOfTerm_alreadyTakenBack_throws() {
        EndOfTermDecision decision = endOfTermDecision(member, EndOfTermChoice.TAKE_BACK, Instant.now().minus(1, ChronoUnit.DAYS));
        when(endOfTermDecisionRepository.findById(refId)).thenReturn(Optional.of(decision));
        CounterHandoverRequest request = request("END_OF_TERM_TAKE_BACK", memberId, refId.toString());

        assertErrorCode(() -> service.handover(moderator, request), ErrorCode.COUNTER_RECORD_NOT_FOUND);

        verify(endOfTermDecisionRepository, never()).save(any());
    }

    @Test
    @DisplayName("handover END_OF_TERM_TAKE_BACK: sản phẩm không thuộc member này -> COUNTER_RECORD_NOT_FOUND")
    void handover_endOfTerm_wrongOwner_throws() {
        Account other = Account.builder().id(UUID.randomUUID()).build();
        EndOfTermDecision decision = endOfTermDecision(other, EndOfTermChoice.TAKE_BACK, null);
        when(endOfTermDecisionRepository.findById(refId)).thenReturn(Optional.of(decision));
        CounterHandoverRequest request = request("END_OF_TERM_TAKE_BACK", memberId, refId.toString());

        assertErrorCode(() -> service.handover(moderator, request), ErrorCode.COUNTER_RECORD_NOT_FOUND);

        verify(endOfTermDecisionRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- handover - INVALID_RETURN_PICKUP

    private ReturnRequest returnRequest(Account buyer, ReturnRequestStatus status,
                                        InvalidReturnChoice choice, Instant pickedUpAt) {
        Order order = Order.builder().id(UUID.randomUUID()).buyer(buyer).build();
        return ReturnRequest.builder()
                .id(refId)
                .order(order)
                .reason("Không đúng mô tả")
                .status(status)
                .invalidReturnChoice(choice)
                .pickedUpAt(pickedUpAt)
                .handoverDeadline(Instant.now().plus(3, ChronoUnit.DAYS))
                .build();
    }

    @Test
    @DisplayName("handover INVALID_RETURN_PICKUP: thành công -> pickedUpAt được set")
    void handover_invalidReturnPickup_success() {
        ReturnRequest returnRequest = returnRequest(member, ReturnRequestStatus.INVALID,
                InvalidReturnChoice.WAREHOUSE_PICKUP, null);
        when(returnRequestRepository.findById(refId)).thenReturn(Optional.of(returnRequest));
        CounterHandoverRequest request = request("INVALID_RETURN_PICKUP", memberId, refId.toString());

        service.handover(moderator, request);

        assertThat(returnRequest.getPickedUpAt()).isNotNull();
        verify(returnRequestRepository).save(returnRequest);
    }

    @Test
    @DisplayName("handover INVALID_RETURN_PICKUP: không tìm thấy refId -> COUNTER_RECORD_NOT_FOUND")
    void handover_invalidReturnPickup_notFound_throws() {
        when(returnRequestRepository.findById(refId)).thenReturn(Optional.empty());
        CounterHandoverRequest request = request("INVALID_RETURN_PICKUP", memberId, refId.toString());

        assertErrorCode(() -> service.handover(moderator, request), ErrorCode.COUNTER_RECORD_NOT_FOUND);

        verify(returnRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("handover INVALID_RETURN_PICKUP: status không phải INVALID -> COUNTER_RECORD_NOT_FOUND")
    void handover_invalidReturnPickup_wrongStatus_throws() {
        ReturnRequest returnRequest = returnRequest(member, ReturnRequestStatus.INSPECTING,
                null, null);
        when(returnRequestRepository.findById(refId)).thenReturn(Optional.of(returnRequest));
        CounterHandoverRequest request = request("INVALID_RETURN_PICKUP", memberId, refId.toString());

        assertErrorCode(() -> service.handover(moderator, request), ErrorCode.COUNTER_RECORD_NOT_FOUND);

        verify(returnRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("handover INVALID_RETURN_PICKUP: Member chọn SHIP_BACK, không phải nhận tại kho -> COUNTER_RECORD_NOT_FOUND")
    void handover_invalidReturnPickup_shipBackChoice_throws() {
        ReturnRequest returnRequest = returnRequest(member, ReturnRequestStatus.INVALID,
                InvalidReturnChoice.SHIP_BACK, null);
        when(returnRequestRepository.findById(refId)).thenReturn(Optional.of(returnRequest));
        CounterHandoverRequest request = request("INVALID_RETURN_PICKUP", memberId, refId.toString());

        assertErrorCode(() -> service.handover(moderator, request), ErrorCode.COUNTER_RECORD_NOT_FOUND);

        verify(returnRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("handover INVALID_RETURN_PICKUP: đã nhận hàng trước đó -> COUNTER_RECORD_NOT_FOUND")
    void handover_invalidReturnPickup_alreadyPickedUp_throws() {
        ReturnRequest returnRequest = returnRequest(member, ReturnRequestStatus.INVALID,
                InvalidReturnChoice.WAREHOUSE_PICKUP, Instant.now().minus(1, ChronoUnit.DAYS));
        when(returnRequestRepository.findById(refId)).thenReturn(Optional.of(returnRequest));
        CounterHandoverRequest request = request("INVALID_RETURN_PICKUP", memberId, refId.toString());

        assertErrorCode(() -> service.handover(moderator, request), ErrorCode.COUNTER_RECORD_NOT_FOUND);

        verify(returnRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("handover INVALID_RETURN_PICKUP: không phải người mua trong đơn -> COUNTER_RECORD_NOT_FOUND")
    void handover_invalidReturnPickup_wrongBuyer_throws() {
        Account other = Account.builder().id(UUID.randomUUID()).build();
        ReturnRequest returnRequest = returnRequest(other, ReturnRequestStatus.INVALID,
                InvalidReturnChoice.WAREHOUSE_PICKUP, null);
        when(returnRequestRepository.findById(refId)).thenReturn(Optional.of(returnRequest));
        CounterHandoverRequest request = request("INVALID_RETURN_PICKUP", memberId, refId.toString());

        assertErrorCode(() -> service.handover(moderator, request), ErrorCode.COUNTER_RECORD_NOT_FOUND);

        verify(returnRequestRepository, never()).save(any());
    }
}