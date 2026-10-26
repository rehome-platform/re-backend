package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.request.CounterHandoverRequest;
import com.rehome.rehome.dto.request.CounterLookupRequest;
import com.rehome.rehome.dto.response.MemberLookupItemResponse;
import com.rehome.rehome.dto.response.MemberTasksResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.MemberProfile;
import com.rehome.rehome.entity.counter.CounterIdentityConfirmation;
import com.rehome.rehome.entity.enums.*;
import com.rehome.rehome.entity.marketplace.EndOfTermDecision;
import com.rehome.rehome.entity.marketplace.ItemWithdrawalRequest;
import com.rehome.rehome.entity.marketplace.ReturnRequest;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.*;
import com.rehome.rehome.service.CounterService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CounterServiceImpl implements CounterService {

    private static final Duration IDENTITY_CONFIRMATION_TTL = Duration.ofMinutes(15);
    private final MemberProfileRepository memberProfileRepository;
    private final ConsignmentFormRepository consignmentFormRepository;
    private final ItemWithdrawalRequestRepository itemWithdrawalRequestRepository;
    private final EndOfTermDecisionRepository endOfTermDecisionRepository;
    private final AccountRepository accountRepository;
    private final CounterIdentityConfirmationRepository identityConfirmationRepository;
    private final ReturnRequestRepository returnRequestRepository;

    private static final List<ConsignmentFormStatus> OPEN_FORM_STATUSES =
            List.of(ConsignmentFormStatus.PENDING, ConsignmentFormStatus.APPROVED);
    @Override
    @Transactional
    public void confirmIdentity(Account moderator, UUID accountId) {
        Account member = accountRepository.findById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        Instant now = Instant.now();
        CounterIdentityConfirmation confirmation = identityConfirmationRepository.findById(member.getId())
                .orElseGet(() -> CounterIdentityConfirmation.builder().accountId(member.getId()).build());

        confirmation.setConfirmedAt(now);
        confirmation.setExpiresAt(now.plus(IDENTITY_CONFIRMATION_TTL));
        confirmation.setConfirmedBy(moderator);

        identityConfirmationRepository.save(confirmation);
    }

    @Override
    @Transactional
    public void handover(Account moderator, CounterHandoverRequest request) {
        CounterHandoverContext context = parseContext(request.getContext());

        ensureIdentityConfirmed(request.getAccountId());

        UUID refId = parseRefId(request.getRefId());

        switch (context) {
            case ITEM_WITHDRAWAL -> handoverItemWithdrawal(request.getAccountId(), refId);
            case END_OF_TERM_TAKE_BACK -> handoverEndOfTermTakeBack(request.getAccountId(), refId);
            case INVALID_RETURN_PICKUP -> handoverInvalidReturnPickup(request.getAccountId(), refId);
        }
    }

    private CounterHandoverContext parseContext(String raw) {
        try {
            return CounterHandoverContext.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new AppException(ErrorCode.COUNTER_CONTEXT_INVALID);
        }
    }

    private UUID parseRefId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.COUNTER_RECORD_NOT_FOUND);
        }
    }

    private void ensureIdentityConfirmed(UUID accountId) {
        CounterIdentityConfirmation confirmation = identityConfirmationRepository.findById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.COUNTER_IDENTITY_NOT_CONFIRMED));

        if (confirmation.getExpiresAt().isBefore(Instant.now())) {
            throw new AppException(ErrorCode.COUNTER_IDENTITY_NOT_CONFIRMED);
        }
    }

    private void handoverItemWithdrawal(UUID accountId, UUID refId) {
        ItemWithdrawalRequest withdrawal = itemWithdrawalRequestRepository.findById(refId)
                .filter(r -> r.getMember().getId().equals(accountId))
                .filter(r -> r.getStatus() == ItemWithdrawalStatus.PENDING_PICKUP)
                .orElseThrow(() -> new AppException(ErrorCode.COUNTER_RECORD_NOT_FOUND));

        withdrawal.setStatus(ItemWithdrawalStatus.COMPLETED);
        withdrawal.setCompletedAt(Instant.now());
        itemWithdrawalRequestRepository.save(withdrawal);
    }

    private void handoverEndOfTermTakeBack(UUID accountId, UUID refId) {
        EndOfTermDecision decision = endOfTermDecisionRepository.findById(refId)
                .filter(d -> d.getProduct().getMember().getId().equals(accountId))
                .filter(d -> d.getDecision() == EndOfTermChoice.TAKE_BACK)
                .filter(d -> d.getTakenBackAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.COUNTER_RECORD_NOT_FOUND));

        decision.setTakenBackAt(Instant.now());
        endOfTermDecisionRepository.save(decision);
    }

    private void handoverInvalidReturnPickup(UUID accountId, UUID refId) {
        ReturnRequest returnRequest = returnRequestRepository.findById(refId)
                .filter(r -> r.getOrder().getBuyer().getId().equals(accountId))
                .filter(r -> r.getStatus() == ReturnRequestStatus.INVALID)
                .filter(r -> r.getInvalidReturnChoice() == InvalidReturnChoice.WAREHOUSE_PICKUP)
                .filter(r -> r.getPickedUpAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.COUNTER_RECORD_NOT_FOUND));

        returnRequest.setPickedUpAt(Instant.now());
        returnRequestRepository.save(returnRequest);
    }
    @Override
    @Transactional
    public List<MemberLookupItemResponse> lookup(CounterLookupRequest req) {
        String memberCode = trimToNull(req.getMemberCode());
        String phone = trimToNull(req.getPhone());
        String qrPayload = trimToNull(req.getQrPayload());

        int provided = (memberCode != null ? 1 : 0) + (phone != null ? 1 : 0) + (qrPayload != null ? 1 : 0);
        if (provided != 1) {
            throw new AppException(ErrorCode.LOOKUP_INVALID_INPUT);
        }

        List<MemberProfile> found;
        if (phone != null) {
            found = memberProfileRepository.findByAccount_Phone(phone);
        } else {
            // Giả định: qrPayload chứa đúng memberCode
            String code = memberCode != null ? memberCode : qrPayload;
            found = memberProfileRepository.findByMemberCode(code).map(List::of).orElse(List.of());
        }

        if (found.isEmpty()) {
            throw new AppException(ErrorCode.MEMBER_NOT_FOUND);
        }
        return found.stream().map(MemberLookupItemResponse::from).toList();
    }

    @Override
    @Transactional
    public MemberTasksResponse getTasks(UUID accountId) {
        if (!memberProfileRepository.existsById(accountId)) {
            throw new AppException(ErrorCode.MEMBER_NOT_FOUND);
        }

        List<MemberTasksResponse.PendingFormItem> forms = consignmentFormRepository
                .findByMember_IdAndStatusInOrderByCreatedAtAsc(accountId, OPEN_FORM_STATUSES)
                .stream()
                .map(f -> MemberTasksResponse.PendingFormItem.builder()
                        .id(f.getId())
                        .productName(f.getProductName())
                        .desiredPrice(f.getDesiredPrice())
                        .status(f.getStatus())
                        .createdAt(f.getCreatedAt())
                        .build())
                .toList();

        List<MemberTasksResponse.PendingWithdrawalItem> withdrawals = itemWithdrawalRequestRepository
                .findByMember_IdAndStatusOrderByCreatedAtAsc(accountId, ItemWithdrawalStatus.PENDING_PICKUP)
                .stream()
                .map(w -> MemberTasksResponse.PendingWithdrawalItem.builder()
                        .id(w.getId())
                        .productId(w.getProduct().getId())
                        .productName(w.getProduct().getName())
                        .feeCharged(w.getFeeCharged())
                        .pickupDeadline(w.getPickupDeadline())
                        .build())
                .toList();

        List<MemberTasksResponse.PendingEndOfTermItem> endOfTerm =
                endOfTermDecisionRepository.findUndecidedByMemberId(accountId).stream()
                        .map(d -> MemberTasksResponse.PendingEndOfTermItem.builder()
                                .id(d.getId())
                                .productId(d.getProduct().getId())
                                .productName(d.getProduct().getName())
                                .deadline(d.getDeadline())
                                .build())
                        .toList();

        return MemberTasksResponse.builder()
                .pendingForms(forms)
                .pendingWithdrawals(withdrawals)
                .pendingEndOfTerm(endOfTerm)
                .build();
    }

    private static String trimToNull(String s) {
        return Optional.ofNullable(s).map(String::trim).filter(v -> !v.isEmpty()).orElse(null);
    }
}
