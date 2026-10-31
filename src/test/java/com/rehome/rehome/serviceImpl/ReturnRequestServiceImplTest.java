package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.client.GhnApi;
import com.rehome.rehome.dto.request.CreateReturnRequestRequest;
import com.rehome.rehome.dto.request.GhnCreateOrderRequest;
import com.rehome.rehome.dto.request.InspectReturnRequest;
import com.rehome.rehome.dto.request.InvalidReturnChoiceRequest;
import com.rehome.rehome.dto.request.RejectReturnRequestRequest;
import com.rehome.rehome.dto.response.GhnCreateOrderResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.ReturnRequestResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.InvalidReturnChoice;
import com.rehome.rehome.entity.enums.OrderStatus;
import com.rehome.rehome.entity.enums.ReturnRequestStatus;
import com.rehome.rehome.entity.marketplace.Order;
import com.rehome.rehome.entity.marketplace.OrderItem;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.ReturnRequest;
import com.rehome.rehome.entity.marketplace.ReturnRequestPhoto;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ConflictException;
import com.rehome.rehome.exception.ForbiddenException;
import com.rehome.rehome.exception.GhnException;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.OrderItemRepository;
import com.rehome.rehome.repository.OrderRepository;
import com.rehome.rehome.repository.ReturnRequestPhotoRepository;
import com.rehome.rehome.repository.ReturnRequestRepository;
import com.rehome.rehome.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReturnRequestServiceImplTest {

    @Mock AccountRepository accountRepository;
    @Mock OrderRepository orderRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock ReturnRequestRepository returnRequestRepository;
    @Mock ReturnRequestPhotoRepository returnRequestPhotoRepository;
    @Mock OrderService orderService;
    @Mock GhnApi ghnApi;

    @InjectMocks
    ReturnRequestServiceImpl service;

    static final String EMAIL = "buyer@example.com";
    static final String GHN_TOKEN = "ghn-token";
    static final String GHN_SHOP_ID = "12345";
    static final long REQUEST_WINDOW_DAYS = 7;
    static final long HANDOVER_DEADLINE_DAYS = 3;
    static final long PICKUP_DEADLINE_DAYS = 5;

    Account buyer;
    Account otherBuyer;
    UUID orderId;
    Order order;
    UUID returnId;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "requestWindowDays", REQUEST_WINDOW_DAYS);
        ReflectionTestUtils.setField(service, "handoverDeadlineDays", HANDOVER_DEADLINE_DAYS);
        ReflectionTestUtils.setField(service, "pickupDeadlineDays", PICKUP_DEADLINE_DAYS);
        ReflectionTestUtils.setField(service, "ghnToken", GHN_TOKEN);
        ReflectionTestUtils.setField(service, "ghnShopId", GHN_SHOP_ID);
        ReflectionTestUtils.setField(service, "defaultPackageWeightGrams", 500);
        ReflectionTestUtils.setField(service, "defaultPackageLengthCm", 30);
        ReflectionTestUtils.setField(service, "defaultPackageWidthCm", 20);
        ReflectionTestUtils.setField(service, "defaultPackageHeightCm", 10);

        buyer = Account.builder().id(UUID.randomUUID()).email(EMAIL).build();
        otherBuyer = Account.builder().id(UUID.randomUUID()).email("other@example.com").build();

        orderId = UUID.randomUUID();
        order = Order.builder()
                .id(orderId)
                .orderNo("OD-2026-000008")
                .buyer(buyer)
                .status(OrderStatus.DELIVERED)
                .deliveredAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .subtotal(new BigDecimal("600000"))
                .build();

        returnId = UUID.randomUUID();

        when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(buyer));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
    }

    // ==================== helpers ====================

    private ReturnRequest returnRequest(ReturnRequestStatus status) {
        ReturnRequest r = ReturnRequest.builder()
                .id(returnId)
                .order(order)
                .reason("Không đúng mô tả")
                .status(status)
                .build();
        when(returnRequestRepository.findById(returnId)).thenReturn(Optional.of(r));
        return r;
    }

    private CreateReturnRequestRequest createRequest(String orderIdValue, String reason, List<String> photos) {
        CreateReturnRequestRequest req = mock(CreateReturnRequestRequest.class);
        when(req.getOrderId()).thenReturn(orderIdValue);
        when(req.getReason()).thenReturn(reason);
        when(req.getPhotoUrls()).thenReturn(photos);
        return req;
    }

    private InspectReturnRequest inspectRequest(Boolean valid, String note) {
        InspectReturnRequest req = mock(InspectReturnRequest.class);
        when(req.getValid()).thenReturn(valid);
        when(req.getResolutionNote()).thenReturn(note);
        return req;
    }

    private InvalidReturnChoiceRequest choiceRequest(InvalidReturnChoice choice) {
        InvalidReturnChoiceRequest req = mock(InvalidReturnChoiceRequest.class);
        when(req.getChoice()).thenReturn(choice);
        return req;
    }

    private RejectReturnRequestRequest rejectRequest(String reason) {
        RejectReturnRequestRequest req = mock(RejectReturnRequestRequest.class);
        when(req.getReason()).thenReturn(reason);
        return req;
    }

    /** Giả lập JPA gán id khi save yêu cầu trả hàng mới. */
    private void stubSaveAssignsId() {
        when(returnRequestRepository.save(any(ReturnRequest.class))).thenAnswer(inv -> {
            ReturnRequest r = inv.getArgument(0);
            if (r.getId() == null) {
                ReflectionTestUtils.setField(r, "id", returnId);
            }
            return r;
        });
    }

    private GhnCreateOrderResponse ghnResponse(String orderCode) {
        GhnCreateOrderResponse resp = mock(GhnCreateOrderResponse.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        when(resp.getData().getOrderCode()).thenReturn(orderCode);
        return resp;
    }

    private void attachShippingData() {
        Warehouse warehouse = Warehouse.builder()
                .id(UUID.randomUUID())
                .name("Kho Quận 7")
                .phone("02812345678")
                .address("1 Nguyễn Văn Linh")
                .wardName("Tân Phú")
                .districtName("Quận 7")
                .provinceName("TP.HCM")
                .isNewAddress(false)
                .build();
        order.setWarehouse(warehouse);
        order.setShippingRecipientName("Tạ Thị Ngọc Anh");
        order.setShippingPhone("0912345678");
        order.setShippingAddressDetail("12 Nguyễn Trãi");
        order.setShippingWardName("Bến Thành");
        order.setShippingDistrictName("Quận 1");
        order.setShippingProvinceName("TP.HCM");
        order.setShippingIsNewAddress(false);
    }

    private void stubItems() {
        Product p1 = Product.builder().id("Q7-AK-000845").name("Áo khoác Uniqlo").build();
        Product p2 = Product.builder().id("Q7-AK-000846").name("Đầm Zara").build();
        OrderItem i1 = OrderItem.builder().order(order).product(p1).priceAtOrder(new BigDecimal("350000")).build();
        OrderItem i2 = OrderItem.builder().order(order).product(p2).priceAtOrder(new BigDecimal("250000")).build();
        when(orderItemRepository.findAllByOrder_IdWithProductOrderByIdAsc(orderId)).thenReturn(List.of(i1, i2));
    }

    // ==================== create ====================

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("create: orderId rỗng/trắng -> BadRequest")
    void create_blankOrderId_throws(String blank) {
        CreateReturnRequestRequest req = createRequest(blank, "Lỗi", List.of("a.jpg"));

        assertThatThrownBy(() -> service.create(EMAIL, req)).isInstanceOf(BadRequestException.class);

        verifyNoInteractions(returnRequestRepository);
    }

    @Test
    @DisplayName("create: orderId null -> BadRequest")
    void create_nullOrderId_throws() {
        CreateReturnRequestRequest req = createRequest(null, "Lỗi", List.of("a.jpg"));

        assertThatThrownBy(() -> service.create(EMAIL, req)).isInstanceOf(BadRequestException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    @DisplayName("create: reason rỗng/trắng -> BadRequest")
    void create_blankReason_throws(String blank) {
        CreateReturnRequestRequest req = createRequest(orderId.toString(), blank, List.of("a.jpg"));

        assertThatThrownBy(() -> service.create(EMAIL, req)).isInstanceOf(BadRequestException.class);

        verifyNoInteractions(returnRequestRepository);
    }

    @Test
    @DisplayName("create: photoUrls null -> BadRequest (cần ít nhất 1 ảnh)")
    void create_nullPhotos_throws() {
        CreateReturnRequestRequest req = createRequest(orderId.toString(), "Lỗi", null);

        assertThatThrownBy(() -> service.create(EMAIL, req)).isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("create: photoUrls rỗng -> BadRequest")
    void create_emptyPhotos_throws() {
        CreateReturnRequestRequest req = createRequest(orderId.toString(), "Lỗi", List.of());

        assertThatThrownBy(() -> service.create(EMAIL, req)).isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("create: account không tồn tại -> IllegalArgumentException")
    void create_accountNotFound_throws() {
        when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        CreateReturnRequestRequest req = createRequest(orderId.toString(), "Lỗi", List.of("a.jpg"));

        assertThatThrownBy(() -> service.create(EMAIL, req)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("create: orderId không phải UUID -> BadRequest")
    void create_invalidUuid_throws() {
        CreateReturnRequestRequest req = createRequest("not-a-uuid", "Lỗi", List.of("a.jpg"));

        assertThatThrownBy(() -> service.create(EMAIL, req)).isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("create: không tìm thấy đơn -> IllegalArgumentException")
    void create_orderNotFound_throws() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());
        CreateReturnRequestRequest req = createRequest(orderId.toString(), "Lỗi", List.of("a.jpg"));

        assertThatThrownBy(() -> service.create(EMAIL, req)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("create: đơn của người khác -> Forbidden")
    void create_notOwner_throwsForbidden() {
        order.setBuyer(otherBuyer);
        CreateReturnRequestRequest req = createRequest(orderId.toString(), "Lỗi", List.of("a.jpg"));

        assertThatThrownBy(() -> service.create(EMAIL, req)).isInstanceOf(ForbiddenException.class);

        verify(returnRequestRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"DELIVERED", "COMPLETED"}, mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("create: đơn chưa giao hoặc đã quá hạn xử lý -> ORDER_NOT_RETURNABLE")
    void create_orderNotDelivered_throwsConflict(OrderStatus status) {
        order.setStatus(status);
        CreateReturnRequestRequest req = createRequest(orderId.toString(), "Lỗi", List.of("a.jpg"));

        assertThatThrownBy(() -> service.create(EMAIL, req))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getCode())
                .isEqualTo("ORDER_NOT_RETURNABLE");
    }

    @Test
    @DisplayName("create: DELIVERED nhưng deliveredAt null -> ORDER_NOT_RETURNABLE")
    void create_deliveredAtNull_throwsConflict() {
        order.setDeliveredAt(null);
        CreateReturnRequestRequest req = createRequest(orderId.toString(), "Lỗi", List.of("a.jpg"));

        assertThatThrownBy(() -> service.create(EMAIL, req))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getCode())
                .isEqualTo("ORDER_NOT_RETURNABLE");
    }

    @Test
    @DisplayName("create: quá hạn gửi yêu cầu trả hàng -> RETURN_WINDOW_EXPIRED")
    void create_windowExpired_throwsConflict() {
        order.setDeliveredAt(Instant.now().minus(REQUEST_WINDOW_DAYS + 1, ChronoUnit.DAYS));
        CreateReturnRequestRequest req = createRequest(orderId.toString(), "Lỗi", List.of("a.jpg"));

        assertThatThrownBy(() -> service.create(EMAIL, req))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getCode())
                .isEqualTo("RETURN_WINDOW_EXPIRED");
    }

    @Test
    @DisplayName("create: đơn đã có yêu cầu đang xử lý -> RETURN_REQUEST_EXISTS")
    void create_openRequestExists_throwsConflict() {
        when(returnRequestRepository.existsByOrder_IdAndStatusIn(eq(orderId), anyList())).thenReturn(true);
        CreateReturnRequestRequest req = createRequest(orderId.toString(), "Lỗi", List.of("a.jpg"));

        assertThatThrownBy(() -> service.create(EMAIL, req))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getCode())
                .isEqualTo("RETURN_REQUEST_EXISTS");

        verify(returnRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: kiểm tra yêu cầu đang mở theo đúng 4 trạng thái PENDING/APPROVED/SHIPPED_BACK/INSPECTING")
    void create_checksOpenStatuses() {
        stubSaveAssignsId();
        CreateReturnRequestRequest req = createRequest(orderId.toString(), "Lỗi", List.of("a.jpg"));

        service.create(EMAIL, req);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ReturnRequestStatus>> captor = ArgumentCaptor.forClass(List.class);
        verify(returnRequestRepository).existsByOrder_IdAndStatusIn(eq(orderId), captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(
                ReturnRequestStatus.PENDING, ReturnRequestStatus.APPROVED,
                ReturnRequestStatus.SHIPPED_BACK, ReturnRequestStatus.INSPECTING);
    }

    @Test
    @DisplayName("create: thành công -> lưu PENDING kèm hạn bàn giao tạm, lưu ảnh, trả response")
    void create_success() {
        stubSaveAssignsId();
        List<String> photos = List.of("a.jpg", "b.jpg");
        CreateReturnRequestRequest req = createRequest(orderId.toString(), "Không đúng mô tả", photos);

        Instant before = Instant.now();
        ReturnRequestResponse response = service.create(EMAIL, req);

        ArgumentCaptor<ReturnRequest> captor = ArgumentCaptor.forClass(ReturnRequest.class);
        verify(returnRequestRepository).save(captor.capture());
        ReturnRequest saved = captor.getValue();
        assertThat(saved.getOrder()).isSameAs(order);
        assertThat(saved.getReason()).isEqualTo("Không đúng mô tả");
        assertThat(saved.getStatus()).isEqualTo(ReturnRequestStatus.PENDING);
        assertThat(saved.getHandoverDeadline())
                .isAfter(before.plus(HANDOVER_DEADLINE_DAYS, ChronoUnit.DAYS).minusSeconds(5))
                .isBefore(Instant.now().plus(HANDOVER_DEADLINE_DAYS, ChronoUnit.DAYS).plusSeconds(5));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ReturnRequestPhoto>> photoCaptor = ArgumentCaptor.forClass(List.class);
        verify(returnRequestPhotoRepository).saveAll(photoCaptor.capture());
        assertThat(photoCaptor.getValue()).extracting(ReturnRequestPhoto::getImageUrl)
                .containsExactly("a.jpg", "b.jpg");
        assertThat(photoCaptor.getValue()).allSatisfy(p -> assertThat(p.getReturnRequest()).isSameAs(saved));

        assertThat(response.getId()).isEqualTo(returnId.toString());
        assertThat(response.getOrderId()).isEqualTo(orderId.toString());
        assertThat(response.getOrderNo()).isEqualTo("OD-2026-000008");
        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getReason()).isEqualTo("Không đúng mô tả");
        assertThat(response.getPhotoUrls()).containsExactly("a.jpg", "b.jpg");
    }

    @Test
    @DisplayName("create: đơn đã COMPLETED (buyer đã bấm xác nhận) nhưng còn trong hạn 3 ngày vẫn được trả hàng")
    void create_completedOrderWithinWindow_isAllowed() {
        stubSaveAssignsId();
        order.setStatus(OrderStatus.COMPLETED);
        CreateReturnRequestRequest req = createRequest(orderId.toString(), "Lỗi", List.of("a.jpg"));

        ReturnRequestResponse response = service.create(EMAIL, req);

        assertThat(response.getStatus()).isEqualTo("PENDING");
        verify(returnRequestRepository).save(any());
    }

    @Test
    @DisplayName("create: đúng hạn chót (giao 7 ngày trước trừ vài giây) vẫn được phép")
    void create_justInsideWindow_isAllowed() {
        stubSaveAssignsId();
        order.setDeliveredAt(Instant.now().minus(REQUEST_WINDOW_DAYS, ChronoUnit.DAYS).plusSeconds(60));
        CreateReturnRequestRequest req = createRequest(orderId.toString(), "Lỗi", List.of("a.jpg"));

        ReturnRequestResponse response = service.create(EMAIL, req);

        assertThat(response.getStatus()).isEqualTo("PENDING");
    }

    // ==================== getMyReturnRequests ====================

    @Test
    @DisplayName("getMyReturnRequests: account không tồn tại -> IllegalArgumentException")
    void getMyReturnRequests_accountNotFound_throws() {
        when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyReturnRequests(EMAIL, 0, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("getMyReturnRequests: trả danh sách của chính buyer, kèm ảnh")
    void getMyReturnRequests_success() {
        ReturnRequest r = returnRequest(ReturnRequestStatus.PENDING);
        when(returnRequestRepository.findByOrder_Buyer_IdOrderByCreatedAtDesc(eq(buyer.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(r)));
        when(returnRequestPhotoRepository.findAllByReturnRequest_IdOrderByIdAsc(returnId))
                .thenReturn(List.of(ReturnRequestPhoto.builder().returnRequest(r).imageUrl("a.jpg").build()));

        PageResponse<ReturnRequestResponse> result = service.getMyReturnRequests(EMAIL, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(returnId.toString());
        assertThat(result.getContent().get(0).getPhotoUrls()).containsExactly("a.jpg");
    }

    @Test
    @DisplayName("getMyReturnRequests: page âm -> 0, size < 1 -> 1")
    void getMyReturnRequests_clampsPaging() {
        when(returnRequestRepository.findByOrder_Buyer_IdOrderByCreatedAtDesc(any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.getMyReturnRequests(EMAIL, -5, 0);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(returnRequestRepository).findByOrder_Buyer_IdOrderByCreatedAtDesc(eq(buyer.getId()), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(1);
    }

    // ==================== getMyReturnRequest ====================

    @Test
    @DisplayName("getMyReturnRequest: không tìm thấy -> IllegalArgumentException")
    void getMyReturnRequest_notFound_throws() {
        when(returnRequestRepository.findById(returnId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyReturnRequest(EMAIL, returnId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("getMyReturnRequest: của người khác -> Forbidden")
    void getMyReturnRequest_notOwner_throwsForbidden() {
        returnRequest(ReturnRequestStatus.PENDING);
        order.setBuyer(otherBuyer);

        assertThatThrownBy(() -> service.getMyReturnRequest(EMAIL, returnId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("getMyReturnRequest: của mình -> trả chi tiết kèm ảnh và số tiền hoàn")
    void getMyReturnRequest_success() {
        ReturnRequest r = returnRequest(ReturnRequestStatus.VALID);
        r.setInvalidReturnChoice(null);
        order.setRefundAmount(new BigDecimal("625000"));
        when(returnRequestPhotoRepository.findAllByReturnRequest_IdOrderByIdAsc(returnId))
                .thenReturn(List.of(ReturnRequestPhoto.builder().returnRequest(r).imageUrl("x.jpg").build()));

        ReturnRequestResponse response = service.getMyReturnRequest(EMAIL, returnId);

        assertThat(response.getId()).isEqualTo(returnId.toString());
        assertThat(response.getStatus()).isEqualTo("VALID");
        assertThat(response.getPhotoUrls()).containsExactly("x.jpg");
        assertThat(response.getRefundAmount()).isEqualByComparingTo("625000");
        assertThat(response.getInvalidReturnChoice()).isNull();
    }

    // ==================== getQueue ====================

    @SuppressWarnings("unchecked")
    private List<ReturnRequestStatus> captureQueueStatuses() {
        ArgumentCaptor<List<ReturnRequestStatus>> captor = ArgumentCaptor.forClass(List.class);
        verify(returnRequestRepository).findByStatusInOrderByCreatedAtAsc(captor.capture(), any(Pageable.class));
        return captor.getValue();
    }

    @Test
    @DisplayName("getQueue: không truyền status -> lấy 4 trạng thái đang mở")
    void getQueue_noStatus_usesOpenStatuses() {
        when(returnRequestRepository.findByStatusInOrderByCreatedAtAsc(anyList(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.getQueue(null, 0, 10);

        assertThat(captureQueueStatuses()).containsExactlyInAnyOrder(
                ReturnRequestStatus.PENDING, ReturnRequestStatus.APPROVED,
                ReturnRequestStatus.SHIPPED_BACK, ReturnRequestStatus.INSPECTING);
    }

    @Test
    @DisplayName("getQueue: status trắng -> coi như không truyền")
    void getQueue_blankStatus_usesOpenStatuses() {
        when(returnRequestRepository.findByStatusInOrderByCreatedAtAsc(anyList(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.getQueue("   ", 0, 10);

        assertThat(captureQueueStatuses()).hasSize(4);
    }

    @Test
    @DisplayName("getQueue: status chữ thường, có khoảng trắng -> parse được")
    void getQueue_statusIsNormalized() {
        when(returnRequestRepository.findByStatusInOrderByCreatedAtAsc(anyList(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.getQueue("  inspecting ", 0, 10);

        assertThat(captureQueueStatuses()).containsExactly(ReturnRequestStatus.INSPECTING);
    }

    @Test
    @DisplayName("getQueue: status không hợp lệ -> BadRequest")
    void getQueue_invalidStatus_throws() {
        assertThatThrownBy(() -> service.getQueue("NOPE", 0, 10)).isInstanceOf(BadRequestException.class);

        verify(returnRequestRepository, never()).findByStatusInOrderByCreatedAtAsc(any(), any());
    }

    @Test
    @DisplayName("getQueue: map kết quả kèm ảnh, page âm / size 0 được chặn về 0 / 1")
    void getQueue_mapsResultAndClampsPaging() {
        ReturnRequest r = returnRequest(ReturnRequestStatus.PENDING);
        when(returnRequestRepository.findByStatusInOrderByCreatedAtAsc(anyList(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(r)));

        PageResponse<ReturnRequestResponse> result = service.getQueue(null, -1, 0);

        assertThat(result.getContent()).hasSize(1);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(returnRequestRepository).findByStatusInOrderByCreatedAtAsc(anyList(), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(1);
    }

    // ==================== getDetailForModerator ====================

    @Test
    @DisplayName("getDetailForModerator: không tìm thấy -> IllegalArgumentException")
    void getDetailForModerator_notFound_throws() {
        when(returnRequestRepository.findById(returnId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetailForModerator(returnId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("getDetailForModerator: không kiểm tra chủ sở hữu, trả đầy đủ thông tin")
    void getDetailForModerator_success() {
        ReturnRequest r = returnRequest(ReturnRequestStatus.APPROVED);
        r.setGhnReturnTrackingCode("GHN123");
        r.setInvalidReturnChoice(InvalidReturnChoice.SHIP_BACK);
        order.setBuyer(otherBuyer);

        ReturnRequestResponse response = service.getDetailForModerator(returnId);

        assertThat(response.getStatus()).isEqualTo("APPROVED");
        assertThat(response.getGhnReturnTrackingCode()).isEqualTo("GHN123");
        assertThat(response.getInvalidReturnChoice()).isEqualTo("SHIP_BACK");
    }

    // ==================== approve ====================

    @Test
    @DisplayName("approve: không tìm thấy yêu cầu -> IllegalArgumentException")
    void approve_notFound_throws() {
        when(returnRequestRepository.findById(returnId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(returnId)).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(ghnApi);
    }

    @Test
    @DisplayName("approve: đã có mã vận đơn trả -> trả kết quả cũ, KHÔNG gọi GHN lần nữa, không save")
    void approve_alreadyHasTrackingCode_isIdempotent() {
        ReturnRequest r = returnRequest(ReturnRequestStatus.APPROVED);
        r.setGhnReturnTrackingCode("GHN-OLD");

        ReturnRequestResponse response = service.approve(returnId);

        assertThat(response.getGhnReturnTrackingCode()).isEqualTo("GHN-OLD");
        verifyNoInteractions(ghnApi);
        verify(returnRequestRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = ReturnRequestStatus.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("approve: không ở trạng thái PENDING -> RETURN_NOT_PENDING")
    void approve_notPending_throwsConflict(ReturnRequestStatus status) {
        returnRequest(status);

        assertThatThrownBy(() -> service.approve(returnId))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getCode())
                .isEqualTo("RETURN_NOT_PENDING");

        verifyNoInteractions(ghnApi);
    }

    @Test
    @DisplayName("approve: GHN ném lỗi -> GhnException, không đổi trạng thái, không save")
    void approve_ghnThrows_throwsGhnException() {
        ReturnRequest r = returnRequest(ReturnRequestStatus.PENDING);
        attachShippingData();
        stubItems();
        when(ghnApi.createOrder(anyString(), anyString(), any(GhnCreateOrderRequest.class)))
                .thenThrow(new RuntimeException("timeout"));

        assertThatThrownBy(() -> service.approve(returnId)).isInstanceOf(GhnException.class);

        assertThat(r.getStatus()).isEqualTo(ReturnRequestStatus.PENDING);
        assertThat(r.getGhnReturnTrackingCode()).isNull();
        verify(returnRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("approve: GHN trả null -> GhnException")
    void approve_ghnReturnsNull_throwsGhnException() {
        returnRequest(ReturnRequestStatus.PENDING);
        attachShippingData();
        stubItems();
        when(ghnApi.createOrder(anyString(), anyString(), any(GhnCreateOrderRequest.class))).thenReturn(null);

        assertThatThrownBy(() -> service.approve(returnId)).isInstanceOf(GhnException.class);

        verify(returnRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("approve: GHN trả data null -> GhnException")
    void approve_ghnDataNull_throwsGhnException() {
        returnRequest(ReturnRequestStatus.PENDING);
        attachShippingData();
        stubItems();
        GhnCreateOrderResponse resp = mock(GhnCreateOrderResponse.class);
        when(resp.getData()).thenReturn(null);
        when(ghnApi.createOrder(anyString(), anyString(), any(GhnCreateOrderRequest.class))).thenReturn(resp);

        assertThatThrownBy(() -> service.approve(returnId)).isInstanceOf(GhnException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("approve: GHN trả order_code rỗng -> GhnException")
    void approve_ghnBlankOrderCode_throwsGhnException(String blank) {
        returnRequest(ReturnRequestStatus.PENDING);
        attachShippingData();
        stubItems();

        GhnCreateOrderResponse response = ghnResponse(blank);

        when(ghnApi.createOrder(
                anyString(),
                anyString(),
                any(GhnCreateOrderRequest.class)
        )).thenReturn(response);

        assertThatThrownBy(() -> service.approve(returnId))
                .isInstanceOf(GhnException.class);

        verify(returnRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("approve: thành công -> APPROVED, lưu mã vận đơn, đặt lại hạn bàn giao")
    void approve_success_updatesRequest() {
        ReturnRequest r = returnRequest(ReturnRequestStatus.PENDING);
        r.setHandoverDeadline(Instant.now().plus(10, ChronoUnit.MINUTES));

        attachShippingData();
        stubItems();

        GhnCreateOrderResponse response = ghnResponse("GHNRT001");

        when(ghnApi.createOrder(
                eq(GHN_TOKEN),
                eq(GHN_SHOP_ID),
                any(GhnCreateOrderRequest.class)
        )).thenReturn(response);

        Instant before = Instant.now();

        ReturnRequestResponse responseResult = service.approve(returnId);

        assertThat(r.getStatus()).isEqualTo(ReturnRequestStatus.APPROVED);
        assertThat(r.getGhnReturnTrackingCode()).isEqualTo("GHNRT001");

        assertThat(r.getHandoverDeadline())
                .isAfter(before.plus(HANDOVER_DEADLINE_DAYS, ChronoUnit.DAYS).minusSeconds(5));

        verify(returnRequestRepository).save(r);

        assertThat(responseResult.getStatus()).isEqualTo("APPROVED");
        assertThat(responseResult.getGhnReturnTrackingCode()).isEqualTo("GHNRT001");
    }

    @Test
    @DisplayName("approve: vận đơn chiều ngược - lấy tại địa chỉ người mua, giao về kho, COD = 0")
    void approve_buildsReverseShipmentRequest() {
        returnRequest(ReturnRequestStatus.PENDING);
        attachShippingData();
        stubItems();

        GhnCreateOrderResponse response = ghnResponse("GHNRT001");

        when(ghnApi.createOrder(
                anyString(),
                anyString(),
                any(GhnCreateOrderRequest.class)
        )).thenReturn(response);

        service.approve(returnId);

        ArgumentCaptor<GhnCreateOrderRequest> captor =
                ArgumentCaptor.forClass(GhnCreateOrderRequest.class);

        verify(ghnApi).createOrder(
                eq(GHN_TOKEN),
                eq(GHN_SHOP_ID),
                captor.capture()
        );

        GhnCreateOrderRequest ghn = captor.getValue();

        assertThat(ghn.getClientOrderCode()).isEqualTo("RT-" + returnId);

        // from = người mua
        assertThat(ghn.getFromName()).isEqualTo("Tạ Thị Ngọc Anh");
        assertThat(ghn.getFromPhone()).isEqualTo("0912345678");
        assertThat(ghn.getFromAddress()).isEqualTo("12 Nguyễn Trãi");
        assertThat(ghn.getFromWardName()).isEqualTo("Bến Thành");
        assertThat(ghn.getFromDistrictName()).isEqualTo("Quận 1");
        assertThat(ghn.getFromProvinceName()).isEqualTo("TP.HCM");

        // to = kho
        assertThat(ghn.getToName()).isEqualTo("Kho Quận 7");
        assertThat(ghn.getToPhone()).isEqualTo("02812345678");
        assertThat(ghn.getToAddress()).isEqualTo("1 Nguyễn Văn Linh");
        assertThat(ghn.getToWardName()).isEqualTo("Tân Phú");
        assertThat(ghn.getToDistrictName()).isEqualTo("Quận 7");
        assertThat(ghn.getToProvinceName()).isEqualTo("TP.HCM");

        // gói hàng và thanh toán
        assertThat(ghn.getWeight()).isEqualTo(500);
        assertThat(ghn.getLength()).isEqualTo(30);
        assertThat(ghn.getWidth()).isEqualTo(20);
        assertThat(ghn.getHeight()).isEqualTo(10);
        assertThat(ghn.getCodAmount()).isZero();
        assertThat(ghn.getOrderValue()).isEqualTo(600000);

        // items
        assertThat(ghn.getItems()).hasSize(2);
        assertThat(ghn.getItems().get(0).getName()).isEqualTo("Áo khoác Uniqlo");
        assertThat(ghn.getItems().get(0).getCode()).isEqualTo("Q7-AK-000845");
        assertThat(ghn.getItems().get(0).getQuantity()).isEqualTo(1);
        assertThat(ghn.getItems().get(0).getPrice()).isEqualTo(350000);
    }

    @Test
    @DisplayName("approve: địa chỉ mới (2 cấp) -> bỏ district ở phía người mua và phía kho")
    void approve_newAddressFormat_dropsDistrict() {
        returnRequest(ReturnRequestStatus.PENDING);
        attachShippingData();

        order.setShippingIsNewAddress(true);
        order.getWarehouse().setIsNewAddress(true);

        stubItems();

        GhnCreateOrderResponse response = ghnResponse("GHNRT001");

        when(ghnApi.createOrder(
                anyString(),
                anyString(),
                any(GhnCreateOrderRequest.class)
        )).thenReturn(response);

        service.approve(returnId);

        ArgumentCaptor<GhnCreateOrderRequest> captor =
                ArgumentCaptor.forClass(GhnCreateOrderRequest.class);

        verify(ghnApi).createOrder(
                anyString(),
                anyString(),
                captor.capture()
        );

        assertThat(captor.getValue().getFromDistrictName()).isEmpty();
        assertThat(captor.getValue().getToDistrictName()).isEmpty();
    }

    // ==================== reject ====================

    @Test
    @DisplayName("reject: request null -> BadRequest")
    void reject_nullRequest_throws() {
        assertThatThrownBy(() -> service.reject(returnId, null)).isInstanceOf(BadRequestException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    @DisplayName("reject: reason rỗng/trắng -> BadRequest")
    void reject_blankReason_throws(String blank) {
        assertThatThrownBy(() -> service.reject(returnId, rejectRequest(blank)))
                .isInstanceOf(BadRequestException.class);

        verify(returnRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("reject: không tìm thấy -> IllegalArgumentException")
    void reject_notFound_throws() {
        when(returnRequestRepository.findById(returnId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reject(returnId, rejectRequest("Ảnh không rõ")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(value = ReturnRequestStatus.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("reject: không ở trạng thái PENDING -> RETURN_NOT_PENDING")
    void reject_notPending_throwsConflict(ReturnRequestStatus status) {
        returnRequest(status);

        assertThatThrownBy(() -> service.reject(returnId, rejectRequest("Ảnh không rõ")))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getCode())
                .isEqualTo("RETURN_NOT_PENDING");

        verify(returnRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("reject: thành công -> CANCELLED, lưu lý do, resolvedAt")
    void reject_success() {
        ReturnRequest r = returnRequest(ReturnRequestStatus.PENDING);

        Instant before = Instant.now();
        ReturnRequestResponse response = service.reject(returnId, rejectRequest("Ảnh không rõ"));

        assertThat(r.getStatus()).isEqualTo(ReturnRequestStatus.CANCELLED);
        assertThat(r.getResolutionNote()).isEqualTo("Ảnh không rõ");
        assertThat(r.getResolvedAt()).isNotNull().isAfterOrEqualTo(before);
        verify(returnRequestRepository).save(r);
        assertThat(response.getStatus()).isEqualTo("CANCELLED");
        assertThat(response.getResolutionNote()).isEqualTo("Ảnh không rõ");
        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("reject: đơn đã quá hạn trả hàng (autoCompleteDeadline) -> trả tiền người ký gửi ngay")
    void reject_afterReturnWindowPassed_paysOutSellerImmediately() {
        order.setAutoCompleteDeadline(Instant.now().minus(1, ChronoUnit.HOURS));
        returnRequest(ReturnRequestStatus.PENDING);

        service.reject(returnId, rejectRequest("Ảnh không rõ"));

        verify(orderService).payoutOrder(eq(orderId), any());
    }

    @Test
    @DisplayName("reject: đơn còn trong hạn trả hàng (autoCompleteDeadline) -> chưa trả tiền, để job xử lý sau")
    void reject_withinReturnWindow_doesNotPayOutYet() {
        order.setAutoCompleteDeadline(Instant.now().plus(1, ChronoUnit.HOURS));
        returnRequest(ReturnRequestStatus.PENDING);

        service.reject(returnId, rejectRequest("Ảnh không rõ"));

        verifyNoInteractions(orderService);
    }

    // ==================== confirmHandover ====================

    @Test
    @DisplayName("confirmHandover: account không tồn tại -> IllegalArgumentException")
    void confirmHandover_accountNotFound_throws() {
        when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmHandover(EMAIL, returnId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("confirmHandover: yêu cầu không tồn tại -> IllegalArgumentException")
    void confirmHandover_requestNotFound_throws() {
        when(returnRequestRepository.findById(returnId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmHandover(EMAIL, returnId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("confirmHandover: của người khác -> Forbidden")
    void confirmHandover_notOwner_throwsForbidden() {
        returnRequest(ReturnRequestStatus.APPROVED);
        order.setBuyer(otherBuyer);

        assertThatThrownBy(() -> service.confirmHandover(EMAIL, returnId))
                .isInstanceOf(ForbiddenException.class);

        verify(returnRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("confirmHandover: đã SHIPPED_BACK (vd webhook GHN tới trước) -> trả kết quả cũ, không save")
    void confirmHandover_alreadyShippedBack_isIdempotent() {
        returnRequest(ReturnRequestStatus.SHIPPED_BACK);

        ReturnRequestResponse response = service.confirmHandover(EMAIL, returnId);

        assertThat(response.getStatus()).isEqualTo("SHIPPED_BACK");
        verify(returnRequestRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = ReturnRequestStatus.class, names = {"APPROVED", "SHIPPED_BACK"}, mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("confirmHandover: chưa được duyệt -> RETURN_NOT_APPROVED")
    void confirmHandover_notApproved_throwsConflict(ReturnRequestStatus status) {
        returnRequest(status);

        assertThatThrownBy(() -> service.confirmHandover(EMAIL, returnId))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getCode())
                .isEqualTo("RETURN_NOT_APPROVED");
    }

    @Test
    @DisplayName("confirmHandover: APPROVED -> SHIPPED_BACK")
    void confirmHandover_success() {
        ReturnRequest r = returnRequest(ReturnRequestStatus.APPROVED);

        ReturnRequestResponse response = service.confirmHandover(EMAIL, returnId);

        assertThat(r.getStatus()).isEqualTo(ReturnRequestStatus.SHIPPED_BACK);
        verify(returnRequestRepository).save(r);
        assertThat(response.getStatus()).isEqualTo("SHIPPED_BACK");
    }

    // ==================== startInspection ====================

    @Test
    @DisplayName("startInspection: không tìm thấy -> IllegalArgumentException")
    void startInspection_notFound_throws() {
        when(returnRequestRepository.findById(returnId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startInspection(returnId)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("startInspection: đã INSPECTING -> idempotent, không save")
    void startInspection_alreadyInspecting_isIdempotent() {
        returnRequest(ReturnRequestStatus.INSPECTING);

        ReturnRequestResponse response = service.startInspection(returnId);

        assertThat(response.getStatus()).isEqualTo("INSPECTING");
        verify(returnRequestRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = ReturnRequestStatus.class, names = {"SHIPPED_BACK", "INSPECTING"}, mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("startInspection: hàng chưa được bàn giao -> RETURN_NOT_SHIPPED_BACK")
    void startInspection_notShippedBack_throwsConflict(ReturnRequestStatus status) {
        returnRequest(status);

        assertThatThrownBy(() -> service.startInspection(returnId))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getCode())
                .isEqualTo("RETURN_NOT_SHIPPED_BACK");
    }

    @Test
    @DisplayName("startInspection: SHIPPED_BACK -> INSPECTING")
    void startInspection_success() {
        ReturnRequest r = returnRequest(ReturnRequestStatus.SHIPPED_BACK);

        ReturnRequestResponse response = service.startInspection(returnId);

        assertThat(r.getStatus()).isEqualTo(ReturnRequestStatus.INSPECTING);
        verify(returnRequestRepository).save(r);
        assertThat(response.getStatus()).isEqualTo("INSPECTING");
    }

    // ==================== inspect ====================

    @Test
    @DisplayName("inspect: request null -> BadRequest")
    void inspect_nullRequest_throws() {
        assertThatThrownBy(() -> service.inspect(returnId, null)).isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("inspect: valid null -> BadRequest")
    void inspect_nullValid_throws() {
        assertThatThrownBy(() -> service.inspect(returnId, inspectRequest(null, "ok")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("inspect: không tìm thấy -> IllegalArgumentException")
    void inspect_notFound_throws() {
        when(returnRequestRepository.findById(returnId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.inspect(returnId, inspectRequest(true, "ok")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(value = ReturnRequestStatus.class, names = "INSPECTING", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("inspect: chưa ở trạng thái INSPECTING -> RETURN_NOT_INSPECTING, không hoàn tiền")
    void inspect_notInspecting_throwsConflict(ReturnRequestStatus status) {
        returnRequest(status);

        assertThatThrownBy(() -> service.inspect(returnId, inspectRequest(true, "ok")))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getCode())
                .isEqualTo("RETURN_NOT_INSPECTING");

        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("inspect: hợp lệ -> VALID, resolvedAt, lưu TRƯỚC rồi hoàn tiền cho đơn")
    void inspect_valid_refundsOrder() {
        ReturnRequest r = returnRequest(ReturnRequestStatus.INSPECTING);

        Instant before = Instant.now();
        ReturnRequestResponse response = service.inspect(returnId, inspectRequest(true, "Hàng đúng như khách báo"));

        assertThat(r.getStatus()).isEqualTo(ReturnRequestStatus.VALID);
        assertThat(r.getResolutionNote()).isEqualTo("Hàng đúng như khách báo");
        assertThat(r.getResolvedAt()).isNotNull().isAfterOrEqualTo(before);
        assertThat(r.getPickupDeadline()).isNull();

        InOrder inOrder = inOrder(returnRequestRepository, orderService);
        inOrder.verify(returnRequestRepository).save(r);
        inOrder.verify(orderService).refundOrderForReturn(orderId, "Hoàn tiền trả hàng hợp lệ - đơn OD-2026-000008");

        assertThat(response.getStatus()).isEqualTo("VALID");
    }

    @Test
    @DisplayName("inspect: không hợp lệ -> INVALID, đặt hạn nhận lại hàng, chưa đóng (resolvedAt null), "
            + "KHÔNG hoàn tiền người mua nhưng TRẢ TIỀN NGAY cho người ký gửi")
    void inspect_invalid_setsPickupDeadlineAndPaysOutSellerWithoutRefunding() {
        ReturnRequest r = returnRequest(ReturnRequestStatus.INSPECTING);

        Instant before = Instant.now();
        ReturnRequestResponse response = service.inspect(returnId, inspectRequest(false, "Hàng đã qua sử dụng"));

        assertThat(r.getStatus()).isEqualTo(ReturnRequestStatus.INVALID);
        assertThat(r.getResolutionNote()).isEqualTo("Hàng đã qua sử dụng");
        assertThat(r.getResolvedAt()).isNull();
        assertThat(r.getPickupDeadline())
                .isAfter(before.plus(PICKUP_DEADLINE_DAYS, ChronoUnit.DAYS).minusSeconds(5))
                .isBefore(Instant.now().plus(PICKUP_DEADLINE_DAYS, ChronoUnit.DAYS).plusSeconds(5));
        verify(returnRequestRepository).save(r);

        verify(orderService, never()).refundOrderForReturn(any(), any());
        verify(orderService).payoutOrder(eq(orderId), any());

        assertThat(response.getStatus()).isEqualTo("INVALID");
        assertThat(response.getPickupDeadline()).isEqualTo(r.getPickupDeadline());
    }

    // ==================== chooseInvalidReturn ====================

    @Test
    @DisplayName("chooseInvalidReturn: request null -> BadRequest")
    void chooseInvalidReturn_nullRequest_throws() {
        assertThatThrownBy(() -> service.chooseInvalidReturn(EMAIL, returnId, null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("chooseInvalidReturn: choice null -> BadRequest")
    void chooseInvalidReturn_nullChoice_throws() {
        assertThatThrownBy(() -> service.chooseInvalidReturn(EMAIL, returnId, choiceRequest(null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("chooseInvalidReturn: account không tồn tại -> IllegalArgumentException")
    void chooseInvalidReturn_accountNotFound_throws() {
        when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.chooseInvalidReturn(EMAIL, returnId,
                choiceRequest(InvalidReturnChoice.SHIP_BACK)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("chooseInvalidReturn: của người khác -> Forbidden")
    void chooseInvalidReturn_notOwner_throwsForbidden() {
        returnRequest(ReturnRequestStatus.INVALID);
        order.setBuyer(otherBuyer);

        assertThatThrownBy(() -> service.chooseInvalidReturn(EMAIL, returnId,
                choiceRequest(InvalidReturnChoice.SHIP_BACK)))
                .isInstanceOf(ForbiddenException.class);

        verify(returnRequestRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = ReturnRequestStatus.class, names = "INVALID", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("chooseInvalidReturn: yêu cầu không ở trạng thái INVALID -> RETURN_NOT_INVALID")
    void chooseInvalidReturn_notInvalid_throwsConflict(ReturnRequestStatus status) {
        returnRequest(status);

        assertThatThrownBy(() -> service.chooseInvalidReturn(EMAIL, returnId,
                choiceRequest(InvalidReturnChoice.SHIP_BACK)))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getCode())
                .isEqualTo("RETURN_NOT_INVALID");
    }

    @Test
    @DisplayName("chooseInvalidReturn: quá hạn nhận lại hàng -> RETURN_PICKUP_EXPIRED")
    void chooseInvalidReturn_pickupExpired_throwsConflict() {
        ReturnRequest r = returnRequest(ReturnRequestStatus.INVALID);
        r.setPickupDeadline(Instant.now().minus(1, ChronoUnit.HOURS));

        assertThatThrownBy(() -> service.chooseInvalidReturn(EMAIL, returnId,
                choiceRequest(InvalidReturnChoice.WAREHOUSE_PICKUP)))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getCode())
                .isEqualTo("RETURN_PICKUP_EXPIRED");

        assertThat(r.getInvalidReturnChoice()).isNull();
        verify(returnRequestRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(InvalidReturnChoice.class)
    @DisplayName("chooseInvalidReturn: còn hạn -> lưu lựa chọn")
    void chooseInvalidReturn_success(InvalidReturnChoice choice) {
        ReturnRequest r = returnRequest(ReturnRequestStatus.INVALID);
        r.setPickupDeadline(Instant.now().plus(2, ChronoUnit.DAYS));

        ReturnRequestResponse response = service.chooseInvalidReturn(EMAIL, returnId, choiceRequest(choice));

        assertThat(r.getInvalidReturnChoice()).isEqualTo(choice);
        verify(returnRequestRepository).save(r);
        assertThat(response.getInvalidReturnChoice()).isEqualTo(choice.name());
    }

    @Test
    @DisplayName("chooseInvalidReturn: pickupDeadline null -> không coi là quá hạn")
    void chooseInvalidReturn_nullPickupDeadline_isAllowed() {
        ReturnRequest r = returnRequest(ReturnRequestStatus.INVALID);
        r.setPickupDeadline(null);

        service.chooseInvalidReturn(EMAIL, returnId, choiceRequest(InvalidReturnChoice.SHIP_BACK));

        assertThat(r.getInvalidReturnChoice()).isEqualTo(InvalidReturnChoice.SHIP_BACK);
    }

    // ==================== applyGhnReturnStatus ====================

    @Test
    @DisplayName("applyGhnReturnStatus: mã vận đơn không khớp yêu cầu nào -> false, không làm gì")
    void applyGhnStatus_unknownTrackingCode_returnsFalse() {
        when(returnRequestRepository.findByGhnReturnTrackingCode("UNKNOWN")).thenReturn(Optional.empty());

        boolean handled = service.applyGhnReturnStatus("UNKNOWN", "picked", null);

        assertThat(handled).isFalse();
        verify(returnRequestRepository, never()).save(any());
        verifyNoInteractions(orderService);
    }

    private ReturnRequest withTrackingCode(ReturnRequestStatus status) {
        ReturnRequest r = returnRequest(status);
        r.setGhnReturnTrackingCode("GHNRT001");
        when(returnRequestRepository.findByGhnReturnTrackingCode("GHNRT001")).thenReturn(Optional.of(r));
        return r;
    }

    @Test
    @DisplayName("applyGhnReturnStatus: picked khi APPROVED -> SHIPPED_BACK")
    void applyGhnStatus_picked_whenApproved_movesToShippedBack() {
        ReturnRequest r = withTrackingCode(ReturnRequestStatus.APPROVED);

        boolean handled = service.applyGhnReturnStatus("GHNRT001", "picked", null);

        assertThat(handled).isTrue();
        assertThat(r.getStatus()).isEqualTo(ReturnRequestStatus.SHIPPED_BACK);
        verify(returnRequestRepository).save(r);
    }

    @Test
    @DisplayName("applyGhnReturnStatus: trạng thái GHN không phân biệt hoa thường (PICKED)")
    void applyGhnStatus_isCaseInsensitive() {
        ReturnRequest r = withTrackingCode(ReturnRequestStatus.APPROVED);

        service.applyGhnReturnStatus("GHNRT001", "PICKED", null);

        assertThat(r.getStatus()).isEqualTo(ReturnRequestStatus.SHIPPED_BACK);
    }

    @ParameterizedTest
    @EnumSource(value = ReturnRequestStatus.class, names = "APPROVED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("applyGhnReturnStatus: picked khi không phải APPROVED -> không đổi, vẫn trả true")
    void applyGhnStatus_picked_whenNotApproved_isNoop(ReturnRequestStatus status) {
        ReturnRequest r = withTrackingCode(status);

        boolean handled = service.applyGhnReturnStatus("GHNRT001", "picked", null);

        assertThat(handled).isTrue();
        assertThat(r.getStatus()).isEqualTo(status);
        verify(returnRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("applyGhnReturnStatus: delivered khi SHIPPED_BACK -> INSPECTING")
    void applyGhnStatus_delivered_whenShippedBack_movesToInspecting() {
        ReturnRequest r = withTrackingCode(ReturnRequestStatus.SHIPPED_BACK);

        boolean handled = service.applyGhnReturnStatus("GHNRT001", "delivered", null);

        assertThat(handled).isTrue();
        assertThat(r.getStatus()).isEqualTo(ReturnRequestStatus.INSPECTING);
        verify(returnRequestRepository).save(r);
    }

    @ParameterizedTest
    @EnumSource(value = ReturnRequestStatus.class, names = "SHIPPED_BACK", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("applyGhnReturnStatus: delivered khi không phải SHIPPED_BACK -> không đổi")
    void applyGhnStatus_delivered_whenNotShippedBack_isNoop(ReturnRequestStatus status) {
        ReturnRequest r = withTrackingCode(status);

        boolean handled = service.applyGhnReturnStatus("GHNRT001", "delivered", null);

        assertThat(handled).isTrue();
        assertThat(r.getStatus()).isEqualTo(status);
        verify(returnRequestRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"lost", "damage", "scrap"})
    @DisplayName("applyGhnReturnStatus: GHN báo thất lạc/hư hỏng/tiêu huỷ khi đang mở -> LOST và hoàn tiền 100%")
    void applyGhnStatus_lost_marksLostAndRefunds(String ghnStatus) {
        ReturnRequest r = withTrackingCode(ReturnRequestStatus.SHIPPED_BACK);

        Instant before = Instant.now();
        boolean handled = service.applyGhnReturnStatus("GHNRT001", ghnStatus, "Kiện hàng bị mất");

        assertThat(handled).isTrue();
        assertThat(r.getStatus()).isEqualTo(ReturnRequestStatus.LOST);
        assertThat(r.getResolutionNote()).isEqualTo("Kiện hàng bị mất");
        assertThat(r.getResolvedAt()).isNotNull().isAfterOrEqualTo(before);

        InOrder inOrder = inOrder(returnRequestRepository, orderService);
        inOrder.verify(returnRequestRepository).save(r);
        inOrder.verify(orderService)
                .refundOrderForReturn(orderId, "Hoàn tiền do hàng trả thất lạc - đơn OD-2026-000008");
    }

    @ParameterizedTest
    @EnumSource(value = ReturnRequestStatus.class, names = {"APPROVED", "SHIPPED_BACK", "INSPECTING", "PENDING"})
    @DisplayName("applyGhnReturnStatus: thất lạc áp dụng cho mọi trạng thái đang mở")
    void applyGhnStatus_lost_appliesToAllOpenStatuses(ReturnRequestStatus status) {
        ReturnRequest r = withTrackingCode(status);

        service.applyGhnReturnStatus("GHNRT001", "lost", null);

        assertThat(r.getStatus()).isEqualTo(ReturnRequestStatus.LOST);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("applyGhnReturnStatus: thất lạc không kèm reason -> ghi chú dùng chính mã trạng thái GHN")
    void applyGhnStatus_lost_withoutReason_usesStatusAsNote(String blankReason) {
        ReturnRequest r = withTrackingCode(ReturnRequestStatus.SHIPPED_BACK);

        service.applyGhnReturnStatus("GHNRT001", "lost", blankReason);

        assertThat(r.getResolutionNote()).isEqualTo("lost");
    }

    @Test
    @DisplayName("applyGhnReturnStatus: thất lạc với reason null -> ghi chú là mã trạng thái")
    void applyGhnStatus_lost_nullReason_usesStatusAsNote() {
        ReturnRequest r = withTrackingCode(ReturnRequestStatus.APPROVED);

        service.applyGhnReturnStatus("GHNRT001", "damage", null);

        assertThat(r.getResolutionNote()).isEqualTo("damage");
    }

    @ParameterizedTest
    @EnumSource(value = ReturnRequestStatus.class, names = {"VALID", "INVALID", "CANCELLED", "LOST"})
    @DisplayName("applyGhnReturnStatus: yêu cầu đã đóng -> thất lạc không đổi trạng thái, không hoàn tiền lần nữa")
    void applyGhnStatus_lost_whenClosed_isNoop(ReturnRequestStatus status) {
        ReturnRequest r = withTrackingCode(status);

        boolean handled = service.applyGhnReturnStatus("GHNRT001", "lost", "mất");

        assertThat(handled).isTrue();
        assertThat(r.getStatus()).isEqualTo(status);
        verify(returnRequestRepository, never()).save(any());
        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("applyGhnReturnStatus: status null -> không làm gì nhưng vẫn trả true")
    void applyGhnStatus_nullStatus_isNoop() {
        ReturnRequest r = withTrackingCode(ReturnRequestStatus.APPROVED);

        boolean handled = service.applyGhnReturnStatus("GHNRT001", null, null);

        assertThat(handled).isTrue();
        assertThat(r.getStatus()).isEqualTo(ReturnRequestStatus.APPROVED);
        verify(returnRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("applyGhnReturnStatus: trạng thái GHN khác (vd transporting) -> không làm gì")
    void applyGhnStatus_otherStatus_isNoop() {
        ReturnRequest r = withTrackingCode(ReturnRequestStatus.SHIPPED_BACK);

        boolean handled = service.applyGhnReturnStatus("GHNRT001", "transporting", null);

        assertThat(handled).isTrue();
        assertThat(r.getStatus()).isEqualTo(ReturnRequestStatus.SHIPPED_BACK);
        verify(returnRequestRepository, never()).save(any());
        verifyNoInteractions(orderService);
    }
}
