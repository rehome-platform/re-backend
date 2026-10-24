package com.rehome.rehome.serviceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rehome.rehome.dto.request.CounterLookupRequest;
import com.rehome.rehome.dto.response.MemberLookupItemResponse;
import com.rehome.rehome.dto.response.MemberTasksResponse;
import com.rehome.rehome.entity.auth.MemberProfile;
import com.rehome.rehome.entity.enums.ConsignmentFormStatus;
import com.rehome.rehome.entity.enums.ItemWithdrawalStatus;
import com.rehome.rehome.entity.marketplace.ConsignmentForm;
import com.rehome.rehome.entity.marketplace.EndOfTermDecision;
import com.rehome.rehome.entity.marketplace.ItemWithdrawalRequest;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.ConsignmentFormRepository;
import com.rehome.rehome.repository.EndOfTermDecisionRepository;
import com.rehome.rehome.repository.ItemWithdrawalRequestRepository;
import com.rehome.rehome.repository.MemberProfileRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.rehome.rehome.serviceImpl.CounterServiceImpl;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CounterServiceImplTest {

    @Mock private MemberProfileRepository memberProfileRepository;
    @Mock private ConsignmentFormRepository consignmentFormRepository;
    @Mock private ItemWithdrawalRequestRepository itemWithdrawalRequestRepository;
    @Mock private EndOfTermDecisionRepository endOfTermDecisionRepository;

    @InjectMocks private CounterServiceImpl counterService;

    private static void assertAppError(ThrowingCallable callable, ErrorCode expected) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(
                        AppException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    private static MemberProfile profile(UUID accountId, String code) {
        return MemberProfile.builder()
                .accountId(accountId)
                .fullName("Tạ Thị Ngọc Anh")
                .avatarUrl("https://cdn.rehome.vn/uploads/avatar.jpg")
                .memberCode(code)
                .build();
    }

    private static CounterLookupRequest request(String memberCode, String phone, String qrPayload) {
        CounterLookupRequest r = new CounterLookupRequest();
        r.setMemberCode(memberCode);
        r.setPhone(phone);
        r.setQrPayload(qrPayload);
        return r;
    }

    @Nested
    @DisplayName("API 25 - lookup")
    class Lookup {

        @Test
        @DisplayName("Tra theo phone: trả về danh sách có Member")
        void byPhone_found() {
            UUID id = UUID.randomUUID();
            when(memberProfileRepository.findByAccount_Phone("0912345678"))
                    .thenReturn(List.of(profile(id, "MB-000123")));

            List<MemberLookupItemResponse> result =
                    counterService.lookup(request(null, "0912345678", null));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getAccountId()).isEqualTo(id);
            assertThat(result.get(0).getFullName()).isEqualTo("Tạ Thị Ngọc Anh");
            assertThat(result.get(0).getAvatarUrl()).isEqualTo("https://cdn.rehome.vn/uploads/avatar.jpg");
            assertThat(result.get(0).getMemberCode()).isEqualTo("MB-000123");
        }

        @Test
        @DisplayName("Tra theo memberCode: trả về đúng 1 phần tử")
        void byMemberCode_found() {
            UUID id = UUID.randomUUID();
            when(memberProfileRepository.findByMemberCode("MB-000123"))
                    .thenReturn(Optional.of(profile(id, "MB-000123")));

            List<MemberLookupItemResponse> result =
                    counterService.lookup(request("MB-000123", null, null));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getMemberCode()).isEqualTo("MB-000123");
        }

        @Test
        @DisplayName("Tra theo qrPayload: coi payload là memberCode")
        void byQrPayload_usesMemberCodeLookup() {
            UUID id = UUID.randomUUID();
            when(memberProfileRepository.findByMemberCode("MB-000123"))
                    .thenReturn(Optional.of(profile(id, "MB-000123")));

            List<MemberLookupItemResponse> result =
                    counterService.lookup(request(null, null, "MB-000123"));

            assertThat(result).hasSize(1);
            verify(memberProfileRepository).findByMemberCode("MB-000123");
        }

        @Test
        @DisplayName("Input được trim trước khi tra")
        void input_isTrimmed() {
            when(memberProfileRepository.findByAccount_Phone("0912345678"))
                    .thenReturn(List.of(profile(UUID.randomUUID(), "MB-000123")));

            counterService.lookup(request(null, "  0912345678  ", null));

            verify(memberProfileRepository).findByAccount_Phone("0912345678");
        }

        @Test
        @DisplayName("Không gửi field nào: 400 LOOKUP_INVALID_INPUT")
        void noneProvided_throws() {
            assertAppError(() -> counterService.lookup(request(null, null, null)), ErrorCode.LOOKUP_INVALID_INPUT);
            verifyNoInteractions(memberProfileRepository);
        }

        @Test
        @DisplayName("Field rỗng hoặc toàn khoảng trắng được coi như không gửi")
        void blankFields_treatedAsMissing() {
            assertAppError(() -> counterService.lookup(request("", "   ", null)), ErrorCode.LOOKUP_INVALID_INPUT);
            verifyNoInteractions(memberProfileRepository);
        }

        @Test
        @DisplayName("Gửi 2 field: 400 LOOKUP_INVALID_INPUT")
        void twoProvided_throws() {
            assertAppError(
                    () -> counterService.lookup(request("MB-000123", "0912345678", null)),
                    ErrorCode.LOOKUP_INVALID_INPUT);
            verifyNoInteractions(memberProfileRepository);
        }

        @Test
        @DisplayName("Gửi đủ 3 field: 400 LOOKUP_INVALID_INPUT")
        void threeProvided_throws() {
            assertAppError(
                    () -> counterService.lookup(request("MB-000123", "0912345678", "MB-000123")),
                    ErrorCode.LOOKUP_INVALID_INPUT);
        }

        @Test
        @DisplayName("Phone không có Member: 404 MEMBER_NOT_FOUND")
        void byPhone_notFound() {
            when(memberProfileRepository.findByAccount_Phone("0900000000")).thenReturn(List.of());

            assertAppError(
                    () -> counterService.lookup(request(null, "0900000000", null)), ErrorCode.MEMBER_NOT_FOUND);
        }

        @Test
        @DisplayName("memberCode không tồn tại: 404 MEMBER_NOT_FOUND")
        void byMemberCode_notFound() {
            when(memberProfileRepository.findByMemberCode("MB-999999")).thenReturn(Optional.empty());

            assertAppError(
                    () -> counterService.lookup(request("MB-999999", null, null)), ErrorCode.MEMBER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("API 27 - getTasks")
    class GetTasks {

        @Test
        @DisplayName("Member không tồn tại: 404 và không query các bảng việc")
        void memberNotFound() {
            UUID id = UUID.randomUUID();
            when(memberProfileRepository.existsById(id)).thenReturn(false);

            assertAppError(() -> counterService.getTasks(id), ErrorCode.MEMBER_NOT_FOUND);

            verifyNoInteractions(
                    consignmentFormRepository, itemWithdrawalRequestRepository, endOfTermDecisionRepository);
        }

        @Test
        @DisplayName("Member chưa có việc nào: trả về 3 mảng rỗng")
        void noTasks_returnsEmptyLists() {
            UUID id = UUID.randomUUID();
            when(memberProfileRepository.existsById(id)).thenReturn(true);
            when(consignmentFormRepository.findByMember_IdAndStatusInOrderByCreatedAtAsc(eq(id), eq(openStatuses())))
                    .thenReturn(List.of());
            when(itemWithdrawalRequestRepository.findByMember_IdAndStatusOrderByCreatedAtAsc(
                    id, ItemWithdrawalStatus.PENDING_PICKUP))
                    .thenReturn(List.of());
            when(endOfTermDecisionRepository.findUndecidedByMemberId(id)).thenReturn(List.of());

            MemberTasksResponse result = counterService.getTasks(id);

            assertThat(result.getPendingForms()).isEmpty();
            assertThat(result.getPendingWithdrawals()).isEmpty();
            assertThat(result.getPendingEndOfTerm()).isEmpty();
        }

        @Test
        @DisplayName("Map đúng dữ liệu của cả 3 nhóm việc")
        void mapsAllThreeGroups() {
            UUID memberId = UUID.randomUUID();
            Instant now = Instant.parse("2026-10-13T08:00:00Z");
            Product product = Product.builder().id("Q7-AK-000845").name("Áo khoác denim").build();

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
                    .feeCharged(new BigDecimal("30000"))
                    .pickupDeadline(now.plusSeconds(3600))
                    .build();
            EndOfTermDecision decision = EndOfTermDecision.builder()
                    .id(UUID.randomUUID())
                    .product(product)
                    .deadline(now.plusSeconds(7200))
                    .build();

            when(memberProfileRepository.existsById(memberId)).thenReturn(true);
            when(consignmentFormRepository.findByMember_IdAndStatusInOrderByCreatedAtAsc(
                    eq(memberId), eq(openStatuses())))
                    .thenReturn(List.of(form));
            when(itemWithdrawalRequestRepository.findByMember_IdAndStatusOrderByCreatedAtAsc(
                    memberId, ItemWithdrawalStatus.PENDING_PICKUP))
                    .thenReturn(List.of(withdrawal));
            when(endOfTermDecisionRepository.findUndecidedByMemberId(memberId)).thenReturn(List.of(decision));

            MemberTasksResponse result = counterService.getTasks(memberId);

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

        @Test
        @DisplayName("Form được lấy với 2 trạng thái PENDING và APPROVED")
        void formsQueriedWithPendingAndApproved() {
            UUID id = UUID.randomUUID();
            when(memberProfileRepository.existsById(id)).thenReturn(true);
            when(consignmentFormRepository.findByMember_IdAndStatusInOrderByCreatedAtAsc(any(), any()))
                    .thenReturn(List.of());
            when(itemWithdrawalRequestRepository.findByMember_IdAndStatusOrderByCreatedAtAsc(any(), any()))
                    .thenReturn(List.of());
            when(endOfTermDecisionRepository.findUndecidedByMemberId(any())).thenReturn(List.of());

            counterService.getTasks(id);

            verify(consignmentFormRepository)
                    .findByMember_IdAndStatusInOrderByCreatedAtAsc(id, openStatuses());
        }

        private List<ConsignmentFormStatus> openStatuses() {
            return List.of(ConsignmentFormStatus.PENDING, ConsignmentFormStatus.APPROVED);
        }
    }
}