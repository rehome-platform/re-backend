package com.rehome.rehome.serviceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.rehome.rehome.client.GhnShippingFeeCalculator;
import com.rehome.rehome.component.VoucherCalculator;
import com.rehome.rehome.dto.response.ApplicableVouchersResponse;
import com.rehome.rehome.dto.response.MemberVoucherResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.MemberVoucherStatus;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.VoucherDiscountKind;
import com.rehome.rehome.entity.enums.VoucherType;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.ShippingAddress;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.entity.membership.MemberVoucher;
import com.rehome.rehome.entity.membership.VoucherTemplate;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.repository.CartItemRepository;
import com.rehome.rehome.repository.CartRepository;
import com.rehome.rehome.repository.MemberVoucherRepository;
import com.rehome.rehome.repository.OrderRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.repository.ShippingAddressRepository;
import com.rehome.rehome.service.SystemConfigReader;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * {@code GET /vouchers/applicable} là API mobile dựa vào để hiện số tiền giảm - đặc tả cấm client
 * tự tính. Vì vậy nó phải dựng lại ĐÚNG các đơn mà {@code POST /orders} sẽ tạo và chạy cùng một
 * {@link VoucherCalculator}, nếu không màn thanh toán hiện một số và lúc đặt lại ra số khác.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VoucherServiceImplTest {

    @Mock private MemberVoucherRepository memberVoucherRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private ProductRepository productRepository;
    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private ShippingAddressRepository shippingAddressRepository;
    @Mock private GhnShippingFeeCalculator ghnShippingFeeCalculator;

    @Spy
    private VoucherCalculator voucherCalculator = new VoucherCalculator(new StubConfigReader());

    @InjectMocks
    private VoucherServiceImpl service;

    private Account member;
    private Account seller;
    private ShippingAddress address;
    private Warehouse warehouse;
    private Product product400k;

    @BeforeEach
    void setUp() {
        member = Account.builder().id(UUID.randomUUID()).email("m@example.com").build();
        seller = Account.builder().id(UUID.randomUUID()).email("s@example.com").build();
        warehouse = Warehouse.builder().id(UUID.randomUUID()).name("Kho Quận 7").build();
        address = ShippingAddress.builder().id(UUID.randomUUID()).member(member)
                .wardName("Phường Bến Nghé").provinceName("TP.HCM").build();

        ConsignmentReceipt receipt = ConsignmentReceipt.builder()
                .commissionRateTerm1(new BigDecimal("0.10"))
                .commissionRateTerm2(new BigDecimal("0.15"))
                .commissionRateTerm3(new BigDecimal("0.20"))
                .build();

        product400k = Product.builder()
                .id("Q7-AK-000400")
                .status(ProductStatus.ON_SALE)
                .currentPrice(new BigDecimal("400000"))
                .currentTerm((short) 1)
                .warehouse(warehouse)
                .consignmentReceipt(receipt)
                .member(seller)
                .build();

        when(shippingAddressRepository.findById(address.getId())).thenReturn(Optional.of(address));
        when(productRepository.findAllById(any())).thenReturn(List.of(product400k));
        when(ghnShippingFeeCalculator.calculate(any(), anyString(), anyString()))
                .thenReturn(new BigDecimal("30000"));
    }

    @Test
    @DisplayName("Trả subtotal + shippingFee của CẢ lần thanh toán và số tiền giảm thật của từng mã")
    void returnsRealDiscountPerVoucher() {
        MemberVoucher ship = voucher("SHIP-AAA111", VoucherType.SHIPPING,
                VoucherDiscountKind.FIXED, "15000", null, "0");
        MemberVoucher sale = voucher("SALE-BBB222", VoucherType.ORDER_DISCOUNT,
                VoucherDiscountKind.PERCENT, "0.05", "30000", "0");
        when(memberVoucherRepository.findUsable(eq(member.getId()), any()))
                .thenReturn(List.of(ship, sale));

        ApplicableVouchersResponse response = service.getApplicableVouchers(
                member, List.of(product400k.getId()), address.getId());

        assertThat(response.getSubtotal()).isEqualByComparingTo("400000");
        assertThat(response.getShippingFee()).isEqualByComparingTo("30000");

        // Dùng được trước, giảm nhiều nhất lên đầu -> mã giảm đơn 20.000 đứng trước mã ship 15.000
        assertThat(response.getVouchers()).hasSize(2);
        assertThat(response.getVouchers().get(0).getCode()).isEqualTo("SALE-BBB222");
        assertThat(response.getVouchers().get(0).getDiscountAmount()).isEqualByComparingTo("20000");
        assertThat(response.getVouchers().get(1).getCode()).isEqualTo("SHIP-AAA111");
        assertThat(response.getVouchers().get(1).getDiscountAmount()).isEqualByComparingTo("15000");
        assertThat(response.getVouchers()).allSatisfy(v -> assertThat(v.isApplicable()).isTrue());
    }

    @Test
    @DisplayName("Mã chưa đạt đơn tối thiểu: applicable = false, discountAmount = 0 và CÓ reason "
            + "để FE hiện xám ở nhóm 'Chưa đủ điều kiện'")
    void minOrderNotMet_isReturnedWithReasonInsteadOfBeingHidden() {
        MemberVoucher bigSpender = voucher("SALE-CCC333", VoucherType.ORDER_DISCOUNT,
                VoucherDiscountKind.PERCENT, "0.05", "50000", "1000000");
        when(memberVoucherRepository.findUsable(eq(member.getId()), any()))
                .thenReturn(List.of(bigSpender));

        ApplicableVouchersResponse response = service.getApplicableVouchers(
                member, List.of(product400k.getId()), address.getId());

        ApplicableVouchersResponse.Item item = response.getVouchers().get(0);
        assertThat(item.isApplicable()).isFalse();
        assertThat(item.getDiscountAmount()).isEqualByComparingTo("0");
        assertThat(item.getReason()).contains("1.000.000đ");
    }

    @Test
    @DisplayName("Mã ship gặp đơn miễn phí ship: đủ điều kiện hình thức nhưng không giảm được gì "
            + "-> applicable = false kèm lý do, không phải ném lỗi")
    void voucherThatCannotDiscountAnything_isMarkedNotApplicable() {
        when(ghnShippingFeeCalculator.calculate(any(), anyString(), anyString()))
                .thenReturn(BigDecimal.ZERO);
        MemberVoucher ship = voucher("SHIP-DDD444", VoucherType.SHIPPING,
                VoucherDiscountKind.FIXED, "15000", null, "0");
        when(memberVoucherRepository.findUsable(eq(member.getId()), any()))
                .thenReturn(List.of(ship));

        ApplicableVouchersResponse response = service.getApplicableVouchers(
                member, List.of(product400k.getId()), address.getId());

        assertThat(response.getVouchers().get(0).isApplicable()).isFalse();
        assertThat(response.getVouchers().get(0).getReason()).isNotBlank();
    }

    @Test
    @DisplayName("Món vừa bị người khác mua (rời ON_SALE) bị lọc bỏ, không làm hỏng cả màn xem trước")
    void soldOutProductIsSkippedNotFatal() {
        Product reserved = Product.builder()
                .id("Q7-AK-000999")
                .status(ProductStatus.RESERVED)
                .currentPrice(new BigDecimal("100000"))
                .currentTerm((short) 1)
                .warehouse(warehouse)
                .consignmentReceipt(product400k.getConsignmentReceipt())
                .member(seller)
                .build();
        when(productRepository.findAllById(any())).thenReturn(List.of(product400k, reserved));
        when(memberVoucherRepository.findUsable(eq(member.getId()), any())).thenReturn(List.of());

        ApplicableVouchersResponse response = service.getApplicableVouchers(
                member, List.of(product400k.getId(), reserved.getId()), address.getId());

        // Chỉ tính món còn bán được
        assertThat(response.getSubtotal()).isEqualByComparingTo("400000");
    }

    @Test
    @DisplayName("Món do chính mình ký gửi bị lọc bỏ - không mua được nên cũng không tính vào xem trước")
    void ownProductIsSkipped() {
        product400k.setMember(member);
        when(memberVoucherRepository.findUsable(eq(member.getId()), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.getApplicableVouchers(
                member, List.of(product400k.getId()), address.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Không còn món nào");
    }

    @Test
    @DisplayName("Thiếu shippingAddressId -> 400 (phí ship là một phần của số tiền giảm)")
    void missingAddress_isRejected() {
        assertThatThrownBy(() -> service.getApplicableVouchers(
                member, List.of(product400k.getId()), null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("shippingAddressId");
    }

    @Test
    @DisplayName("Địa chỉ của người khác -> 404, không được lộ phí ship tới địa chỉ đó")
    void othersAddress_isNotFound() {
        ShippingAddress othersAddress = ShippingAddress.builder()
                .id(UUID.randomUUID())
                .member(Account.builder().id(UUID.randomUUID()).build())
                .build();
        when(shippingAddressRepository.findById(othersAddress.getId()))
                .thenReturn(Optional.of(othersAddress));

        assertThatThrownBy(() -> service.getApplicableVouchers(
                member, List.of(product400k.getId()), othersAddress.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Ví voucher: daysRemaining làm tròn LÊN, và mã đã dùng trả kèm danh sách orderNo "
            + "(một mã có thể trải trên nhiều đơn của cùng lần thanh toán)")
    void wallet_mapsDaysRemainingAndUsedOrderNos() {
        MemberVoucher used = voucher("SHIP-EEE555", VoucherType.SHIPPING,
                VoucherDiscountKind.FIXED, "15000", null, "0");
        used.setStatus(MemberVoucherStatus.USED);
        used.setUsedAt(Instant.now());
        used.setExpiresAt(Instant.now().plus(36, ChronoUnit.HOURS));

        when(memberVoucherRepository.findWallet(eq(member.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(used), PageRequest.of(0, 20), 1));
        when(orderRepository.findVoucherIdAndOrderNoByVoucherIds(any()))
                .thenReturn(List.of(
                        new Object[]{used.getId(), "OD-2026-000001"},
                        new Object[]{used.getId(), "OD-2026-000002"}));

        PageResponse<MemberVoucherResponse> page = service.getMyVouchers(member, null, 0, 20);

        MemberVoucherResponse item = page.getContent().get(0);
        // 36 giờ -> 2 ngày (làm tròn lên), không phải 1
        assertThat(item.getDaysRemaining()).isEqualTo(2);
        assertThat(item.getUsedOrderNos()).containsExactly("OD-2026-000001", "OD-2026-000002");
        assertThat(item.getStatus()).isEqualTo(MemberVoucherStatus.USED);
    }

    private MemberVoucher voucher(String code, VoucherType type, VoucherDiscountKind kind,
                                   String value, String maxDiscount, String minOrder) {
        return MemberVoucher.builder()
                .id(UUID.randomUUID())
                .code(code)
                .member(member)
                .voucherTemplate(VoucherTemplate.builder()
                        .id(UUID.randomUUID()).name("Mẫu " + code).description("Mô tả").build())
                .type(type)
                .discountKind(kind)
                .discountValue(new BigDecimal(value))
                .maxDiscountAmount(maxDiscount == null ? null : new BigDecimal(maxDiscount))
                .minOrderAmount(new BigDecimal(minOrder))
                .status(MemberVoucherStatus.UNUSED)
                .issuedAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .expiresAt(Instant.now().plus(29, ChronoUnit.DAYS))
                .build();
    }

    /** Trần hoa hồng 1.00 (mặc định). */
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
