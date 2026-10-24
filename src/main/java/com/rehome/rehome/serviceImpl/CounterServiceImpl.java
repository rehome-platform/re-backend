package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.request.CounterLookupRequest;
import com.rehome.rehome.dto.response.MemberLookupItemResponse;
import com.rehome.rehome.dto.response.MemberTasksResponse;
import com.rehome.rehome.entity.auth.MemberProfile;
import com.rehome.rehome.entity.enums.ConsignmentFormStatus;
import com.rehome.rehome.entity.enums.ItemWithdrawalStatus;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.ConsignmentFormRepository;
import com.rehome.rehome.repository.EndOfTermDecisionRepository;
import com.rehome.rehome.repository.ItemWithdrawalRequestRepository;
import com.rehome.rehome.repository.MemberProfileRepository;
import com.rehome.rehome.service.CounterService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CounterServiceImpl implements CounterService {

    // Form còn dang dở với Member: chờ duyệt, hoặc đã duyệt và đang chờ mang hàng đến quầy
    private static final List<ConsignmentFormStatus> OPEN_FORM_STATUSES =
            List.of(ConsignmentFormStatus.PENDING, ConsignmentFormStatus.APPROVED);

    private final MemberProfileRepository memberProfileRepository;
    private final ConsignmentFormRepository consignmentFormRepository;
    private final ItemWithdrawalRequestRepository itemWithdrawalRequestRepository;
    private final EndOfTermDecisionRepository endOfTermDecisionRepository;

    @Override
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
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