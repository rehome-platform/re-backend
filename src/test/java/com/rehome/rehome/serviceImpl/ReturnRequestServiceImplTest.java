package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.client.GhnApi;
import com.rehome.rehome.dto.request.CreateReturnRequestRequest;
import com.rehome.rehome.dto.request.GhnCreateOrderRequest;
import com.rehome.rehome.dto.request.InvalidReturnChoiceRequest;
import com.rehome.rehome.dto.request.MarkLostRequest;
import com.rehome.rehome.dto.request.RejectReturnRequestRequest;
import com.rehome.rehome.dto.request.ResolveReturnRequestRequest;
import com.rehome.rehome.dto.request.StartInspectionRequest;
import com.rehome.rehome.dto.response.GhnCreateOrderResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.ReturnRequestResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.OrderStatus;
import com.rehome.rehome.entity.enums.QueueType;
import com.rehome.rehome.entity.enums.ReturnRequestStatus;
import com.rehome.rehome.entity.enums.ReturnedItemAction;
import com.rehome.rehome.entity.marketplace.DegradedItemCase;
import com.rehome.rehome.entity.marketplace.Order;
import com.rehome.rehome.entity.marketplace.OrderItem;
import com.rehome.rehome.entity.marketplace.ReturnRequest;
import com.rehome.rehome.entity.marketplace.ReturnRequestPhoto;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ConflictException;
import com.rehome.rehome.exception.ForbiddenException;
import com.rehome.rehome.exception.GhnException;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.DegradedItemCaseRepository;
import com.rehome.rehome.repository.OrderItemRepository;
import com.rehome.rehome.repository.OrderRepository;
import com.rehome.rehome.repository.ReturnRequestPhotoRepository;
import com.rehome.rehome.repository.ReturnRequestRepository;
import com.rehome.rehome.service.OrderService;
import com.rehome.rehome.service.QueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReturnRequestServiceImplTest {

    private static final String EMAIL = "buyer@rehome.com";
    private static final String TOKEN = "ghn-token";
    private static final String SHOP_ID = "ghn-shop";

    private static final UUID BUYER_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID RR_ID = UUID.randomUUID();

    @Mock private AccountRepository accountRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private ReturnRequestRepository returnRequestRepository;
    @Mock private ReturnRequestPhotoRepository returnRequestPhotoRepository;
    @Mock private DegradedItemCaseRepository degradedItemCaseRepository;
    @Mock private OrderService orderService;
    @Mock private QueueService queueService;
    @Mock private GhnApi ghnApi;

    @InjectMocks
    private ReturnRequestServiceImpl service;

    private Account buyer;
    private Account moderator;
    private Order order;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "requestWindowDays", 3L);
        ReflectionTestUtils.setField(service, "handoverDeadlineDays", 3L);
        ReflectionTestUtils.setField(service, "pickupDeadlineDays", 7L);
        ReflectionTestUtils.setField(service, "ghnToken", TOKEN);
        ReflectionTestUtils.setField(service, "ghnShopId", SHOP_ID);
        ReflectionTestUtils.setField(service, "defaultPackageWeightGrams", 500);
        ReflectionTestUtils.setField(service, "defaultPackageLengthCm", 20);
        ReflectionTestUtils.setField(service, "defaultPackageWidthCm", 15);
        ReflectionTestUtils.setField(service, "defaultPackageHeightCm", 10);

        buyer = mock(Account.class);
        when(buyer.getId()).thenReturn(BUYER_ID);

        moderator = mock(Account.class);

        order = mock(Order.class);
        when(order.getId()).thenReturn(ORDER_ID);
        when(order.getBuyer()).thenReturn(buyer);
        when(order.getWarehouse()).thenReturn(mock(Warehouse.class));
        when(order.getSubtotal()).thenReturn(new BigDecimal("1000000"));

        when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(buyer));
    }

    // ------------------------------------------------------------------ helpers

    private ReturnRequest givenReturnRequest(ReturnRequestStatus status) {
        ReturnRequest rr = ReturnRequest.builder().order(order).status(status).build();
        ReflectionTestUtils.setField(rr, "id", RR_ID);
        when(returnRequestRepository.findById(RR_ID)).thenReturn(Optional.of(rr));
        return rr;
    }

    private void givenOtherBuyerOwnsOrder() {
        Account other = mock(Account.class);
        when(other.getId()).thenReturn(UUID.randomUUID());
        when(order.getBuyer()).thenReturn(other);
    }

    private CreateReturnRequestRequest createReq(String orderId, String reason, List<String> photos) {
        CreateReturnRequestRequest req = mock(CreateReturnRequestRequest.class);
        when(req.getOrderId()).thenReturn(orderId);
        when(req.getReason()).thenReturn(reason);
        when(req.getPhotoUrls()).thenReturn(photos);
        return req;
    }

    private StartInspectionRequest inspectReq(String note, Boolean matches, Boolean degraded, Boolean counterfeit) {
        StartInspectionRequest req = mock(StartInspectionRequest.class);
        when(req.getConditionNote()).thenReturn(note);
        when(req.getMatchesOriginal()).thenReturn(matches);
        when(req.getDegraded()).thenReturn(degraded);
        when(req.getCounterfeitSuspected()).thenReturn(counterfeit);
        return req;
    }

    private ResolveReturnRequestRequest resolveReq(String resolution, String action, String note) {
        ResolveReturnRequestRequest req = mock(ResolveReturnRequestRequest.class);
        when(req.getResolution()).thenReturn(resolution);
        when(req.getAction()).thenReturn(action);
        when(req.getResolutionNote()).thenReturn(note);
        return req;
    }

    private RejectReturnRequestRequest rejectReq(String reason) {
        RejectReturnRequestRequest req = mock(RejectReturnRequestRequest.class);
        when(req.getReason()).thenReturn(reason);
        return req;
    }

    private MarkLostRequest lostReq(String note) {
        MarkLostRequest req = mock(MarkLostRequest.class);
        when(req.getNote()).thenReturn(note);
        return req;
    }

    // Không cần biết tên enum của choice: lấy kiểu trả về của getChoice() rồi lấy hằng theo tên.
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object stubChoice(InvalidReturnChoiceRequest req, String constantName) throws Exception {
        Class<?> type = InvalidReturnChoiceRequest.class.getMethod("getChoice").getReturnType();
        Object constant = Enum.valueOf((Class) type, constantName);
        doReturn(constant).when(req).getChoice();
        return constant;
    }

    private OrderItem givenOrderItem() {
        OrderItem item = mock(OrderItem.class, RETURNS_DEEP_STUBS);
        when(item.getPriceAtOrder()).thenReturn(new BigDecimal("100000"));
        when(item.getCommissionRateApplied()).thenReturn(new BigDecimal("0.10"));
        when(orderItemRepository.findAllByOrder_IdWithProductOrderByIdAsc(ORDER_ID)).thenReturn(List.of(item));
        return item;
    }

    private GhnCreateOrderResponse ghnResponseWithCode(String code) {
        GhnCreateOrderResponse resp = mock(GhnCreateOrderResponse.class, RETURNS_DEEP_STUBS);
        when(resp.getData().getOrderCode()).thenReturn(code);
        return resp;
    }

    // ------------------------------------------------------------------ create

    @Nested
    class Create {

        private void givenReturnableOrder() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(order.getStatus()).thenReturn(OrderStatus.DELIVERED);
            when(order.getDeliveredAt()).thenReturn(Instant.now().minus(1, ChronoUnit.DAYS));
            when(returnRequestRepository.existsByOrder_IdAndStatusIn(eq(ORDER_ID), anyList())).thenReturn(false);
            when(returnRequestRepository.save(any(ReturnRequest.class))).thenAnswer(inv -> {
                ReturnRequest saved = inv.getArgument(0);
                ReflectionTestUtils.setField(saved, "id", RR_ID);
                return saved;
            });
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        void blankOrderId_throwsBadRequest(String blank) {
            assertThatThrownBy(() -> service.create(EMAIL, createReq(blank, "reason", List.of("u"))))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("orderId");
        }

        @Test
        void nullOrderId_throwsBadRequest() {
            assertThatThrownBy(() -> service.create(EMAIL, createReq(null, "reason", List.of("u"))))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void blankReason_throwsBadRequest() {
            assertThatThrownBy(() -> service.create(EMAIL, createReq(ORDER_ID.toString(), " ", List.of("u"))))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("reason");
        }

        @Test
        void nullPhotos_throwsBadRequest() {
            assertThatThrownBy(() -> service.create(EMAIL, createReq(ORDER_ID.toString(), "r", null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("ảnh");
        }

        @Test
        void emptyPhotos_throwsBadRequest() {
            assertThatThrownBy(() -> service.create(EMAIL, createReq(ORDER_ID.toString(), "r", List.of())))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void accountNotFound_throwsIllegalArgument() {
            when(accountRepository.findByEmail("x@x.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.create("x@x.com", createReq(ORDER_ID.toString(), "r", List.of("u"))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Account");
        }

        @Test
        void orderIdNotUuid_throwsBadRequest() {
            assertThatThrownBy(() -> service.create(EMAIL, createReq("not-a-uuid", "r", List.of("u"))))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("không hợp lệ");
        }

        @Test
        void orderNotFound_throwsIllegalArgument() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.create(EMAIL, createReq(ORDER_ID.toString(), "r", List.of("u"))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("đơn hàng");
        }

        @Test
        void notOwner_throwsForbidden() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            givenOtherBuyerOwnsOrder();

            assertThatThrownBy(() -> service.create(EMAIL, createReq(ORDER_ID.toString(), "r", List.of("u"))))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void orderStatusNotReturnable_throwsConflict() {
            OrderStatus other = Arrays.stream(OrderStatus.values())
                    .filter(s -> s != OrderStatus.DELIVERED && s != OrderStatus.COMPLETED)
                    .findFirst().orElseThrow();
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(order.getStatus()).thenReturn(other);
            when(order.getDeliveredAt()).thenReturn(Instant.now());

            assertThatThrownBy(() -> service.create(EMAIL, createReq(ORDER_ID.toString(), "r", List.of("u"))))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("đã giao");
        }

        @Test
        void deliveredAtNull_throwsConflict() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(order.getStatus()).thenReturn(OrderStatus.DELIVERED);
            when(order.getDeliveredAt()).thenReturn(null);

            assertThatThrownBy(() -> service.create(EMAIL, createReq(ORDER_ID.toString(), "r", List.of("u"))))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        void windowExpired_throwsConflict() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(order.getStatus()).thenReturn(OrderStatus.DELIVERED);
            when(order.getDeliveredAt()).thenReturn(Instant.now().minus(10, ChronoUnit.DAYS));

            assertThatThrownBy(() -> service.create(EMAIL, createReq(ORDER_ID.toString(), "r", List.of("u"))))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("quá hạn");
        }

        @Test
        void openRequestExists_throwsConflict() {
            givenReturnableOrder();
            when(returnRequestRepository.existsByOrder_IdAndStatusIn(eq(ORDER_ID), anyList())).thenReturn(true);

            assertThatThrownBy(() -> service.create(EMAIL, createReq(ORDER_ID.toString(), "r", List.of("u"))))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("đang xử lý");
            verify(returnRequestRepository, never()).save(any());
        }

        @Test
        void delivered_success_savesRequestPhotosAndEnqueues() {
            givenReturnableOrder();
            List<String> urls = List.of("http://a/1.jpg", "http://a/2.jpg");

            ReturnRequestResponse res = service.create(EMAIL, createReq(ORDER_ID.toString(), "Hàng lỗi", urls));

            ArgumentCaptor<ReturnRequest> rrCaptor = ArgumentCaptor.forClass(ReturnRequest.class);
            verify(returnRequestRepository).save(rrCaptor.capture());
            ReturnRequest saved = rrCaptor.getValue();
            assertThat(saved.getStatus()).isEqualTo(ReturnRequestStatus.PENDING);
            assertThat(saved.getReason()).isEqualTo("Hàng lỗi");
            assertThat(saved.getHandoverDeadline()).isAfter(Instant.now());

            verify(queueService).enqueue(eq(QueueType.RETURN), eq(RR_ID.toString()), any());

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<ReturnRequestPhoto>> photoCaptor = ArgumentCaptor.forClass(List.class);
            verify(returnRequestPhotoRepository).saveAll(photoCaptor.capture());
            assertThat(photoCaptor.getValue()).hasSize(2);

            assertThat(res.getStatus()).isEqualTo("PENDING");
            assertThat(res.getPhotoUrls()).containsExactlyElementsOf(urls);
        }

        @Test
        void completedOrder_withinWindow_isAllowed() {
            givenReturnableOrder();
            when(order.getStatus()).thenReturn(OrderStatus.COMPLETED);

            ReturnRequestResponse res = service.create(EMAIL, createReq(ORDER_ID.toString(), "r", List.of("u")));

            assertThat(res.getStatus()).isEqualTo("PENDING");
        }
    }

    // ------------------------------------------------------------------ getMyReturnRequests / getMyReturnRequest

    @Nested
    class MyRequests {

        @Test
        void getMyReturnRequests_accountNotFound() {
            when(accountRepository.findByEmail("x@x.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getMyReturnRequests("x@x.com", 0, 10))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void getMyReturnRequests_success_clampsNegativePageAndZeroSize() {
            ReturnRequest rr = givenReturnRequest(ReturnRequestStatus.PENDING);
            Page<ReturnRequest> page = new PageImpl<>(List.of(rr));
            when(returnRequestRepository.findByOrder_Buyer_IdOrderByCreatedAtDesc(eq(BUYER_ID), any(Pageable.class)))
                    .thenReturn(page);

            PageResponse<ReturnRequestResponse> res = service.getMyReturnRequests(EMAIL, -5, 0);

            assertThat(res).isNotNull();
            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(returnRequestRepository).findByOrder_Buyer_IdOrderByCreatedAtDesc(eq(BUYER_ID), captor.capture());
            assertThat(captor.getValue().getPageNumber()).isZero();
            assertThat(captor.getValue().getPageSize()).isEqualTo(1);
        }

        @Test
        void getMyReturnRequest_accountNotFound() {
            when(accountRepository.findByEmail("x@x.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getMyReturnRequest("x@x.com", RR_ID))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void getMyReturnRequest_notFound() {
            when(returnRequestRepository.findById(RR_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getMyReturnRequest(EMAIL, RR_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("yêu cầu trả hàng");
        }

        @Test
        void getMyReturnRequest_notOwner_forbidden() {
            givenReturnRequest(ReturnRequestStatus.PENDING);
            givenOtherBuyerOwnsOrder();

            assertThatThrownBy(() -> service.getMyReturnRequest(EMAIL, RR_ID))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void getMyReturnRequest_success_includesPhotos() {
            givenReturnRequest(ReturnRequestStatus.PENDING);
            ReturnRequestPhoto photo = mock(ReturnRequestPhoto.class);
            when(photo.getImageUrl()).thenReturn("http://a/1.jpg");
            when(returnRequestPhotoRepository.findAllByReturnRequest_IdOrderByIdAsc(RR_ID))
                    .thenReturn(List.of(photo));

            ReturnRequestResponse res = service.getMyReturnRequest(EMAIL, RR_ID);

            assertThat(res.getId()).isEqualTo(RR_ID.toString());
            assertThat(res.getPhotoUrls()).containsExactly("http://a/1.jpg");
        }
    }

    // ------------------------------------------------------------------ moderator reads

    @Nested
    class ModeratorReads {

        private final List<ReturnRequestStatus> openStatuses = List.of(
                ReturnRequestStatus.PENDING,
                ReturnRequestStatus.APPROVED,
                ReturnRequestStatus.SHIPPED_BACK,
                ReturnRequestStatus.INSPECTING);

        @Test
        void getQueue_noStatus_usesOpenStatuses() {
            when(returnRequestRepository.findByStatusInOrderByCreatedAtAsc(anyList(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            service.getQueue(null, 0, 10);

            verify(returnRequestRepository).findByStatusInOrderByCreatedAtAsc(eq(openStatuses), any(Pageable.class));
        }

        @Test
        void getQueue_withStatus_filtersByThatStatus() {
            when(returnRequestRepository.findByStatusInOrderByCreatedAtAsc(anyList(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            service.getQueue(" approved ", 0, 10);

            verify(returnRequestRepository).findByStatusInOrderByCreatedAtAsc(
                    eq(List.of(ReturnRequestStatus.APPROVED)), any(Pageable.class));
        }

        @Test
        void getQueue_invalidStatus_throwsBadRequest() {
            assertThatThrownBy(() -> service.getQueue("WHATEVER", 0, 10))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("status");
        }

        @Test
        void getQueue_clampsPaging() {
            when(returnRequestRepository.findByStatusInOrderByCreatedAtAsc(anyList(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            service.getQueue("", -1, -1);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(returnRequestRepository).findByStatusInOrderByCreatedAtAsc(anyList(), captor.capture());
            assertThat(captor.getValue().getPageNumber()).isZero();
            assertThat(captor.getValue().getPageSize()).isEqualTo(1);
        }

        @Test
        void getDetailForModerator_success() {
            givenReturnRequest(ReturnRequestStatus.PENDING);

            ReturnRequestResponse res = service.getDetailForModerator(RR_ID);

            assertThat(res.getId()).isEqualTo(RR_ID.toString());
            assertThat(res.getStatus()).isEqualTo("PENDING");
        }

        @Test
        void getDetailForModerator_notFound() {
            when(returnRequestRepository.findById(RR_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getDetailForModerator(RR_ID))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ------------------------------------------------------------------ approve

    @Nested
    class Approve {

        @Test
        void alreadyHasTrackingCode_returnsWithoutCallingGhn() {
            ReturnRequest rr = givenReturnRequest(ReturnRequestStatus.APPROVED);
            rr.setGhnReturnTrackingCode("OLD-CODE");

            ReturnRequestResponse res = service.approve(RR_ID);

            assertThat(res.getGhnReturnTrackingCode()).isEqualTo("OLD-CODE");
            verifyNoInteractions(ghnApi);
            verify(returnRequestRepository, never()).save(any());
        }

        @Test
        void notPending_throwsConflict() {
            givenReturnRequest(ReturnRequestStatus.INSPECTING);

            assertThatThrownBy(() -> service.approve(RR_ID))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("chờ duyệt");
            verifyNoInteractions(ghnApi);
        }

        @Test
        void notFound_throwsIllegalArgument() {
            when(returnRequestRepository.findById(RR_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.approve(RR_ID)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void ghnThrows_wrapsIntoGhnException() {
            ReturnRequest rr = givenReturnRequest(ReturnRequestStatus.PENDING);
            givenOrderItem();
            when(ghnApi.createOrder(anyString(), anyString(), any(GhnCreateOrderRequest.class)))
                    .thenThrow(new RuntimeException("boom"));

            assertThatThrownBy(() -> service.approve(RR_ID))
                    .isInstanceOf(GhnException.class)
                    .hasMessageContaining("GHN_ERROR");
            assertThat(rr.getStatus()).isEqualTo(ReturnRequestStatus.PENDING);
            verify(returnRequestRepository, never()).save(any());
        }

        @Test
        void ghnReturnsNull_throwsGhnException() {
            givenReturnRequest(ReturnRequestStatus.PENDING);
            givenOrderItem();
            when(ghnApi.createOrder(anyString(), anyString(), any(GhnCreateOrderRequest.class))).thenReturn(null);

            assertThatThrownBy(() -> service.approve(RR_ID)).isInstanceOf(GhnException.class);
        }

        @Test
        void ghnReturnsNullData_throwsGhnException() {
            givenReturnRequest(ReturnRequestStatus.PENDING);
            givenOrderItem();
            GhnCreateOrderResponse resp = mock(GhnCreateOrderResponse.class);
            when(resp.getData()).thenReturn(null);
            when(resp.getMessage()).thenReturn("error");
            when(ghnApi.createOrder(anyString(), anyString(), any(GhnCreateOrderRequest.class))).thenReturn(resp);

            assertThatThrownBy(() -> service.approve(RR_ID)).isInstanceOf(GhnException.class);
        }

        @Test
        void ghnReturnsBlankOrderCode_throwsGhnException() {
            givenReturnRequest(ReturnRequestStatus.PENDING);
            givenOrderItem();
            GhnCreateOrderResponse blankCodeResponse = ghnResponseWithCode(" ");
            when(ghnApi.createOrder(anyString(), anyString(), any(GhnCreateOrderRequest.class)))
                    .thenReturn(blankCodeResponse);

            assertThatThrownBy(() -> service.approve(RR_ID)).isInstanceOf(GhnException.class);
        }

        @Test
        void success_setsTrackingCodeStatusAndDeadline() {
            ReturnRequest rr = givenReturnRequest(ReturnRequestStatus.PENDING);
            givenOrderItem();
            GhnCreateOrderResponse okResponse = ghnResponseWithCode("GHN123");
            when(ghnApi.createOrder(eq(TOKEN), eq(SHOP_ID), any(GhnCreateOrderRequest.class)))
                    .thenReturn(okResponse);

            ReturnRequestResponse res = service.approve(RR_ID);

            assertThat(rr.getStatus()).isEqualTo(ReturnRequestStatus.APPROVED);
            assertThat(rr.getGhnReturnTrackingCode()).isEqualTo("GHN123");
            assertThat(rr.getHandoverDeadline()).isAfter(Instant.now().plus(2, ChronoUnit.DAYS));
            assertThat(res.getGhnReturnTrackingCode()).isEqualTo("GHN123");
            verify(returnRequestRepository).save(rr);

            ArgumentCaptor<GhnCreateOrderRequest> captor = ArgumentCaptor.forClass(GhnCreateOrderRequest.class);
            verify(ghnApi).createOrder(eq(TOKEN), eq(SHOP_ID), captor.capture());
            assertThat(captor.getValue().getClientOrderCode()).isEqualTo("RT-" + RR_ID);
        }
    }

    // ------------------------------------------------------------------ reject

    @Nested
    class Reject {

        @Test
        void nullRequest_throwsBadRequest() {
            assertThatThrownBy(() -> service.reject(RR_ID, null)).isInstanceOf(BadRequestException.class);
        }

        @Test
        void blankReason_throwsBadRequest() {
            assertThatThrownBy(() -> service.reject(RR_ID, rejectReq("  ")))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void notPending_throwsConflict() {
            givenReturnRequest(ReturnRequestStatus.APPROVED);

            assertThatThrownBy(() -> service.reject(RR_ID, rejectReq("no")))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        void withinWindow_cancelsWithoutPayout() {
            ReturnRequest rr = givenReturnRequest(ReturnRequestStatus.PENDING);
            when(order.getAutoCompleteDeadline()).thenReturn(Instant.now().plus(1, ChronoUnit.DAYS));

            ReturnRequestResponse res = service.reject(RR_ID, rejectReq("Không đủ bằng chứng"));

            assertThat(rr.getStatus()).isEqualTo(ReturnRequestStatus.CANCELLED);
            assertThat(rr.getResolutionNote()).isEqualTo("Không đủ bằng chứng");
            assertThat(rr.getResolvedAt()).isNotNull();
            assertThat(res.getStatus()).isEqualTo("CANCELLED");
            verify(queueService).dequeue(QueueType.RETURN, RR_ID.toString());
            verify(orderService, never()).payoutOrder(any(), anyString());
        }

        @Test
        void afterWindow_payoutImmediately() {
            givenReturnRequest(ReturnRequestStatus.PENDING);
            when(order.getAutoCompleteDeadline()).thenReturn(Instant.now().minus(1, ChronoUnit.HOURS));

            service.reject(RR_ID, rejectReq("no"));

            verify(orderService).payoutOrder(eq(ORDER_ID), anyString());
        }

        @Test
        void autoCompleteDeadlineNull_noPayout() {
            givenReturnRequest(ReturnRequestStatus.PENDING);
            when(order.getAutoCompleteDeadline()).thenReturn(null);

            service.reject(RR_ID, rejectReq("no"));

            verify(orderService, never()).payoutOrder(any(), anyString());
        }
    }

    // ------------------------------------------------------------------ confirmHandover

    @Nested
    class ConfirmHandover {

        @Test
        void accountNotFound() {
            when(accountRepository.findByEmail("x@x.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.confirmHandover("x@x.com", RR_ID))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void notOwner_forbidden() {
            givenReturnRequest(ReturnRequestStatus.APPROVED);
            givenOtherBuyerOwnsOrder();

            assertThatThrownBy(() -> service.confirmHandover(EMAIL, RR_ID))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void alreadyShippedBack_returnsIdempotently() {
            givenReturnRequest(ReturnRequestStatus.SHIPPED_BACK);

            ReturnRequestResponse res = service.confirmHandover(EMAIL, RR_ID);

            assertThat(res.getStatus()).isEqualTo("SHIPPED_BACK");
            verify(returnRequestRepository, never()).save(any());
        }

        @Test
        void notApproved_throwsConflict() {
            givenReturnRequest(ReturnRequestStatus.PENDING);

            assertThatThrownBy(() -> service.confirmHandover(EMAIL, RR_ID))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("chưa được duyệt");
        }

        @Test
        void deadlinePassed_cancelsAndThrows_withPayoutWhenWindowPassed() {
            ReturnRequest rr = givenReturnRequest(ReturnRequestStatus.APPROVED);
            rr.setHandoverDeadline(Instant.now().minus(1, ChronoUnit.HOURS));
            when(order.getAutoCompleteDeadline()).thenReturn(Instant.now().minus(1, ChronoUnit.DAYS));

            assertThatThrownBy(() -> service.confirmHandover(EMAIL, RR_ID))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("quá hạn bàn giao");

            assertThat(rr.getStatus()).isEqualTo(ReturnRequestStatus.CANCELLED);
            assertThat(rr.getResolvedAt()).isNotNull();
            verify(returnRequestRepository).save(rr);
            verify(orderService).payoutOrder(eq(ORDER_ID), anyString());
        }

        @Test
        void deadlinePassed_noPayoutWhenStillInReturnWindow() {
            ReturnRequest rr = givenReturnRequest(ReturnRequestStatus.APPROVED);
            rr.setHandoverDeadline(Instant.now().minus(1, ChronoUnit.HOURS));
            when(order.getAutoCompleteDeadline()).thenReturn(Instant.now().plus(1, ChronoUnit.DAYS));

            assertThatThrownBy(() -> service.confirmHandover(EMAIL, RR_ID))
                    .isInstanceOf(ConflictException.class);

            verify(orderService, never()).payoutOrder(any(), anyString());
        }

        @Test
        void success_setsShippedBack() {
            ReturnRequest rr = givenReturnRequest(ReturnRequestStatus.APPROVED);
            rr.setHandoverDeadline(Instant.now().plus(1, ChronoUnit.DAYS));

            ReturnRequestResponse res = service.confirmHandover(EMAIL, RR_ID);

            assertThat(rr.getStatus()).isEqualTo(ReturnRequestStatus.SHIPPED_BACK);
            assertThat(res.getStatus()).isEqualTo("SHIPPED_BACK");
            verify(returnRequestRepository).save(rr);
        }

        @Test
        void success_whenDeadlineNull() {
            ReturnRequest rr = givenReturnRequest(ReturnRequestStatus.APPROVED);
            rr.setHandoverDeadline(null);

            service.confirmHandover(EMAIL, RR_ID);

            assertThat(rr.getStatus()).isEqualTo(ReturnRequestStatus.SHIPPED_BACK);
        }
    }

    // ------------------------------------------------------------------ startInspection

    @Nested
    class StartInspection {

        @Test
        void nullRequest_throwsBadRequest() {
            assertThatThrownBy(() -> service.startInspection(RR_ID, null))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void blankNote_throwsBadRequest() {
            assertThatThrownBy(() -> service.startInspection(RR_ID, inspectReq(" ", true, false, false)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("conditionNote");
        }

        @Test
        void wrongStatus_throwsConflict() {
            givenReturnRequest(ReturnRequestStatus.APPROVED);

            assertThatThrownBy(() -> service.startInspection(RR_ID, inspectReq("ok", true, false, false)))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("bàn giao");
        }

        @Test
        void fromShippedBack_movesToInspecting_andStoresFields() {
            ReturnRequest rr = givenReturnRequest(ReturnRequestStatus.SHIPPED_BACK);

            ReturnRequestResponse res = service.startInspection(RR_ID, inspectReq("Trầy xước", true, true, true));

            assertThat(rr.getStatus()).isEqualTo(ReturnRequestStatus.INSPECTING);
            assertThat(rr.getMatchesOriginal()).isTrue();
            assertThat(rr.getDegraded()).isTrue();
            assertThat(rr.getCounterfeitSuspected()).isTrue();
            assertThat(rr.getConditionNote()).isEqualTo("Trầy xước");
            assertThat(res.getStatus()).isEqualTo("INSPECTING");
            verify(returnRequestRepository).save(rr);
        }

        @Test
        void fromInspecting_isAllowed_andNullCounterfeitBecomesFalse() {
            ReturnRequest rr = givenReturnRequest(ReturnRequestStatus.INSPECTING);

            service.startInspection(RR_ID, inspectReq("Còn mới", true, false, null));

            assertThat(rr.getCounterfeitSuspected()).isFalse();
            assertThat(rr.getDegraded()).isFalse();
        }

        @Test
        void notFound() {
            when(returnRequestRepository.findById(RR_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.startInspection(RR_ID, inspectReq("ok", true, false, false)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ------------------------------------------------------------------ resolve

    @Nested
    class Resolve {

        @Test
        void nullRequest_throwsBadRequest() {
            assertThatThrownBy(() -> service.resolve(moderator, RR_ID, null))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void blankResolution_throwsBadRequest() {
            assertThatThrownBy(() -> service.resolve(moderator, RR_ID, resolveReq(" ", "RELIST", "n")))
                    .isInstanceOf(BadRequestException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"FOO", "PENDING", "LOST"})
        void invalidResolution_throwsBadRequest(String value) {
            assertThatThrownBy(() -> service.resolve(moderator, RR_ID, resolveReq(value, "RELIST", "n")))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("VALID hoặc INVALID");
        }

        @Test
        void notFound() {
            when(returnRequestRepository.findById(RR_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.resolve(moderator, RR_ID, resolveReq("VALID", "RELIST", "n")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void notInspecting_throwsConflict() {
            givenReturnRequest(ReturnRequestStatus.SHIPPED_BACK);

            assertThatThrownBy(() -> service.resolve(moderator, RR_ID, resolveReq("VALID", "RELIST", "n")))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Chưa kiểm tra");
        }

        @Test
        void valid_notDegraded_missingAction_throwsBadRequest() {
            ReturnRequest rr = givenReturnRequest(ReturnRequestStatus.INSPECTING);
            rr.setDegraded(false);

            assertThatThrownBy(() -> service.resolve(moderator, RR_ID, resolveReq("VALID", null, "n")))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("action");
            verifyNoInteractions(orderService);
        }

        @Test
        void valid_notDegraded_invalidAction_throwsBadRequest() {
            ReturnRequest rr = givenReturnRequest(ReturnRequestStatus.INSPECTING);
            rr.setDegraded(false);

            assertThatThrownBy(() -> service.resolve(moderator, RR_ID, resolveReq("VALID", "BURN", "n")))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("RELIST");
        }

        @Test
        void valid_notDegraded_success_refundsAndAppliesAction() {
            ReturnRequest rr = givenReturnRequest(ReturnRequestStatus.INSPECTING);
            rr.setDegraded(false);

            ReturnRequestResponse res = service.resolve(moderator, RR_ID, resolveReq(" valid ", "relist", "Hợp lệ"));

            assertThat(rr.getStatus()).isEqualTo(ReturnRequestStatus.VALID);
            assertThat(rr.getItemAction()).isEqualTo(ReturnedItemAction.RELIST);
            assertThat(rr.getResolutionNote()).isEqualTo("Hợp lệ");
            assertThat(rr.getResolvedAt()).isNotNull();
            assertThat(res.getStatus()).isEqualTo("VALID");

            verify(queueService).dequeue(QueueType.RETURN, RR_ID.toString());
            verify(orderService).refundOrderForReturn(eq(ORDER_ID), anyString());
            verify(orderService).applyReturnedItemAction(ORDER_ID, ReturnedItemAction.RELIST);
            verify(degradedItemCaseRepository, never()).save(any());
        }

        @Test
        void valid_degradedNullFlag_treatedAsNotDegraded() {
            ReturnRequest rr = givenReturnRequest(ReturnRequestStatus.INSPECTING);
            rr.setDegraded(null);

            service.resolve(moderator, RR_ID, resolveReq("VALID", "DISCARD", "n"));

            verify(orderService).applyReturnedItemAction(ORDER_ID, ReturnedItemAction.DISCARD);
        }

        @Test
        void valid_degraded_opensDegradedCase_withoutAction() {
            ReturnRequest rr = givenReturnRequest(ReturnRequestStatus.INSPECTING);
            rr.setDegraded(true);
            OrderItem item = givenOrderItem();

            service.resolve(moderator, RR_ID, resolveReq("VALID", null, "Xuống cấp"));

            assertThat(rr.getStatus()).isEqualTo(ReturnRequestStatus.VALID);
            assertThat(rr.getItemAction()).isNull();
            verify(orderService).refundOrderForReturn(eq(ORDER_ID), anyString());
            verify(orderService, never()).applyReturnedItemAction(any(), any());

            ArgumentCaptor<DegradedItemCase> captor = ArgumentCaptor.forClass(DegradedItemCase.class);
            verify(degradedItemCaseRepository).save(captor.capture());
            // 100000 - 100000 * 0.10 = 90000 (không trừ thuế)
            assertThat(captor.getValue().getCompensationAmount()).isEqualByComparingTo("90000");
            assertThat(item).isNotNull();
        }

        @Test
        void valid_degraded_orderHasNoItem_throwsIllegalState() {
            ReturnRequest rr = givenReturnRequest(ReturnRequestStatus.INSPECTING);
            rr.setDegraded(true);
            when(orderItemRepository.findAllByOrder_IdWithProductOrderByIdAsc(ORDER_ID)).thenReturn(List.of());

            assertThatThrownBy(() -> service.resolve(moderator, RR_ID, resolveReq("VALID", null, "n")))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void invalid_setsPickupDeadline_paysOutAndKeepsOpen() {
            ReturnRequest rr = givenReturnRequest(ReturnRequestStatus.INSPECTING);

            ReturnRequestResponse res = service.resolve(moderator, RR_ID, resolveReq("INVALID", null, "Hàng giả"));

            assertThat(rr.getStatus()).isEqualTo(ReturnRequestStatus.INVALID);
            assertThat(rr.getResolvedAt()).isNull();
            assertThat(rr.getResolutionNote()).isEqualTo("Hàng giả");
            assertThat(rr.getPickupDeadline()).isAfter(Instant.now().plus(6, ChronoUnit.DAYS));
            assertThat(res.getStatus()).isEqualTo("INVALID");

            verify(queueService).dequeue(QueueType.RETURN, RR_ID.toString());
            verify(orderService).payoutOrder(eq(ORDER_ID), anyString());
            verify(orderService, never()).refundOrderForReturn(any(), anyString());
            verify(orderService, never()).applyReturnedItemAction(any(), any());
        }
    }

    // ------------------------------------------------------------------ chooseInvalidReturn

    @Nested
    class ChooseInvalidReturn {

        @Test
        void nullRequest_throwsBadRequest() {
            assertThatThrownBy(() -> service.chooseInvalidReturn(EMAIL, RR_ID, null))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void nullChoice_throwsBadRequest() {
            InvalidReturnChoiceRequest req = mock(InvalidReturnChoiceRequest.class);
            when(req.getChoice()).thenReturn(null);

            assertThatThrownBy(() -> service.chooseInvalidReturn(EMAIL, RR_ID, req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("choice");
        }

        @Test
        void accountNotFound() throws Exception {
            InvalidReturnChoiceRequest req = mock(InvalidReturnChoiceRequest.class);
            stubChoice(req, "SHIP_BACK");
            when(accountRepository.findByEmail("x@x.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.chooseInvalidReturn("x@x.com", RR_ID, req))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void notOwner_forbidden() throws Exception {
            InvalidReturnChoiceRequest req = mock(InvalidReturnChoiceRequest.class);
            stubChoice(req, "SHIP_BACK");
            givenReturnRequest(ReturnRequestStatus.INVALID);
            givenOtherBuyerOwnsOrder();

            assertThatThrownBy(() -> service.chooseInvalidReturn(EMAIL, RR_ID, req))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void notInvalid_throwsConflict() throws Exception {
            InvalidReturnChoiceRequest req = mock(InvalidReturnChoiceRequest.class);
            stubChoice(req, "SHIP_BACK");
            givenReturnRequest(ReturnRequestStatus.INSPECTING);

            assertThatThrownBy(() -> service.chooseInvalidReturn(EMAIL, RR_ID, req))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("không hợp lệ");
        }

        @Test
        void pickupExpired_throwsConflict() throws Exception {
            InvalidReturnChoiceRequest req = mock(InvalidReturnChoiceRequest.class);
            stubChoice(req, "SHIP_BACK");
            ReturnRequest rr = givenReturnRequest(ReturnRequestStatus.INVALID);
            rr.setPickupDeadline(Instant.now().minus(1, ChronoUnit.HOURS));

            assertThatThrownBy(() -> service.chooseInvalidReturn(EMAIL, RR_ID, req))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("quá hạn");
            verify(returnRequestRepository, never()).save(any());
        }

        @Test
        void success_storesChoice() throws Exception {
            InvalidReturnChoiceRequest req = mock(InvalidReturnChoiceRequest.class);
            Object choice = stubChoice(req, "WAREHOUSE_PICKUP");
            ReturnRequest rr = givenReturnRequest(ReturnRequestStatus.INVALID);
            rr.setPickupDeadline(Instant.now().plus(2, ChronoUnit.DAYS));

            ReturnRequestResponse res = service.chooseInvalidReturn(EMAIL, RR_ID, req);

            assertThat(rr.getInvalidReturnChoice()).isEqualTo(choice);
            assertThat(res.getInvalidReturnChoice()).isEqualTo("WAREHOUSE_PICKUP");
            verify(returnRequestRepository).save(rr);
        }

        @Test
        void success_whenPickupDeadlineNull() throws Exception {
            InvalidReturnChoiceRequest req = mock(InvalidReturnChoiceRequest.class);
            Object choice = stubChoice(req, "SHIP_BACK");
            ReturnRequest rr = givenReturnRequest(ReturnRequestStatus.INVALID);
            rr.setPickupDeadline(null);

            service.chooseInvalidReturn(EMAIL, RR_ID, req);

            assertThat(rr.getInvalidReturnChoice()).isEqualTo(choice);
        }
    }

    // ------------------------------------------------------------------ applyGhnReturnStatus

    @Nested
    class ApplyGhnReturnStatus {

        private static final String CODE = "GHN-RT-1";

        private ReturnRequest givenByTrackingCode(ReturnRequestStatus status) {
            ReturnRequest rr = givenReturnRequest(status);
            when(returnRequestRepository.findByGhnReturnTrackingCode(CODE)).thenReturn(Optional.of(rr));
            return rr;
        }

        @Test
        void unknownTrackingCode_returnsFalse() {
            when(returnRequestRepository.findByGhnReturnTrackingCode("nope")).thenReturn(Optional.empty());

            assertThat(service.applyGhnReturnStatus("nope", "picked", null)).isFalse();
        }

        @Test
        void picked_whenApproved_movesToShippedBack() {
            ReturnRequest rr = givenByTrackingCode(ReturnRequestStatus.APPROVED);

            assertThat(service.applyGhnReturnStatus(CODE, "PICKED", null)).isTrue();

            assertThat(rr.getStatus()).isEqualTo(ReturnRequestStatus.SHIPPED_BACK);
            verify(returnRequestRepository).save(rr);
        }

        @Test
        void picked_whenNotApproved_noChange() {
            ReturnRequest rr = givenByTrackingCode(ReturnRequestStatus.INSPECTING);

            assertThat(service.applyGhnReturnStatus(CODE, "picked", null)).isTrue();

            assertThat(rr.getStatus()).isEqualTo(ReturnRequestStatus.INSPECTING);
            verify(returnRequestRepository, never()).save(any());
        }

        @Test
        void delivered_whenShippedBack_movesToInspecting() {
            ReturnRequest rr = givenByTrackingCode(ReturnRequestStatus.SHIPPED_BACK);

            assertThat(service.applyGhnReturnStatus(CODE, "delivered", null)).isTrue();

            assertThat(rr.getStatus()).isEqualTo(ReturnRequestStatus.INSPECTING);
            verify(returnRequestRepository).save(rr);
        }

        @Test
        void delivered_whenNotShippedBack_noChange() {
            ReturnRequest rr = givenByTrackingCode(ReturnRequestStatus.APPROVED);

            service.applyGhnReturnStatus(CODE, "delivered", null);

            assertThat(rr.getStatus()).isEqualTo(ReturnRequestStatus.APPROVED);
            verify(returnRequestRepository, never()).save(any());
        }

        @ParameterizedTest
        @ValueSource(strings = {"lost", "damage", "scrap", "LOST"})
        void lostStatuses_whenOpen_marksLostAndRefunds(String ghnStatus) {
            ReturnRequest rr = givenByTrackingCode(ReturnRequestStatus.SHIPPED_BACK);

            assertThat(service.applyGhnReturnStatus(CODE, ghnStatus, "Mất hàng")).isTrue();

            assertThat(rr.getStatus()).isEqualTo(ReturnRequestStatus.LOST);
            assertThat(rr.getResolutionNote()).isEqualTo("Mất hàng");
            assertThat(rr.getResolvedAt()).isNotNull();
            verify(queueService).dequeue(QueueType.RETURN, RR_ID.toString());
            verify(orderService).refundOrderForReturn(eq(ORDER_ID), anyString());
        }

        @Test
        void lost_withBlankReason_usesStatusAsNote() {
            ReturnRequest rr = givenByTrackingCode(ReturnRequestStatus.APPROVED);

            service.applyGhnReturnStatus(CODE, "damage", " ");

            assertThat(rr.getResolutionNote()).isEqualTo("damage");
        }

        @Test
        void lost_whenAlreadyClosed_doesNothing() {
            ReturnRequest rr = givenByTrackingCode(ReturnRequestStatus.VALID);

            assertThat(service.applyGhnReturnStatus(CODE, "lost", "x")).isTrue();

            assertThat(rr.getStatus()).isEqualTo(ReturnRequestStatus.VALID);
            verify(orderService, never()).refundOrderForReturn(any(), anyString());
        }

        @Test
        void nullStatus_noChange() {
            ReturnRequest rr = givenByTrackingCode(ReturnRequestStatus.APPROVED);

            assertThat(service.applyGhnReturnStatus(CODE, null, null)).isTrue();

            assertThat(rr.getStatus()).isEqualTo(ReturnRequestStatus.APPROVED);
            verify(returnRequestRepository, never()).save(any());
        }

        @Test
        void unknownStatus_noChange() {
            ReturnRequest rr = givenByTrackingCode(ReturnRequestStatus.APPROVED);

            assertThat(service.applyGhnReturnStatus(CODE, "ready_to_pick", null)).isTrue();

            assertThat(rr.getStatus()).isEqualTo(ReturnRequestStatus.APPROVED);
        }
    }

    // ------------------------------------------------------------------ markLost (API 89)

    @Nested
    class MarkLost {

        @Test
        void nullRequest_throwsBadRequest() {
            assertThatThrownBy(() -> service.markLost(RR_ID, null)).isInstanceOf(BadRequestException.class);
        }

        @Test
        void blankNote_throwsBadRequest() {
            assertThatThrownBy(() -> service.markLost(RR_ID, lostReq(" ")))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("note");
        }

        @Test
        void notFound() {
            when(returnRequestRepository.findById(RR_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.markLost(RR_ID, lostReq("n")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void alreadyLost_returnsWithoutRefund() {
            givenReturnRequest(ReturnRequestStatus.LOST);

            ReturnRequestResponse res = service.markLost(RR_ID, lostReq("n"));

            assertThat(res.getStatus()).isEqualTo("LOST");
            verifyNoInteractions(orderService);
            verify(returnRequestRepository, never()).save(any());
        }

        @Test
        void alreadyResolved_throwsConflict() {
            givenReturnRequest(ReturnRequestStatus.VALID);

            assertThatThrownBy(() -> service.markLost(RR_ID, lostReq("n")))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("xử lý xong");
        }

        @ParameterizedTest
        @ValueSource(strings = {"PENDING", "APPROVED", "SHIPPED_BACK", "INSPECTING"})
        void openStatus_marksLostAndRefunds(String statusName) {
            ReturnRequest rr = givenReturnRequest(ReturnRequestStatus.valueOf(statusName));

            ReturnRequestResponse res = service.markLost(RR_ID, lostReq("GHN làm mất"));

            assertThat(rr.getStatus()).isEqualTo(ReturnRequestStatus.LOST);
            assertThat(rr.getResolutionNote()).isEqualTo("GHN làm mất");
            assertThat(rr.getResolvedAt()).isNotNull();
            assertThat(res.getStatus()).isEqualTo("LOST");
            verify(returnRequestRepository).save(rr);
            verify(queueService).dequeue(QueueType.RETURN, RR_ID.toString());
            verify(orderService).refundOrderForReturn(eq(ORDER_ID), anyString());
        }
    }
}