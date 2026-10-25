package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.request.CounterHandoverRequest;
import com.rehome.rehome.dto.request.CounterLookupRequest;
import com.rehome.rehome.dto.response.MemberLookupItemResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.MemberProfile;
import com.rehome.rehome.entity.counter.CounterIdentityConfirmation;
import com.rehome.rehome.entity.enums.CounterHandoverContext;
import com.rehome.rehome.entity.enums.EndOfTermChoice;
import com.rehome.rehome.entity.enums.InvalidReturnChoice;
import com.rehome.rehome.entity.enums.ItemWithdrawalStatus;
import com.rehome.rehome.entity.enums.ReturnRequestStatus;
import com.rehome.rehome.entity.marketplace.EndOfTermDecision;
import com.rehome.rehome.entity.marketplace.ItemWithdrawalRequest;
import com.rehome.rehome.entity.marketplace.ReturnRequest;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.CounterIdentityConfirmationRepository;
import com.rehome.rehome.repository.EndOfTermDecisionRepository;
import com.rehome.rehome.repository.ItemWithdrawalRequestRepository;
import com.rehome.rehome.repository.MemberProfileRepository;
import com.rehome.rehome.repository.ReturnRequestRepository;
import com.rehome.rehome.service.CounterService;
import com.rehome.rehome.util.MemberQrPayload;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CounterServiceImpl implements CounterService {

    private static final Duration IDENTITY_CONFIRMATION_TTL = Duration.ofMinutes(15);

    private final AccountRepository accountRepository;
    private final CounterIdentityConfirmationRepository identityConfirmationRepository;
    private final ItemWithdrawalRequestRepository itemWithdrawalRequestRepository;
    private final EndOfTermDecisionRepository endOfTermDecisionRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final MemberProfileRepository memberProfileRepository;

    // API 25 (F-108, F-110): tra Member theo mã, số điện thoại hoặc QR.
    // Quét QR chỉ để mở đúng hồ sơ, không thay cho việc xác nhận danh tính (API 26).
    @Override
    public List<MemberLookupItemResponse> lookup(CounterLookupRequest request) {
        String memberCode = normalizeMemberCode(request.getMemberCode());
        String phone = trimToNull(request.getPhone());
        String qrPayload = trimToNull(request.getQrPayload());

        int provided = (memberCode != null ? 1 : 0) + (phone != null ? 1 : 0) + (qrPayload != null ? 1 : 0);
        if (provided != 1) {
            throw new AppException(ErrorCode.LOOKUP_INVALID_INPUT);
        }

        if (qrPayload != null) {
            memberCode = normalizeMemberCode(MemberQrPayload.decodeMemberCode(qrPayload));
        }

        // member_profiles chỉ có dòng cho Role.MEMBER nên không cần lọc theo role.
        // Số điện thoại có thể không duy nhất -> trả danh sách để Moderator chọn.
        List<MemberProfile> profiles = memberCode != null
                ? memberProfileRepository.findByMemberCode(memberCode).map(List::of).orElseGet(List::of)
                : memberProfileRepository.findByAccount_Phone(phone);

        if (profiles.isEmpty()) {
            throw new AppException(ErrorCode.MEMBER_NOT_FOUND);
        }

        return profiles.stream().map(MemberLookupItemResponse::from).toList();
    }

    private String normalizeMemberCode(String raw) {
        String code = trimToNull(raw);
        return code == null ? null : code.toUpperCase();
    }

    private String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

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
}
