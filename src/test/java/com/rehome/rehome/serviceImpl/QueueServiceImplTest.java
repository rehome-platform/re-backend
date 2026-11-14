package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.response.QueueClaimResponse;
import com.rehome.rehome.dto.response.QueueItemResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.MemberProfile;
import com.rehome.rehome.entity.auth.OrganizationLegalChangeRequest;
import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.entity.enums.QueueType;
import com.rehome.rehome.entity.marketplace.ConsignmentForm;
import com.rehome.rehome.entity.marketplace.Donation;
import com.rehome.rehome.entity.marketplace.ItemWithdrawalRequest;
import com.rehome.rehome.entity.marketplace.Order;
import com.rehome.rehome.entity.marketplace.OrderItem;
import com.rehome.rehome.entity.marketplace.ProductPhoto;
import com.rehome.rehome.entity.marketplace.QueueItem;
import com.rehome.rehome.entity.marketplace.ReturnRequest;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.mapper.OrganizationLegalChangeMapper;
import com.rehome.rehome.repository.ConsignmentFormRepository;
import com.rehome.rehome.repository.DonationRepository;
import com.rehome.rehome.repository.ItemWithdrawalRequestRepository;
import com.rehome.rehome.repository.MemberProfileRepository;
import com.rehome.rehome.repository.OrderItemRepository;
import com.rehome.rehome.repository.OrderRepository;
import com.rehome.rehome.repository.OrganizationLegalChangeRequestRepository;
import com.rehome.rehome.repository.OrganizationProfileRepository;
import com.rehome.rehome.repository.ProductPhotoRepository;
import com.rehome.rehome.repository.QueueItemRepository;
import com.rehome.rehome.repository.ReturnRequestRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lưu ý giả định (chỉnh lại nếu code thật khác):
 * - AppException có field/getter "errorCode".
 * - QueueItemResponse có các static factory: from, fromPacking, fromLegalChange, fromReturn,
 *   fromConsignmentForm, fromOrganization, fromWithdrawal, fromDonationPickup (được mock tĩnh).
 * - OrganizationLegalChangeMapper.LegalChangeSummary có thể mock được (record -> cần mockito-inline, mặc định Mockito 5).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QueueServiceImplTest {

    @Mock private QueueItemRepository queueItemRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private OrganizationLegalChangeRequestRepository organizationLegalChangeRequestRepository;
    @Mock private OrganizationLegalChangeMapper organizationLegalChangeMapper;
    @Mock private ReturnRequestRepository returnRequestRepository;
    @Mock private MemberProfileRepository memberProfileRepository;
    @Mock private ConsignmentFormRepository consignmentFormRepository;
    @Mock private OrganizationProfileRepository organizationProfileRepository;
    @Mock private ItemWithdrawalRequestRepository itemWithdrawalRequestRepository;
    @Mock private ProductPhotoRepository productPhotoRepository;
    @Mock private DonationRepository donationRepository;

    @Mock private com.rehome.rehome.component.AuditLogger auditLogger;
    @org.mockito.Spy private com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
    @InjectMocks
    private QueueServiceImpl service;

    private MockedStatic<QueueItemResponse> responses;

    private final UUID warehouseId = UUID.randomUUID();
    private Warehouse warehouse;
    private Account moderator;

    @BeforeEach
    void setUp() {
        warehouse = mock(Warehouse.class);
        when(warehouse.getId()).thenReturn(warehouseId);

        moderator = mock(Account.class);
        when(moderator.getId()).thenReturn(UUID.randomUUID());
        when(moderator.getWarehouse()).thenReturn(warehouse);

        responses = mockStatic(QueueItemResponse.class);
    }

    @AfterEach
    void tearDown() {
        responses.close();
    }

    // ------------------------------------------------------------------ helpers

    private QueueItem queueItem(QueueType type, String refId) {
        QueueItem q = mock(QueueItem.class);
        when(q.getType()).thenReturn(type);
        when(q.getRefId()).thenReturn(refId);
        return q;
    }

    private void assertAppError(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run)
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", code);
    }

    // ================================================================ list

    @Nested
    @DisplayName("list")
    class ListTests {

        @Test
        void moderatorWithoutWarehouse_throws() {
            when(moderator.getWarehouse()).thenReturn(null);
            assertAppError(() -> service.list(null, moderator), ErrorCode.MODERATOR_NO_WAREHOUSE);
        }

        @Test
        void nullOrBlankType_loadsAllVisible() {
            when(queueItemRepository.findAllVisible(warehouse)).thenReturn(List.of());

            assertThat(service.list(null, moderator)).isEmpty();
            assertThat(service.list("   ", moderator)).isEmpty();

            verify(queueItemRepository, times(2)).findAllVisible(warehouse);
            verify(queueItemRepository, never()).findVisibleByType(any(), any());
        }

        @Test
        void invalidType_throws() {
            assertAppError(() -> service.list("NOT_A_TYPE", moderator), ErrorCode.INVALID_QUEUE_TYPE);
        }

        @Test
        void validType_trimmedAndFiltered() {
            when(queueItemRepository.findVisibleByType(QueueType.PACKING, warehouse)).thenReturn(List.of());

            service.list("  PACKING ", moderator);

            verify(queueItemRepository).findVisibleByType(QueueType.PACKING, warehouse);
            verify(returnRequestRepository, never()).findPendingIdsByWarehouse(any());
        }

        @Test
        void returnTab_noPendingRequests_doesNotEnqueue() {
            when(returnRequestRepository.findPendingIdsByWarehouse(warehouse)).thenReturn(List.of());
            when(queueItemRepository.findVisibleByType(QueueType.RETURN, warehouse)).thenReturn(List.of());

            service.list("RETURN", moderator);

            verify(queueItemRepository, never()).save(any());
            verify(queueItemRepository, never()).findByTypesAndRefIds(anyList(), anyList());
        }

        @Test
        void returnTab_enqueuesOnlyMissingPendingRequests() {
            UUID alreadyQueued = UUID.randomUUID();
            UUID missing = UUID.randomUUID();
            when(returnRequestRepository.findPendingIdsByWarehouse(warehouse))
                    .thenReturn(List.of(alreadyQueued, missing));

            QueueItem existing = queueItem(QueueType.RETURN, alreadyQueued.toString());
            when(queueItemRepository.findByTypesAndRefIds(anyList(), anyList())).thenReturn(List.of(existing));
            when(queueItemRepository.findByTypeAndRefId(QueueType.RETURN, missing.toString()))
                    .thenReturn(Optional.empty());
            when(queueItemRepository.save(any(QueueItem.class))).thenAnswer(inv -> inv.getArgument(0));
            when(queueItemRepository.findVisibleByType(QueueType.RETURN, warehouse)).thenReturn(List.of());

            service.list("RETURN", moderator);

            ArgumentCaptor<QueueItem> captor = ArgumentCaptor.forClass(QueueItem.class);
            verify(queueItemRepository, times(1)).save(captor.capture());
            assertThat(captor.getValue().getRefId()).isEqualTo(missing.toString());
            assertThat(captor.getValue().getType()).isEqualTo(QueueType.RETURN);
            assertThat(captor.getValue().getWarehouse()).isSameAs(warehouse);
        }
    }

    // ================================================================ toResponses (qua list)

    @Nested
    @DisplayName("list -> toResponses")
    class ToResponses {

        private void listAll(QueueItem... items) {
            when(queueItemRepository.findAllVisible(warehouse)).thenReturn(List.of(items));
        }

        @Test
        void unknownTypeOrInvalidRefId_fallsBackToPlainFrom() {
            QueueItem plain = queueItem(QueueType.PACKING, "not-a-uuid");
            QueueItemResponse fallback = mock(QueueItemResponse.class);
            responses.when(() -> QueueItemResponse.from(plain)).thenReturn(fallback);
            listAll(plain);

            List<QueueItemResponse> res = service.list(null, moderator);

            assertThat(res).containsExactly(fallback);
            verify(orderRepository, never()).findAllById(any());
        }

        @Test
        void packing_enrichedWithOrderAndItemCount() {
            UUID orderId = UUID.randomUUID();
            QueueItem q = queueItem(QueueType.PACKING, orderId.toString());

            Order order = mock(Order.class);
            when(order.getId()).thenReturn(orderId);
            when(order.getCreatedAt()).thenReturn(Instant.now().minus(Duration.ofMinutes(45)));
            when(orderRepository.findAllById(anyList())).thenReturn(List.of(order));

            OrderItem oi1 = mock(OrderItem.class, RETURNS_DEEP_STUBS);
            when(oi1.getOrder().getId()).thenReturn(orderId);
            OrderItem oi2 = mock(OrderItem.class, RETURNS_DEEP_STUBS);
            when(oi2.getOrder().getId()).thenReturn(orderId);
            when(orderItemRepository.findAllByOrder_IdInOrderByIdAsc(anyList())).thenReturn(List.of(oi1, oi2));

            QueueItemResponse packing = mock(QueueItemResponse.class);
            responses.when(() -> QueueItemResponse.fromPacking(eq(q), eq(order), eq(2), anyLong()))
                    .thenReturn(packing);
            listAll(q);

            List<QueueItemResponse> res = service.list(null, moderator);

            assertThat(res).containsExactly(packing);
            ArgumentCaptor<Long> minutes = ArgumentCaptor.forClass(Long.class);
            responses.verify(() -> QueueItemResponse.fromPacking(eq(q), eq(order), eq(2), minutes.capture()));
            assertThat(minutes.getValue()).isBetween(44L, 46L);
        }

        @Test
        void packing_orderMissing_fallsBackToFrom() {
            UUID orderId = UUID.randomUUID();
            QueueItem q = queueItem(QueueType.PACKING, orderId.toString());
            when(orderRepository.findAllById(anyList())).thenReturn(List.of());
            when(orderItemRepository.findAllByOrder_IdInOrderByIdAsc(anyList())).thenReturn(List.of());
            QueueItemResponse fallback = mock(QueueItemResponse.class);
            responses.when(() -> QueueItemResponse.from(q)).thenReturn(fallback);
            listAll(q);

            assertThat(service.list(null, moderator)).containsExactly(fallback);
        }

        @Test
        void legalChange_enrichedWithSummary() {
            UUID requestId = UUID.randomUUID();
            QueueItem q = queueItem(QueueType.LEGAL_CHANGE, requestId.toString());

            OrganizationLegalChangeRequest request = mock(OrganizationLegalChangeRequest.class);
            when(request.getId()).thenReturn(requestId);
            when(organizationLegalChangeRequestRepository.findAllById(anyList())).thenReturn(List.of(request));

            OrganizationLegalChangeMapper.LegalChangeSummary summary =
                    mock(OrganizationLegalChangeMapper.LegalChangeSummary.class);
            when(summary.fieldChanged()).thenReturn("taxCode");
            when(summary.oldValue()).thenReturn("111");
            when(summary.newValue()).thenReturn("222");
            when(organizationLegalChangeMapper.summarize(any(), any())).thenReturn(summary);

            QueueItemResponse legal = mock(QueueItemResponse.class);
            responses.when(() -> QueueItemResponse.fromLegalChange(q, request, "taxCode", "111", "222"))
                    .thenReturn(legal);
            listAll(q);

            assertThat(service.list(null, moderator)).containsExactly(legal);
        }

        @Test
        void legalChange_requestMissing_fallsBackToFrom() {
            UUID requestId = UUID.randomUUID();
            QueueItem q = queueItem(QueueType.LEGAL_CHANGE, requestId.toString());
            when(organizationLegalChangeRequestRepository.findAllById(anyList())).thenReturn(List.of());
            QueueItemResponse fallback = mock(QueueItemResponse.class);
            responses.when(() -> QueueItemResponse.from(q)).thenReturn(fallback);
            listAll(q);

            assertThat(service.list(null, moderator)).containsExactly(fallback);
            verify(organizationLegalChangeMapper, never()).summarize(any(), any());
        }

        @Test
        void return_enrichedWithSellerAndBuyerNames() {
            UUID returnId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            UUID buyerId = UUID.randomUUID();
            UUID sellerId = UUID.randomUUID();
            QueueItem q = queueItem(QueueType.RETURN, returnId.toString());

            ReturnRequest rr = mock(ReturnRequest.class, RETURNS_DEEP_STUBS);
            when(rr.getId()).thenReturn(returnId);
            when(rr.getOrder().getId()).thenReturn(orderId);
            when(returnRequestRepository.findAllById(anyList())).thenReturn(List.of(rr));

            Order order = mock(Order.class, RETURNS_DEEP_STUBS);
            when(order.getId()).thenReturn(orderId);
            when(order.getBuyer().getId()).thenReturn(buyerId);
            when(orderRepository.findAllById(anyList())).thenReturn(List.of(order));

            OrderItem oi = mock(OrderItem.class, RETURNS_DEEP_STUBS);
            when(oi.getOrder().getId()).thenReturn(orderId);
            when(oi.getProduct().getMember().getId()).thenReturn(sellerId);
            when(orderItemRepository.findAllByOrder_IdInOrderByIdAsc(anyList())).thenReturn(List.of(oi));

            MemberProfile buyer = mock(MemberProfile.class, RETURNS_DEEP_STUBS);
            when(buyer.getAccount().getId()).thenReturn(buyerId);
            when(buyer.getFullName()).thenReturn("Người Mua");
            MemberProfile seller = mock(MemberProfile.class, RETURNS_DEEP_STUBS);
            when(seller.getAccount().getId()).thenReturn(sellerId);
            when(seller.getFullName()).thenReturn("Người Bán");
            when(memberProfileRepository.findAllByAccount_IdIn(anyList())).thenReturn(List.of(buyer, seller));

            QueueItemResponse ret = mock(QueueItemResponse.class);
            responses.when(() -> QueueItemResponse.fromReturn(q, order, "Người Bán", "Người Mua"))
                    .thenReturn(ret);
            listAll(q);

            assertThat(service.list(null, moderator)).containsExactly(ret);
        }

        @Test
        void return_orderNotFound_fallsBackToFrom() {
            UUID returnId = UUID.randomUUID();
            QueueItem q = queueItem(QueueType.RETURN, returnId.toString());

            ReturnRequest rr = mock(ReturnRequest.class, RETURNS_DEEP_STUBS);
            when(rr.getId()).thenReturn(returnId);
            when(rr.getOrder().getId()).thenReturn(UUID.randomUUID());
            when(returnRequestRepository.findAllById(anyList())).thenReturn(List.of(rr));
            when(orderRepository.findAllById(anyList())).thenReturn(List.of());
            when(orderItemRepository.findAllByOrder_IdInOrderByIdAsc(anyList())).thenReturn(List.of());

            QueueItemResponse fallback = mock(QueueItemResponse.class);
            responses.when(() -> QueueItemResponse.from(q)).thenReturn(fallback);
            listAll(q);

            assertThat(service.list(null, moderator)).containsExactly(fallback);
        }

        @Test
        void formReview_andInspection_shareFormLookup() {
            UUID formId = UUID.randomUUID();
            UUID memberAccountId = UUID.randomUUID();
            QueueItem review = queueItem(QueueType.FORM_REVIEW, formId.toString());
            QueueItem inspection = queueItem(QueueType.INSPECTION, formId.toString());

            ConsignmentForm form = mock(ConsignmentForm.class, RETURNS_DEEP_STUBS);
            when(form.getId()).thenReturn(formId);
            when(form.getMember().getId()).thenReturn(memberAccountId);
            when(consignmentFormRepository.findAllById(anyList())).thenReturn(List.of(form));

            MemberProfile profile = mock(MemberProfile.class);
            when(profile.getAccountId()).thenReturn(memberAccountId);
            when(profile.getFullName()).thenReturn("Nguyễn Văn A");
            when(memberProfileRepository.findAllByAccount_IdIn(anyList())).thenReturn(List.of(profile));

            QueueItemResponse r1 = mock(QueueItemResponse.class);
            QueueItemResponse r2 = mock(QueueItemResponse.class);
            responses.when(() -> QueueItemResponse.fromConsignmentForm(review, form, "Nguyễn Văn A")).thenReturn(r1);
            responses.when(() -> QueueItemResponse.fromConsignmentForm(inspection, form, "Nguyễn Văn A")).thenReturn(r2);
            listAll(review, inspection);

            assertThat(service.list(null, moderator)).containsExactly(r1, r2);
            // refId trùng được gộp distinct -> chỉ 1 lần truy vấn form
            verify(consignmentFormRepository, times(1)).findAllById(anyList());
        }

        @Test
        void orgVerification_enrichedWithOrganization() {
            UUID orgId = UUID.randomUUID();
            QueueItem q = queueItem(QueueType.ORG_VERIFICATION, orgId.toString());

            OrganizationProfile org = mock(OrganizationProfile.class);
            when(org.getAccountId()).thenReturn(orgId);
            when(organizationProfileRepository.findAllById(anyList())).thenReturn(List.of(org));

            QueueItemResponse orgResponse = mock(QueueItemResponse.class);
            responses.when(() -> QueueItemResponse.fromOrganization(q, org)).thenReturn(orgResponse);
            listAll(q);

            assertThat(service.list(null, moderator)).containsExactly(orgResponse);
        }

        @Test
        void orgVerification_notFound_fallsBackToFrom() {
            UUID orgId = UUID.randomUUID();
            QueueItem q = queueItem(QueueType.ORG_VERIFICATION, orgId.toString());
            when(organizationProfileRepository.findAllById(anyList())).thenReturn(List.of());
            QueueItemResponse fallback = mock(QueueItemResponse.class);
            responses.when(() -> QueueItemResponse.from(q)).thenReturn(fallback);
            listAll(q);

            assertThat(service.list(null, moderator)).containsExactly(fallback);
        }

        @Test
        void withdrawal_enrichedWithMemberAndFirstPhoto() {
            UUID withdrawalId = UUID.randomUUID();
            UUID memberAccountId = UUID.randomUUID();
            QueueItem q = queueItem(QueueType.WITHDRAWAL, withdrawalId.toString());

            ItemWithdrawalRequest w = mock(ItemWithdrawalRequest.class, RETURNS_DEEP_STUBS);
            when(w.getId()).thenReturn(withdrawalId);
            when(w.getMember().getId()).thenReturn(memberAccountId);
            when(w.getProduct().getId()).thenReturn("P1");
            when(itemWithdrawalRequestRepository.findAllWithProductByIdIn(anyList())).thenReturn(List.of(w));

            MemberProfile profile = mock(MemberProfile.class);
            when(profile.getAccountId()).thenReturn(memberAccountId);
            when(profile.getFullName()).thenReturn("Nguyễn Văn A");
            when(profile.getMemberCode()).thenReturn("MB-001");
            when(memberProfileRepository.findAllByAccount_IdIn(anyList())).thenReturn(List.of(profile));

            ProductPhoto first = mock(ProductPhoto.class, RETURNS_DEEP_STUBS);
            when(first.getProduct().getId()).thenReturn("P1");
            when(first.getImageUrl()).thenReturn("first.jpg");
            ProductPhoto second = mock(ProductPhoto.class, RETURNS_DEEP_STUBS);
            when(second.getProduct().getId()).thenReturn("P1");
            when(second.getImageUrl()).thenReturn("second.jpg");
            when(productPhotoRepository.findByProduct_IdInOrderByDisplayOrderAscIdAsc(anyList()))
                    .thenReturn(List.of(first, second));

            QueueItemResponse wr = mock(QueueItemResponse.class);
            responses.when(() -> QueueItemResponse.fromWithdrawal(q, w, "Nguyễn Văn A", "MB-001", "first.jpg"))
                    .thenReturn(wr);
            listAll(q);

            assertThat(service.list(null, moderator)).containsExactly(wr);
        }

        @Test
        void withdrawal_memberProfileMissing_passesNullNameAndCode() {
            UUID withdrawalId = UUID.randomUUID();
            QueueItem q = queueItem(QueueType.WITHDRAWAL, withdrawalId.toString());

            ItemWithdrawalRequest w = mock(ItemWithdrawalRequest.class, RETURNS_DEEP_STUBS);
            when(w.getId()).thenReturn(withdrawalId);
            when(w.getMember().getId()).thenReturn(UUID.randomUUID());
            when(w.getProduct().getId()).thenReturn("P1");
            when(itemWithdrawalRequestRepository.findAllWithProductByIdIn(anyList())).thenReturn(List.of(w));
            when(memberProfileRepository.findAllByAccount_IdIn(anyList())).thenReturn(List.of());
            when(productPhotoRepository.findByProduct_IdInOrderByDisplayOrderAscIdAsc(anyList()))
                    .thenReturn(List.of());

            QueueItemResponse wr = mock(QueueItemResponse.class);
            responses.when(() -> QueueItemResponse.fromWithdrawal(q, w, null, null, null)).thenReturn(wr);
            listAll(q);

            assertThat(service.list(null, moderator)).containsExactly(wr);
        }

        @Test
        void donationPickup_enrichedWithDonation() {
            UUID donationId = UUID.randomUUID();
            QueueItem q = queueItem(QueueType.DONATION_PICKUP, donationId.toString());

            Donation donation = mock(Donation.class);
            when(donation.getId()).thenReturn(donationId);
            when(donationRepository.findAllWithDetailsByIdIn(anyList())).thenReturn(List.of(donation));

            QueueItemResponse dr = mock(QueueItemResponse.class);
            responses.when(() -> QueueItemResponse.fromDonationPickup(q, donation)).thenReturn(dr);
            listAll(q);

            assertThat(service.list(null, moderator)).containsExactly(dr);
        }

        @Test
        void mixedTypes_keepOriginalOrder() {
            UUID donationId = UUID.randomUUID();
            QueueItem donationQ = queueItem(QueueType.DONATION_PICKUP, donationId.toString());
            QueueItem plainQ = queueItem(QueueType.PACKING, "bad-uuid");

            Donation donation = mock(Donation.class);
            when(donation.getId()).thenReturn(donationId);
            when(donationRepository.findAllWithDetailsByIdIn(anyList())).thenReturn(List.of(donation));

            QueueItemResponse dr = mock(QueueItemResponse.class);
            QueueItemResponse plain = mock(QueueItemResponse.class);
            responses.when(() -> QueueItemResponse.fromDonationPickup(donationQ, donation)).thenReturn(dr);
            responses.when(() -> QueueItemResponse.from(plainQ)).thenReturn(plain);
            listAll(plainQ, donationQ);

            assertThat(service.list(null, moderator)).containsExactly(plain, dr);
        }

        @Test
        void noItems_noBatchQueries() {
            listAll();

            assertThat(service.list(null, moderator)).isEmpty();

            verify(orderRepository, never()).findAllById(any());
            verify(consignmentFormRepository, never()).findAllById(any());
            verify(donationRepository, never()).findAllWithDetailsByIdIn(anyList());
            verify(itemWithdrawalRequestRepository, never()).findAllWithProductByIdIn(anyList());
        }
    }

    // ================================================================ claim

    @Nested
    @DisplayName("claim")
    class Claim {

        @Test
        void moderatorWithoutWarehouse_throws() {
            when(moderator.getWarehouse()).thenReturn(null);
            assertAppError(() -> service.claim("Q1", moderator), ErrorCode.MODERATOR_NO_WAREHOUSE);
        }

        @Test
        void itemNotFound_throws() {
            when(queueItemRepository.findById("Q1")).thenReturn(Optional.empty());
            assertAppError(() -> service.claim("Q1", moderator), ErrorCode.QUEUE_ITEM_NOT_FOUND);
        }

        @Test
        void itemOfAnotherWarehouse_treatedAsNotFound() {
            QueueItem item = mock(QueueItem.class, RETURNS_DEEP_STUBS);
            when(item.getWarehouse().getId()).thenReturn(UUID.randomUUID());
            when(queueItemRepository.findById("Q1")).thenReturn(Optional.of(item));

            assertAppError(() -> service.claim("Q1", moderator), ErrorCode.QUEUE_ITEM_NOT_FOUND);
            verify(queueItemRepository, never()).claimIfFree(any(), any(), any());
        }

        @Test
        void alreadyClaimed_throws() {
            QueueItem item = mock(QueueItem.class);
            when(item.getWarehouse()).thenReturn(warehouse);
            when(queueItemRepository.findById("Q1")).thenReturn(Optional.of(item));
            when(queueItemRepository.claimIfFree(eq("Q1"), eq(moderator), any(Instant.class))).thenReturn(0);

            assertAppError(() -> service.claim("Q1", moderator), ErrorCode.QUEUE_ITEM_ALREADY_CLAIMED);
        }

        @Test
        void packingTask_success_writesClaimAuditLog() {
            QueueItem item = mock(QueueItem.class);
            when(item.getType()).thenReturn(QueueType.PACKING);
            when(item.getRefId()).thenReturn(UUID.randomUUID().toString());
            when(item.getWarehouse()).thenReturn(warehouse);
            when(queueItemRepository.findById("Q1")).thenReturn(Optional.of(item));
            when(queueItemRepository.claimIfFree(eq("Q1"), eq(moderator), any(Instant.class))).thenReturn(1);

            service.claim("Q1", moderator);

            verify(auditLogger).logWarehouse(eq(moderator), eq(warehouse),
                    eq(com.rehome.rehome.entity.enums.WarehouseAuditGroup.PACKING), eq("CLAIM_TASK"),
                    eq("ORDER"), any(), eq("SUCCESS"), any());
        }

        @Test
        void packingTask_alreadyClaimed_writesNoAuditLog() {
            QueueItem item = mock(QueueItem.class);
            when(item.getType()).thenReturn(QueueType.PACKING);
            when(item.getWarehouse()).thenReturn(warehouse);
            when(queueItemRepository.findById("Q1")).thenReturn(Optional.of(item));
            when(queueItemRepository.claimIfFree(eq("Q1"), eq(moderator), any(Instant.class))).thenReturn(0);

            assertAppError(() -> service.claim("Q1", moderator), ErrorCode.QUEUE_ITEM_ALREADY_CLAIMED);
            verify(auditLogger, never()).logWarehouse(any(), any(Warehouse.class), any(), any(), any(), any(), any(), any());
        }

        @Test
        void nonPackingTask_success_writesNoAuditLog() {
            QueueItem item = mock(QueueItem.class);
            when(item.getType()).thenReturn(QueueType.FORM_REVIEW);
            when(item.getWarehouse()).thenReturn(warehouse);
            when(queueItemRepository.findById("Q1")).thenReturn(Optional.of(item));
            when(queueItemRepository.claimIfFree(eq("Q1"), eq(moderator), any(Instant.class))).thenReturn(1);

            service.claim("Q1", moderator);

            verify(auditLogger, never()).logWarehouse(any(), any(Warehouse.class), any(), any(), any(), any(), any(), any());
        }

        @Test
        void success_returnsClaimInfo() {
            QueueItem item = mock(QueueItem.class);
            when(item.getWarehouse()).thenReturn(warehouse);
            when(queueItemRepository.findById("Q1")).thenReturn(Optional.of(item));
            when(queueItemRepository.claimIfFree(eq("Q1"), eq(moderator), any(Instant.class))).thenReturn(1);

            QueueClaimResponse res = service.claim("Q1", moderator);

            assertThat(res)
                    .hasFieldOrPropertyWithValue("id", "Q1")
                    .hasFieldOrPropertyWithValue("claimedBy", moderator.getId().toString());
            assertThat(res).extracting("claimedAt").isNotNull();
        }

        @Test
        void itemWithoutWarehouse_canBeClaimedByAnyModerator() {
            QueueItem item = mock(QueueItem.class);
            when(item.getWarehouse()).thenReturn(null);
            when(queueItemRepository.findById("Q1")).thenReturn(Optional.of(item));
            when(queueItemRepository.claimIfFree(eq("Q1"), eq(moderator), any(Instant.class))).thenReturn(1);

            assertThat(service.claim("Q1", moderator)).isNotNull();
        }
    }

    // ================================================================ release

    @Nested
    @DisplayName("release")
    class Release {

        @Test
        void itemNotFound_throws() {
            when(queueItemRepository.findById("Q1")).thenReturn(Optional.empty());
            assertAppError(() -> service.release("Q1", moderator), ErrorCode.QUEUE_ITEM_NOT_FOUND);
        }

        @Test
        void notClaimedAtAll_throws() {
            QueueItem item = mock(QueueItem.class);
            when(item.getClaimedBy()).thenReturn(null);
            when(queueItemRepository.findById("Q1")).thenReturn(Optional.of(item));

            assertAppError(() -> service.release("Q1", moderator), ErrorCode.QUEUE_ITEM_NOT_CLAIMED_BY_YOU);
            verify(queueItemRepository, never()).save(any());
        }

        @Test
        void packingTask_release_writesReleaseAuditLog() {
            QueueItem item = mock(QueueItem.class);
            when(item.getClaimedBy()).thenReturn(moderator);
            when(item.getType()).thenReturn(QueueType.PACKING);
            when(item.getRefId()).thenReturn(UUID.randomUUID().toString());
            when(item.getWarehouse()).thenReturn(warehouse);
            when(queueItemRepository.findById("Q1")).thenReturn(Optional.of(item));

            service.release("Q1", moderator);

            verify(auditLogger).logWarehouse(eq(moderator), eq(warehouse),
                    eq(com.rehome.rehome.entity.enums.WarehouseAuditGroup.PACKING), eq("RELEASE_TASK"),
                    eq("ORDER"), any(), eq("SUCCESS"), any());
        }

        @Test
        void claimedBySomeoneElse_throws() {
            Account other = mock(Account.class);
            when(other.getId()).thenReturn(UUID.randomUUID());
            QueueItem item = mock(QueueItem.class);
            when(item.getClaimedBy()).thenReturn(other);
            when(queueItemRepository.findById("Q1")).thenReturn(Optional.of(item));

            assertAppError(() -> service.release("Q1", moderator), ErrorCode.QUEUE_ITEM_NOT_CLAIMED_BY_YOU);
            verify(queueItemRepository, never()).save(any());
        }

        @Test
        void claimedByMe_clearsClaimAndSaves() {
            QueueItem item = mock(QueueItem.class);
            when(item.getClaimedBy()).thenReturn(moderator);
            when(queueItemRepository.findById("Q1")).thenReturn(Optional.of(item));

            service.release("Q1", moderator);

            verify(item).setClaimedBy(null);
            verify(item).setClaimedAt(null);
            verify(queueItemRepository).save(item);
        }
    }

    // ================================================================ enqueue / dequeue

    @Nested
    @DisplayName("enqueue / dequeue")
    class EnqueueDequeue {

        @Test
        void enqueue_existing_returnsWithoutSaving() {
            QueueItem existing = mock(QueueItem.class);
            when(queueItemRepository.findByTypeAndRefId(QueueType.WITHDRAWAL, "R1"))
                    .thenReturn(Optional.of(existing));

            QueueItem res = service.enqueue(QueueType.WITHDRAWAL, "R1", warehouse);

            assertThat(res).isSameAs(existing);
            verify(queueItemRepository, never()).save(any());
        }

        @Test
        void enqueue_new_savesItemWithTypeRefAndWarehouse() {
            when(queueItemRepository.findByTypeAndRefId(QueueType.DONATION_PICKUP, "D1"))
                    .thenReturn(Optional.empty());
            when(queueItemRepository.save(any(QueueItem.class))).thenAnswer(inv -> inv.getArgument(0));

            QueueItem res = service.enqueue(QueueType.DONATION_PICKUP, "D1", warehouse);

            assertThat(res.getType()).isEqualTo(QueueType.DONATION_PICKUP);
            assertThat(res.getRefId()).isEqualTo("D1");
            assertThat(res.getWarehouse()).isSameAs(warehouse);
        }

        @Test
        void dequeue_existing_deletesIt() {
            QueueItem existing = mock(QueueItem.class);
            when(queueItemRepository.findByTypeAndRefId(QueueType.WITHDRAWAL, "R1"))
                    .thenReturn(Optional.of(existing));

            service.dequeue(QueueType.WITHDRAWAL, "R1");

            verify(queueItemRepository).delete(existing);
        }

        @Test
        void dequeue_missing_doesNothing() {
            when(queueItemRepository.findByTypeAndRefId(QueueType.WITHDRAWAL, "R1"))
                    .thenReturn(Optional.empty());

            service.dequeue(QueueType.WITHDRAWAL, "R1");

            verify(queueItemRepository, never()).delete(any());
        }
    }
}