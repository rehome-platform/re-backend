package com.rehome.rehome.serviceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rehome.rehome.client.GhnApi;
import com.rehome.rehome.client.GhnShippingFeeCalculator;
import com.rehome.rehome.component.VoucherCalculator;
import com.rehome.rehome.constant.SystemConfigKeys;
import com.rehome.rehome.dto.request.CreateOrderRequest;
import com.rehome.rehome.dto.response.OrderResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.MemberVoucherStatus;
import com.rehome.rehome.entity.enums.OrderStatus;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.VoucherDiscountKind;
import com.rehome.rehome.entity.enums.VoucherType;
import com.rehome.rehome.entity.enums.WalletTransactionType;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.Order;
import com.rehome.rehome.entity.marketplace.OrderItem;
import com.rehome.rehome.entity.marketplace.PlatformFund;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.ShippingAddress;
import com.rehome.rehome.entity.marketplace.Wallet;
import com.rehome.rehome.entity.marketplace.WalletTransaction;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.entity.membership.MemberVoucher;
import com.rehome.rehome.entity.membership.VoucherTemplate;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.CartItemRepository;
import com.rehome.rehome.repository.CartRepository;
import com.rehome.rehome.repository.MemberVoucherRepository;
import com.rehome.rehome.repository.OrderItemRepository;
import com.rehome.rehome.repository.OrderRepository;
import com.rehome.rehome.repository.OrderTrackingEventRepository;
import com.rehome.rehome.repository.PaymentRepository;
import com.rehome.rehome.repository.PlatformFundRepository;
import com.rehome.rehome.repository.ProductPhotoRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.repository.QueueItemRepository;
import com.rehome.rehome.repository.ReturnRequestRepository;
import com.rehome.rehome.repository.ShippingAddressRepository;
import com.rehome.rehome.repository.WalletRepository;
import com.rehome.rehome.repository.WalletTransactionRepository;
import com.rehome.rehome.service.QueueService;
import com.rehome.rehome.service.SystemConfigReader;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Voucher trong luồng đặt hàng, hoàn tiền và ghi sổ.
 *
 * <p><b>Test quan trọng nhất của toàn bộ tính năng</b> là
 * {@link Ledger#consignorPayoutIsUnaffectedByVoucher}: bảng đối chiếu §13.4 nói người ký gửi nhận
 * đúng 450.000đ cho món 500.000đ kỳ 1, <b>dù đơn có voucher hay không</b> - phần giảm do nền tảng
 * chịu, không trừ vào tiền người ký gửi. Nếu test đó đỏ thì {@code price_at_order} hoặc công thức
 * payout đã bị voucher làm sai, và đó là chuyện đụng tới hợp đồng ký gửi.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderVoucherIntegrationTest {

    @Mock AccountRepository accountRepository;
    @Mock ShippingAddressRepository shippingAddressRepository;
    @Mock CartRepository cartRepository;
    @Mock CartItemRepository cartItemRepository;
    @Mock ProductRepository productRepository;
    @Mock OrderRepository orderRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock OrderTrackingEventRepository orderTrackingEventRepository;
    @Mock ReturnRequestRepository returnRequestRepository;
    @Mock ProductPhotoRepository productPhotoRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock WalletRepository walletRepository;
    @Mock WalletTransactionRepository walletTransactionRepository;
    @Mock PlatformFundRepository platformFundRepository;
    @Mock QueueService queueService;
    @Mock QueueItemRepository queueItemRepository;
    @Mock GhnApi ghnApi;
    @Mock GhnShippingFeeCalculator ghnShippingFeeCalculator;
    @Mock SystemConfigReader systemConfigReader;
    @Mock MemberVoucherRepository memberVoucherRepository;

    /**
     * Dùng {@code @Spy} với instance thật thay vì mock: thuật toán tính/phân bổ là thứ cần được
     * chạy thật trong test này. {@code VoucherCalculatorTest} lo phần đơn vị của nó.
     */
    @Spy
    VoucherCalculator voucherCalculator = new VoucherCalculator(new StubConfigReader());

    @Mock private com.rehome.rehome.service.ContentModerationService moderation;

    @InjectMocks
    OrderServiceImpl orderService;

    private String email;
    private Account buyer;
    private Account seller;
    private ShippingAddress address;
    private Warehouse warehouse;
    private ConsignmentReceipt receipt;
    private Product product500k;
    private Product productA400k;
    private Product productB400k;
    private final List<Order> savedOrders = new ArrayList<>();
    private final List<WalletTransaction> savedTransactions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(orderService, "taxRate", BigDecimal.ZERO);
        when(systemConfigReader.getInt(eq(SystemConfigKeys.ORDER_PAYMENT_TIMEOUT_MINUTES), anyInt()))
                .thenReturn(15);

        email = "buyer@example.com";
        buyer = Account.builder().id(UUID.randomUUID()).email(email).build();
        seller = Account.builder().id(UUID.randomUUID()).email("seller@example.com").build();

        address = ShippingAddress.builder().id(UUID.randomUUID()).member(buyer)
                .recipientName("Tạ Thị Ngọc Anh").phone("0912345678")
                .addressDetail("12 Nguyễn Trãi").wardName("Phường Bến Nghé")
                .provinceName("TP.HCM").build();

        warehouse = Warehouse.builder().id(UUID.randomUUID()).name("Kho Quận 7").build();

        receipt = ConsignmentReceipt.builder()
                .commissionRateTerm1(new BigDecimal("0.10"))
                .commissionRateTerm2(new BigDecimal("0.15"))
                .commissionRateTerm3(new BigDecimal("0.20"))
                .build();

        product500k = product("Q7-AK-000500", "500000", (short) 1);
        productA400k = product("Q7-AK-000A", "400000", (short) 1);
        productB400k = product("Q7-AK-000B", "400000", (short) 1);

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(buyer));
        when(shippingAddressRepository.findById(address.getId())).thenReturn(Optional.of(address));
        when(cartRepository.findByMember_Id(buyer.getId())).thenReturn(Optional.empty());
        when(walletTransactionRepository.countByTransactionNoStartingWith(anyString())).thenReturn(0L);
        when(orderRepository.countByOrderNoStartingWith(anyString())).thenReturn(0L);
        when(platformFundRepository.findTopByOrderByIdAsc())
                .thenReturn(Optional.of(PlatformFund.builder().id(UUID.randomUUID())
                        .balance(new BigDecimal("10000000")).build()));

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            if (o.getId() == null) {
                o.setId(UUID.randomUUID());
                savedOrders.add(o);
            }
            return o;
        });
        when(walletTransactionRepository.save(any(WalletTransaction.class))).thenAnswer(inv -> {
            savedTransactions.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));
        when(platformFundRepository.save(any(PlatformFund.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ================================================================ Đặt hàng

    @Nested
    class Checkout {

        @Test
        @DisplayName("Voucher ship 15.000 trên đơn ship 30.000: total = 500.000 + 30.000 − 15.000, "
                + "mã chuyển USED, priceAtOrder GIỮ NGUYÊN 500.000")
        void shippingVoucher_reducesTotalButNotPriceAtOrder() {
            stubProduct(product500k, "30000");
            MemberVoucher voucher = shippingVoucher("15000");
            stubVoucher(voucher);

            List<OrderResponse> responses = orderService.createOrders(email,
                    new CreateOrderRequest(address.getId().toString(),
                            List.of(product500k.getId()), null, voucher.getCode()));

            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).getDiscountAmount()).isEqualByComparingTo("15000");
            assertThat(responses.get(0).getTotalAmount()).isEqualByComparingTo("515000");
            assertThat(responses.get(0).getVoucherCode()).isEqualTo(voucher.getCode());

            Order order = savedOrders.get(0);
            assertThat(order.getSubtotal()).isEqualByComparingTo("500000");
            assertThat(order.getShippingFee()).isEqualByComparingTo("30000");
            assertThat(order.getDiscountAmount()).isEqualByComparingTo("15000");
            assertThat(order.getVoucher()).isSameAs(voucher);

            // price_at_order là mốc tính tiền người ký gửi - voucher không được đụng tới
            ArgumentCaptor<OrderItem> item = ArgumentCaptor.forClass(OrderItem.class);
            verify(orderItemRepository).save(item.capture());
            assertThat(item.getValue().getPriceAtOrder()).isEqualByComparingTo("500000");

            assertThat(voucher.getStatus()).isEqualTo(MemberVoucherStatus.USED);
            assertThat(voucher.getUsedAt()).isNotNull();
        }

        @Test
        @DisplayName("Không gửi voucherCode: hành vi y như trước khi có tính năng voucher "
                + "(discount 0, không khóa dòng voucher nào)")
        void noVoucherCode_behavesLikeBefore() {
            stubProduct(product500k, "30000");

            List<OrderResponse> responses = orderService.createOrders(email,
                    new CreateOrderRequest(address.getId().toString(), List.of(product500k.getId()), null));

            assertThat(responses.get(0).getDiscountAmount()).isEqualByComparingTo("0");
            assertThat(responses.get(0).getTotalAmount()).isEqualByComparingTo("530000");
            assertThat(responses.get(0).getVoucherCode()).isNull();
            assertThat(savedOrders.get(0).getVoucher()).isNull();
            verify(memberVoucherRepository, never()).findByCodeForUpdate(anyString());
        }

        @Test
        @DisplayName("Giỏ 2 món: MỘT mã áp cho cả lần thanh toán, server phân bổ về từng đơn, "
                + "mỗi đơn trỏ cùng voucher_id")
        void oneVoucherSpreadsAcrossMultipleOrders() {
            stubProduct(productA400k, "20000");
            stubProduct(productB400k, "20000");
            MemberVoucher voucher = orderDiscountVoucher("0.10", "50000");
            stubVoucher(voucher);

            List<OrderResponse> responses = orderService.createOrders(email,
                    new CreateOrderRequest(address.getId().toString(),
                            List.of(productA400k.getId(), productB400k.getId()), null, voucher.getCode()));

            assertThat(responses).hasSize(2);
            // d thô 40.000 mỗi đơn = 80.000 > trần 50.000 -> chia lại 25.000 mỗi đơn
            assertThat(responses).allSatisfy(r ->
                    assertThat(r.getDiscountAmount()).isEqualByComparingTo("25000"));
            assertThat(responses).allSatisfy(r ->
                    assertThat(r.getTotalAmount()).isEqualByComparingTo("395000"));

            // Nhiều đơn cùng trỏ một voucher -> orders.voucher_id KHÔNG được UNIQUE
            assertThat(savedOrders).allSatisfy(o -> assertThat(o.getVoucher()).isSameAs(voucher));
            // Mã chỉ được đánh dấu USED một lần
            verify(memberVoucherRepository, org.mockito.Mockito.times(1)).save(voucher);
        }

        @Test
        @DisplayName("Mã không tồn tại -> VOUCHER_NOT_FOUND 404, không tạo đơn nào")
        void unknownCode_isRejected() {
            stubProduct(product500k, "30000");
            when(memberVoucherRepository.findByCodeForUpdate("SALE-NOPE")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.createOrders(email,
                    new CreateOrderRequest(address.getId().toString(),
                            List.of(product500k.getId()), null, "SALE-NOPE")))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.VOUCHER_NOT_FOUND);

            assertThat(savedOrders).isEmpty();
        }

        @Test
        @DisplayName("Mã của người khác -> VOUCHER_FORBIDDEN 403, không tạo đơn nào")
        void othersVoucher_isRejected() {
            stubProduct(product500k, "30000");
            MemberVoucher voucher = shippingVoucher("15000");
            voucher.setMember(Account.builder().id(UUID.randomUUID()).build());
            stubVoucher(voucher);

            assertThatThrownBy(() -> orderService.createOrders(email,
                    new CreateOrderRequest(address.getId().toString(),
                            List.of(product500k.getId()), null, voucher.getCode())))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.VOUCHER_FORBIDDEN);

            assertThat(savedOrders).isEmpty();
        }

        @Test
        @DisplayName("Mã đã dùng -> VOUCHER_ALREADY_USED 409. Trạng thái được đọc lại TRONG lock, "
                + "nên hai request song song không cùng dùng được một mã")
        void alreadyUsedVoucher_isRejected() {
            stubProduct(product500k, "30000");
            MemberVoucher voucher = shippingVoucher("15000");
            voucher.setStatus(MemberVoucherStatus.USED);
            stubVoucher(voucher);

            assertThatThrownBy(() -> orderService.createOrders(email,
                    new CreateOrderRequest(address.getId().toString(),
                            List.of(product500k.getId()), null, voucher.getCode())))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.VOUCHER_ALREADY_USED);
        }

        @Test
        @DisplayName("Dùng mã phải đi qua findByCodeForUpdate (khóa dòng), không phải findByCode")
        void voucherRowIsLocked() {
            stubProduct(product500k, "30000");
            MemberVoucher voucher = shippingVoucher("15000");
            stubVoucher(voucher);

            orderService.createOrders(email, new CreateOrderRequest(
                    address.getId().toString(), List.of(product500k.getId()), null, voucher.getCode()));

            verify(memberVoucherRepository).findByCodeForUpdate(voucher.getCode());
        }
    }

    // ================================================================ Ghi sổ (§13.3, §13.4)

    @Nested
    class Ledger {

        /**
         * Bảng đối chiếu §13.4: món 500.000đ, hoa hồng kỳ 1 = 10%, ship 30.000đ, voucher ship 15.000đ.
         *
         * <table>
         *   <tr><td>Người mua trả</td><td>515.000</td></tr>
         *   <tr><td>Người ký gửi nhận</td><td><b>450.000</b></td></tr>
         *   <tr><td>Hoa hồng ghi nhận</td><td>50.000</td></tr>
         *   <tr><td>Chi phí khuyến mãi</td><td>−15.000</td></tr>
         *   <tr><td>Nền tảng thực giữ</td><td>35.000</td></tr>
         * </table>
         */
        @Test
        @DisplayName("§13.4 - người ký gửi nhận ĐÚNG 450.000đ dù đơn có voucher; "
                + "phần giảm ghi riêng thành dòng VOUCHER_DISCOUNT âm bên quỹ")
        void consignorPayoutIsUnaffectedByVoucher() {
            Order order = paidOrderWithDiscount("15000", "SHIP-ABC123");
            OrderItem item = OrderItem.builder()
                    .id(UUID.randomUUID())
                    .order(order)
                    .product(product500k)
                    .priceAtOrder(new BigDecimal("500000"))
                    .commissionRateApplied(new BigDecimal("0.10"))
                    .taxRateApplied(BigDecimal.ZERO)
                    .termApplied((short) 1)
                    .build();

            when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
            when(orderItemRepository.findAllByOrder_Id(order.getId())).thenReturn(List.of(item));
            when(walletRepository.findByMember_Id(seller.getId()))
                    .thenReturn(Optional.of(Wallet.builder().id(UUID.randomUUID()).member(seller)
                            .balance(BigDecimal.ZERO).lockedBalance(BigDecimal.ZERO).build()));

            orderService.payoutOrder(order.getId(), "Trả tiền người ký gửi");

            BigDecimal payout = amountOf(WalletTransactionType.ORDER_PAYOUT);
            BigDecimal commission = amountOf(WalletTransactionType.COMMISSION_REVENUE);
            BigDecimal promotionCost = amountOf(WalletTransactionType.VOUCHER_DISCOUNT);

            // Tính trên price_at_order (500.000), KHÔNG trên số người mua đã trả (515.000 - 30.000 ship)
            assertThat(payout).isEqualByComparingTo("450000");
            assertThat(commission).isEqualByComparingTo("50000");
            // Dòng chi phí khuyến mãi là ÂM và chỉ để thống kê
            assertThat(promotionCost).isEqualByComparingTo("-15000");
        }

        @Test
        @DisplayName("Đơn không có voucher: KHÔNG ghi dòng VOUCHER_DISCOUNT rỗng vào sổ")
        void noVoucher_writesNoPromotionRow() {
            Order order = paidOrderWithDiscount("0", null);
            OrderItem item = OrderItem.builder()
                    .id(UUID.randomUUID()).order(order).product(product500k)
                    .priceAtOrder(new BigDecimal("500000"))
                    .commissionRateApplied(new BigDecimal("0.10"))
                    .taxRateApplied(BigDecimal.ZERO).termApplied((short) 1).build();

            when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
            when(orderItemRepository.findAllByOrder_Id(order.getId())).thenReturn(List.of(item));
            when(walletRepository.findByMember_Id(seller.getId()))
                    .thenReturn(Optional.of(Wallet.builder().id(UUID.randomUUID()).member(seller)
                            .balance(BigDecimal.ZERO).lockedBalance(BigDecimal.ZERO).build()));

            orderService.payoutOrder(order.getId(), "Trả tiền người ký gửi");

            assertThat(savedTransactions)
                    .noneMatch(t -> t.getType() == WalletTransactionType.VOUCHER_DISCOUNT);
        }

        private BigDecimal amountOf(WalletTransactionType type) {
            return savedTransactions.stream()
                    .filter(t -> t.getType() == type)
                    .map(WalletTransaction::getAmount)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Không có bút toán loại " + type));
        }
    }

    // ================================================================ Hoàn tiền và trả mã (§12.4, §12.5)

    @Nested
    class CancelAndRefund {

        @Test
        @DisplayName("§12.5 - hủy đơn ĐÃ THANH TOÁN hoàn ĐÚNG total_amount (đã trừ voucher), "
                + "KHÔNG hoàn subtotal + ship")
        void paidCancel_refundsTotalAmountNotGrossAmount() {
            Order order = paidOrderWithDiscount("15000", "SHIP-ABC123");
            order.setStatus(OrderStatus.PAID);

            when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
            when(orderItemRepository.findAllByOrder_Id(order.getId())).thenReturn(List.of());
            when(walletRepository.findByMember_Id(buyer.getId()))
                    .thenReturn(Optional.of(Wallet.builder().id(UUID.randomUUID()).member(buyer)
                            .balance(BigDecimal.ZERO).lockedBalance(BigDecimal.ZERO).build()));

            orderService.cancelOrder(email, order.getId());

            // 515.000 là số đã trả thật; hoàn 530.000 sẽ cho người mua lãi 15.000 mỗi lần đặt rồi hủy
            assertThat(order.getRefundAmount()).isEqualByComparingTo("515000");
            // Lọc theo walletType: mỗi bút toán ví Member có một dòng đối ứng ngược dấu bên quỹ
            // (F-039), nên không lọc thì sẽ bắt được cả dòng -515.000 của quỹ.
            assertThat(savedTransactions)
                    .filteredOn(t -> t.getType() == WalletTransactionType.REFUND
                            && t.getWalletType() == com.rehome.rehome.entity.enums.WalletType.MEMBER)
                    .singleElement()
                    .satisfies(t -> assertThat(t.getAmount()).isEqualByComparingTo("515000"));
        }

        @Test
        @DisplayName("§12.4 - hủy đơn ĐÃ THANH TOÁN KHÔNG trả lại mã (tránh đặt-hủy liên tục để "
                + "xoay vòng một mã)")
        void paidCancel_doesNotReleaseVoucher() {
            Order order = paidOrderWithDiscount("15000", "SHIP-ABC123");
            order.setStatus(OrderStatus.PAID);
            MemberVoucher voucher = order.getVoucher();

            when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
            when(orderItemRepository.findAllByOrder_Id(order.getId())).thenReturn(List.of());
            when(walletRepository.findByMember_Id(buyer.getId()))
                    .thenReturn(Optional.of(Wallet.builder().id(UUID.randomUUID()).member(buyer)
                            .balance(BigDecimal.ZERO).lockedBalance(BigDecimal.ZERO).build()));

            orderService.cancelOrder(email, order.getId());

            assertThat(voucher.getStatus()).isEqualTo(MemberVoucherStatus.USED);
            verify(memberVoucherRepository, never()).findByCodeForUpdate(anyString());
        }

        @Test
        @DisplayName("§12.4 - hủy đơn CHƯA THANH TOÁN trả mã về UNUSED (chưa thu tiền của ai) "
                + "và xóa voucher_id khỏi đơn")
        void pendingCancel_releasesVoucher() {
            Order order = paidOrderWithDiscount("15000", "SHIP-ABC123");
            order.setStatus(OrderStatus.PENDING_PAYMENT);
            MemberVoucher voucher = order.getVoucher();

            when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
            when(orderItemRepository.findAllByOrder_Id(order.getId())).thenReturn(List.of());
            when(orderRepository.findAllByVoucher_Id(voucher.getId())).thenReturn(List.of());
            when(memberVoucherRepository.findByCodeForUpdate(voucher.getCode()))
                    .thenReturn(Optional.of(voucher));

            orderService.cancelOrder(email, order.getId());

            assertThat(voucher.getStatus()).isEqualTo(MemberVoucherStatus.UNUSED);
            assertThat(voucher.getUsedAt()).isNull();
            assertThat(order.getVoucher()).isNull();
            assertThat(order.getDiscountAmount()).isEqualByComparingTo("0");
            // Chưa thu tiền thì không được cộng khống tiền vào ví người mua
            assertThat(savedTransactions)
                    .noneMatch(t -> t.getType() == WalletTransactionType.REFUND);
        }

        @Test
        @DisplayName("§12.4 - lô nhiều đơn: còn MỘT đơn chưa hủy thì mã vẫn USED")
        void voucherStaysUsedWhileSiblingOrderIsAlive() {
            Order cancelled = paidOrderWithDiscount("10000", "SHIP-ABC123");
            cancelled.setStatus(OrderStatus.PENDING_PAYMENT);
            MemberVoucher voucher = cancelled.getVoucher();

            Order sibling = paidOrderWithDiscount("10000", null);
            sibling.setVoucher(voucher);
            sibling.setStatus(OrderStatus.PAID);

            when(orderRepository.findById(cancelled.getId())).thenReturn(Optional.of(cancelled));
            when(orderItemRepository.findAllByOrder_Id(cancelled.getId())).thenReturn(List.of());
            when(orderRepository.findAllByVoucher_Id(voucher.getId())).thenReturn(List.of(sibling));

            orderService.cancelOrder(email, cancelled.getId());

            assertThat(voucher.getStatus()).isEqualTo(MemberVoucherStatus.USED);
            // Phần giảm của đơn bị hủy KHÔNG được chuyển sang đơn còn lại
            assertThat(sibling.getDiscountAmount()).isEqualByComparingTo("10000");
        }

        @Test
        @DisplayName("§12.4 - mã đã hết hạn tại thời điểm trả thì đặt EXPIRED, không trả về UNUSED")
        void expiredVoucher_becomesExpiredNotUnused() {
            Order order = paidOrderWithDiscount("15000", "SHIP-ABC123");
            order.setStatus(OrderStatus.PENDING_PAYMENT);
            MemberVoucher voucher = order.getVoucher();
            voucher.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));

            when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
            when(orderItemRepository.findAllByOrder_Id(order.getId())).thenReturn(List.of());
            when(orderRepository.findAllByVoucher_Id(voucher.getId())).thenReturn(List.of());
            when(memberVoucherRepository.findByCodeForUpdate(voucher.getCode()))
                    .thenReturn(Optional.of(voucher));

            orderService.cancelOrder(email, order.getId());

            assertThat(voucher.getStatus()).isEqualTo(MemberVoucherStatus.EXPIRED);
        }
    }

    // ================================================================ Helper

    private Order paidOrderWithDiscount(String discount, String voucherCode) {
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .orderNo("OD-2026-000001")
                .buyer(buyer)
                .warehouse(warehouse)
                .shippingAddress(address)
                .subtotal(new BigDecimal("500000"))
                .shippingFee(new BigDecimal("30000"))
                .discountAmount(new BigDecimal(discount))
                .totalAmount(new BigDecimal("500000").add(new BigDecimal("30000"))
                        .subtract(new BigDecimal(discount)))
                .status(OrderStatus.COMPLETED)
                .paymentDeadline(Instant.now().plus(15, ChronoUnit.MINUTES))
                .build();
        if (voucherCode != null) {
            order.setVoucher(shippingVoucher("15000"));
            order.getVoucher().setCode(voucherCode);
            order.getVoucher().setStatus(MemberVoucherStatus.USED);
            order.getVoucher().setUsedAt(Instant.now());
        }
        return order;
    }

    private void stubProduct(Product product, String shippingFee) {
        when(productRepository.findByIdForUpdate(product.getId())).thenReturn(Optional.of(product));
        when(ghnShippingFeeCalculator.calculate(eq(product.getWarehouse()), anyString(), anyString()))
                .thenReturn(new BigDecimal(shippingFee));
    }

    private void stubVoucher(MemberVoucher voucher) {
        when(memberVoucherRepository.findByCodeForUpdate(voucher.getCode()))
                .thenReturn(Optional.of(voucher));
        when(memberVoucherRepository.save(any(MemberVoucher.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Product product(String id, String price, short term) {
        return Product.builder()
                .id(id)
                .status(ProductStatus.ON_SALE)
                .currentPrice(new BigDecimal(price))
                .currentTerm(term)
                .warehouse(warehouse)
                .consignmentReceipt(receipt)
                .member(seller)
                .build();
    }

    private MemberVoucher shippingVoucher(String amount) {
        return baseVoucher("SHIP-ABC123")
                .type(VoucherType.SHIPPING)
                .discountKind(VoucherDiscountKind.FIXED)
                .discountValue(new BigDecimal(amount))
                .maxDiscountAmount(null)
                .build();
    }

    private MemberVoucher orderDiscountVoucher(String percent, String maxDiscount) {
        return baseVoucher("SALE-XYZ789")
                .type(VoucherType.ORDER_DISCOUNT)
                .discountKind(VoucherDiscountKind.PERCENT)
                .discountValue(new BigDecimal(percent))
                .maxDiscountAmount(new BigDecimal(maxDiscount))
                .build();
    }

    private MemberVoucher.MemberVoucherBuilder baseVoucher(String code) {
        return MemberVoucher.builder()
                .id(UUID.randomUUID())
                .code(code)
                .member(buyer)
                .voucherTemplate(VoucherTemplate.builder()
                        .id(UUID.randomUUID()).name("Mẫu thử").build())
                .status(MemberVoucherStatus.UNUSED)
                .minOrderAmount(BigDecimal.ZERO)
                .issuedAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .expiresAt(Instant.now().plus(29, ChronoUnit.DAYS));
    }

    /** Trần hoa hồng 1.00 (giá trị mặc định) - đủ cho mọi ca trong file này. */
    private static class StubConfigReader implements SystemConfigReader {
        @Override
        public int getInt(String key, int defaultValue) {
            return defaultValue;
        }

        @Override
        public BigDecimal getDecimal(String key, BigDecimal defaultValue) {
            return defaultValue;
        }
    }
}
