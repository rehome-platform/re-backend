package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.request.DecideEndOfTermRequest;
import com.rehome.rehome.dto.response.DecideEndOfTermResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.EndOfTermChoice;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.EndOfTermDecision;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.ConsignmentReceiptRepository;
import com.rehome.rehome.repository.EndOfTermDecisionRepository;
import com.rehome.rehome.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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
class EndOfTermDecisionServiceImplTest {

    @Mock private EndOfTermDecisionRepository decisionRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ConsignmentReceiptRepository consignmentReceiptRepository;

    @InjectMocks private EndOfTermDecisionServiceImpl service;

    @Mock private Account account;
    @Mock private DecideEndOfTermRequest request;
    @Mock private EndOfTermDecision decision;
    @Mock private Product product;

    private final UUID accountId = UUID.randomUUID();
    private final UUID decisionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Mặc định: happy path. Từng test sẽ ghi đè phần cần thay đổi.
        lenient().when(account.getRole()).thenReturn(Role.MEMBER);
        lenient().when(account.getId()).thenReturn(accountId);

        lenient().when(decisionRepository.findByIdForUpdate(decisionId)).thenReturn(Optional.of(decision));
        lenient().when(decision.getProduct()).thenReturn(product);
        lenient().when(decision.getDecision()).thenReturn(null);
        lenient().when(decision.getDeadline()).thenReturn(Instant.now().plus(1, ChronoUnit.DAYS));

        lenient().when(product.getMember()).thenReturn(account);
    }

    private void assertErrorCode(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
    }

    private void verifyNothingPersisted() {
        verify(decisionRepository, never()).save(any());
        verifyNoInteractions(productRepository);
        verifyNoInteractions(consignmentReceiptRepository);
    }

    // ---------------------------------------------------------------- validation

    @Test
    @DisplayName("Không phải MEMBER -> NOT_MEMBER_ACCOUNT")
    void decide_notMember_throws() {
        when(account.getRole()).thenReturn(Role.MODERATOR);

        assertErrorCode(() -> service.decide(account, decisionId, request), ErrorCode.NOT_MEMBER_ACCOUNT);

        verifyNoInteractions(decisionRepository);
        verifyNothingPersisted();
    }

    @Test
    @DisplayName("Decision = null -> INVALID_DECISION")
    void decide_nullChoice_throws() {
        when(request.getDecision()).thenReturn(null);

        assertErrorCode(() -> service.decide(account, decisionId, request), ErrorCode.INVALID_DECISION);

        verifyNothingPersisted();
    }

    @Test
    @DisplayName("Decision = UNCLAIMED (client không được chọn) -> INVALID_DECISION")
    void decide_unclaimedChoice_throws() {
        when(request.getDecision()).thenReturn(EndOfTermChoice.UNCLAIMED);

        assertErrorCode(() -> service.decide(account, decisionId, request), ErrorCode.INVALID_DECISION);

        verifyNothingPersisted();
    }

    @Test
    @DisplayName("Không tìm thấy decision -> END_OF_TERM_DECISION_NOT_FOUND")
    void decide_decisionNotFound_throws() {
        when(request.getDecision()).thenReturn(EndOfTermChoice.TAKE_BACK);
        when(decisionRepository.findByIdForUpdate(decisionId)).thenReturn(Optional.empty());

        assertErrorCode(() -> service.decide(account, decisionId, request),
                ErrorCode.END_OF_TERM_DECISION_NOT_FOUND);

        verifyNothingPersisted();
    }

    @Test
    @DisplayName("Sản phẩm không thuộc member này -> END_OF_TERM_DECISION_FORBIDDEN")
    void decide_notOwner_throws() {
        Account other = org.mockito.Mockito.mock(Account.class);
        when(other.getId()).thenReturn(UUID.randomUUID());
        when(product.getMember()).thenReturn(other);
        when(request.getDecision()).thenReturn(EndOfTermChoice.TAKE_BACK);

        assertErrorCode(() -> service.decide(account, decisionId, request),
                ErrorCode.END_OF_TERM_DECISION_FORBIDDEN);

        verifyNothingPersisted();
    }

    @Test
    @DisplayName("Job đã đánh dấu UNCLAIMED -> DECISION_EXPIRED")
    void decide_alreadyUnclaimed_throwsExpired() {
        when(request.getDecision()).thenReturn(EndOfTermChoice.TAKE_BACK);
        when(decision.getDecision()).thenReturn(EndOfTermChoice.UNCLAIMED);

        assertErrorCode(() -> service.decide(account, decisionId, request), ErrorCode.DECISION_EXPIRED);

        verifyNothingPersisted();
    }

    @Test
    @DisplayName("Đã quyết định trước đó -> END_OF_TERM_DECISION_ALREADY_DECIDED")
    void decide_alreadyDecided_throws() {
        when(request.getDecision()).thenReturn(EndOfTermChoice.RE_CONSIGN);
        when(decision.getDecision()).thenReturn(EndOfTermChoice.TAKE_BACK);

        assertErrorCode(() -> service.decide(account, decisionId, request),
                ErrorCode.END_OF_TERM_DECISION_ALREADY_DECIDED);

        verifyNothingPersisted();
    }

    @Test
    @DisplayName("Quá hạn nhưng job chưa chạy -> DECISION_EXPIRED")
    void decide_pastDeadline_throwsExpired() {
        when(request.getDecision()).thenReturn(EndOfTermChoice.TAKE_BACK);
        when(decision.getDeadline()).thenReturn(Instant.now().minus(1, ChronoUnit.MINUTES));

        assertErrorCode(() -> service.decide(account, decisionId, request), ErrorCode.DECISION_EXPIRED);

        verifyNothingPersisted();
    }

    // ---------------------------------------------------------------- happy path

    @Test
    @DisplayName("TAKE_BACK -> product WITHDRAWN, không tạo phiếu mới")
    void decide_takeBack_success() {
        when(request.getDecision()).thenReturn(EndOfTermChoice.TAKE_BACK);

        DecideEndOfTermResponse response = service.decide(account, decisionId, request);

        verify(product).setStatus(ProductStatus.WITHDRAWN);
        verify(productRepository).save(product);
        verifyNoInteractions(consignmentReceiptRepository);

        verify(decision).setDecision(EndOfTermChoice.TAKE_BACK);
        verify(decision).setDecidedAt(any(Instant.class));
        verify(decision).setNewReceipt(null);
        verify(decisionRepository).save(decision);

        assertThat(response.getDecision()).isEqualTo(EndOfTermChoice.TAKE_BACK);
        assertThat(response.getNewReceiptId()).isNull();
    }

    @Test
    @DisplayName("RE_CONSIGN -> tạo phiếu ký gửi mới, sao chép điều khoản, chưa xác nhận, chưa có PDF")
    void decide_reConsign_success() {
        UUID newReceiptId = UUID.randomUUID();
        Account member = org.mockito.Mockito.mock(Account.class);

        ConsignmentReceipt oldReceipt = org.mockito.Mockito.mock(ConsignmentReceipt.class);
        when(oldReceipt.getMember()).thenReturn(member);
        when(product.getConsignmentReceipt()).thenReturn(oldReceipt);

        ConsignmentReceipt savedReceipt = org.mockito.Mockito.mock(ConsignmentReceipt.class);
        when(savedReceipt.getId()).thenReturn(newReceiptId);

        when(consignmentReceiptRepository.nextReceiptSequence()).thenReturn(42L);
        when(consignmentReceiptRepository.save(any(ConsignmentReceipt.class))).thenReturn(savedReceipt);
        when(request.getDecision()).thenReturn(EndOfTermChoice.RE_CONSIGN);

        DecideEndOfTermResponse response = service.decide(account, decisionId, request);

        // Phiếu mới được build đúng
        ArgumentCaptor<ConsignmentReceipt> captor = ArgumentCaptor.forClass(ConsignmentReceipt.class);
        verify(consignmentReceiptRepository).save(captor.capture());
        ConsignmentReceipt built = captor.getValue();

        String expectedNo = "KG-%d-%06d".formatted(
                Year.now(ZoneId.of("Asia/Ho_Chi_Minh")).getValue(), 42L);
        assertThat(built.getReceiptNo()).isEqualTo(expectedNo);
        assertThat(built.getMember()).isSameAs(member);
        assertThat(built.getConfirmedAt()).isNull();
        assertThat(built.getPdfUrl()).isNull();

        // Product không bị đổi trạng thái
        verify(product, never()).setStatus(any());
        verifyNoInteractions(productRepository);

        // Decision được cập nhật
        verify(decision).setDecision(EndOfTermChoice.RE_CONSIGN);
        verify(decision).setDecidedAt(any(Instant.class));
        verify(decision).setNewReceipt(savedReceipt);
        verify(decisionRepository).save(decision);

        assertThat(response.getDecision()).isEqualTo(EndOfTermChoice.RE_CONSIGN);
        assertThat(response.getNewReceiptId()).isEqualTo(newReceiptId);
    }
}