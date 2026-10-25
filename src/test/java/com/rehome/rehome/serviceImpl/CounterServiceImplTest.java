package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.request.CounterHandoverRequest;
import com.rehome.rehome.dto.request.CounterLookupRequest;
import com.rehome.rehome.dto.response.MemberLookupItemResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.MemberProfile;
import com.rehome.rehome.entity.counter.CounterIdentityConfirmation;
import com.rehome.rehome.entity.enums.EndOfTermChoice;
import com.rehome.rehome.entity.enums.InvalidReturnChoice;
import com.rehome.rehome.entity.enums.ItemWithdrawalStatus;
import com.rehome.rehome.entity.enums.ReturnRequestStatus;
import com.rehome.rehome.entity.marketplace.EndOfTermDecision;
import com.rehome.rehome.entity.marketplace.ItemWithdrawalRequest;
import com.rehome.rehome.entity.marketplace.Order;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.ReturnRequest;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.CounterIdentityConfirmationRepository;
import com.rehome.rehome.repository.EndOfTermDecisionRepository;
import com.rehome.rehome.repository.ItemWithdrawalRequestRepository;
import com.rehome.rehome.repository.MemberProfileRepository;
import com.rehome.rehome.repository.ReturnRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CounterServiceImplTest {

    @Mock private AccountRepository accountRepository;
    @Mock private CounterIdentityConfirmationRepository identityConfirmationRepository;
    @Mock private ItemWithdrawalRequestRepository itemWithdrawalRequestRepository;
    @Mock private EndOfTermDecisionRepository endOfTermDecisionRepository;
    @Mock private ReturnRequestRepository returnRequestRepository;
    @Mock private MemberProfileRepository memberProfileRepository;

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

    private CounterLookupRequest lookupRequest(String memberCode, String phone, String qrPayload) {
        CounterLookupRequest r = new CounterLookupRequest();
        r.setMemberCode(memberCode);
        r.setPhone(phone);
        r.setQrPayload(qrPayload);
        return r;
    }

    private MemberProfile profile(String memberCode, String fullName) {
        return MemberProfile.builder()
                .accountId(memberId)
                .fullName(fullName)
                .avatarUrl("https://cdn.rehome.vn/uploads/avatar.jpg")
                .memberCode(memberCode)
                .build();
    }

    // ---------------------------------------------------------------- lookup

    @Test
    @DisplayName("lookup: theo memberCode -> trả đúng 1 item")
    void lookup_byMemberCode_returnsOneItem() {
        when(memberProfileRepository.findByMemberCode("MB000123"))
                .thenReturn(Optional.of(profile("MB000123", "Tạ Thị Ngọc Anh")));

        List<MemberLookupItemResponse> result = service.lookup(lookupRequest("MB000123", null, null));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAccountId()).isEqualTo(memberId);
        assertThat(result.get(0).getMemberCode()).isEqualTo("MB000123");
        assertThat(result.get(0).getFullName()).isEqualTo("Tạ Thị Ngọc Anh");
    }

    @Test
    @DisplayName("lookup: memberCode nhập tay chữ thường/có space -> chuẩn hoá trước khi tra")
    void lookup_byMemberCode_normalizesInput() {
        when(memberProfileRepository.findByMemberCode("MB000123"))
                .thenReturn(Optional.of(profile("MB000123", "Tạ Thị Ngọc Anh")));

        service.lookup(lookupRequest("  mb000123 ", null, null));

        verify(memberProfileRepository).findByMemberCode("MB000123");
    }

    @Test
    @DisplayName("lookup: quét QR -> bóc memberCode trong payload rồi tra như mã")
    void lookup_byQrPayload_resolvesMemberCode() {
        when(memberProfileRepository.findByMemberCode("MB000123"))
                .thenReturn(Optional.of(profile("MB000123", "Tạ Thị Ngọc Anh")));

        List<MemberLookupItemResponse> result =
                service.lookup(lookupRequest(null, null, "REHOME:MEMBER:v1:MB000123"));

        assertThat(result).hasSize(1);
        verify(memberProfileRepository).findByMemberCode("MB000123");
    }

    @Test
    @DisplayName("lookup: QR sai tiền tố (ví dụ QR sản phẩm) -> QR_PAYLOAD_INVALID")
    void lookup_byQrPayload_wrongPrefix_throws() {
        assertErrorCode(() -> service.lookup(lookupRequest(null, null, "REHOME:PRODUCT:v1:SKU-1")),
                ErrorCode.QR_PAYLOAD_INVALID);

        verifyNoInteractions(memberProfileRepository);
    }

    @Test
    @DisplayName("lookup: QR đúng tiền tố nhưng rỗng phần mã -> QR_PAYLOAD_INVALID")
    void lookup_byQrPayload_emptyCode_throws() {
        assertErrorCode(() -> service.lookup(lookupRequest(null, null, "REHOME:MEMBER:v1:   ")),
                ErrorCode.QR_PAYLOAD_INVALID);

        verifyNoInteractions(memberProfileRepository);
    }

    @Test
    @DisplayName("lookup: theo số điện thoại -> trả hết các Member khớp để Moderator chọn")
    void lookup_byPhone_returnsAllMatches() {
        when(memberProfileRepository.findByAccount_Phone("0912345678")).thenReturn(List.of(
                profile("MB000123", "Tạ Thị Ngọc Anh"),
                profile("MB000124", "Nguyễn Văn B")));

        List<MemberLookupItemResponse> result = service.lookup(lookupRequest(null, "0912345678", null));

        assertThat(result).hasSize(2);
        assertThat(result).extracting(MemberLookupItemResponse::getMemberCode)
                .containsExactly("MB000123", "MB000124");
    }

    @Test
    @DisplayName("lookup: không gửi field nào -> LOOKUP_INVALID_INPUT")
    void lookup_noField_throws() {
        assertErrorCode(() -> service.lookup(lookupRequest(null, "  ", "")), ErrorCode.LOOKUP_INVALID_INPUT);

        verifyNoInteractions(memberProfileRepository);
    }

    @Test
    @DisplayName("lookup: gửi hơn 1 field -> LOOKUP_INVALID_INPUT")
    void lookup_moreThanOneField_throws() {
        assertErrorCode(() -> service.lookup(lookupRequest("MB000123", "0912345678", null)),
                ErrorCode.LOOKUP_INVALID_INPUT);

        verifyNoInteractions(memberProfileRepository);
    }

    @Test
    @DisplayName("lookup: mã không tồn tại -> MEMBER_NOT_FOUND")
    void lookup_memberCodeNotFound_throws() {
        when(memberProfileRepository.findByMemberCode("MB999999")).thenReturn(Optional.empty());

        assertErrorCode(() -> service.lookup(lookupRequest("MB999999", null, null)), ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("lookup: số điện thoại không khớp ai -> MEMBER_NOT_FOUND")
    void lookup_phoneNotFound_throws() {
        when(memberProfileRepository.findByAccount_Phone("0900000000")).thenReturn(List.of());

        assertErrorCode(() -> service.lookup(lookupRequest(null, "0900000000", null)), ErrorCode.MEMBER_NOT_FOUND);
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

    private ItemWithdrawalRequest itemWithdrawalRequest(Account owner, ItemWithdrawalStatus status) {
        return ItemWithdrawalRequest.builder()
                .id(refId)
                .member(owner)
                .status(status)
                .pickupDeadline(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();
    }

    @Test
    @DisplayName("handover ITEM_WITHDRAWAL: thành công -> COMPLETED + completedAt")
    void handover_itemWithdrawal_success() {
        ItemWithdrawalRequest withdrawal = itemWithdrawalRequest(member, ItemWithdrawalStatus.PENDING_PICKUP);
        when(itemWithdrawalRequestRepository.findById(refId)).thenReturn(Optional.of(withdrawal));
        CounterHandoverRequest request = request("ITEM_WITHDRAWAL", memberId, refId.toString());

        service.handover(moderator, request);

        assertThat(withdrawal.getStatus()).isEqualTo(ItemWithdrawalStatus.COMPLETED);
        assertThat(withdrawal.getCompletedAt()).isNotNull();
        verify(itemWithdrawalRequestRepository).save(withdrawal);
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
