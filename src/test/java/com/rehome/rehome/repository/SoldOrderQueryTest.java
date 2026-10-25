package com.rehome.rehome.repository;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.OrderPaymentMethod;
import com.rehome.rehome.entity.enums.OrderStatus;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.enums.TryOnSlot;
import com.rehome.rehome.entity.marketplace.Brand;
import com.rehome.rehome.entity.marketplace.Category;
import com.rehome.rehome.entity.marketplace.ConsignmentForm;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.Order;
import com.rehome.rehome.entity.marketplace.OrderItem;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.ShippingAddress;
import com.rehome.rehome.entity.marketplace.Warehouse;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chạy thật hai câu JPQL của API "đơn đã bán" trên H2. Unit test của OrderServiceImpl mock
 * repository nên không bao giờ sinh SQL; cặp distinct + order by ở đây chỉ vỡ lúc runtime,
 * nên phải có một bài chạy thật.
 */
@DataJpaTest
class SoldOrderQueryTest {

    @Autowired EntityManager em;
    @Autowired OrderRepository orderRepository;
    @Autowired OrderItemRepository orderItemRepository;

    Account sellerA;
    Account sellerB;
    Account buyer;
    Account moderator;
    Warehouse warehouse;
    Category category;
    Brand brand;

    @BeforeEach
    void setup() {
        sellerA = persistAccount("seller-a@example.com");
        sellerB = persistAccount("seller-b@example.com");
        buyer = persistAccount("buyer@example.com");
        moderator = persistAccount("moderator@example.com");

        warehouse = new Warehouse();
        warehouse.setName("Kho Quận 7");
        warehouse.setAddress("1 Nguyễn Văn Linh");
        warehouse.setWardName("Tân Phú");
        warehouse.setProvinceName("TP.HCM");
        warehouse.setLatitude(new BigDecimal("10.7300000"));
        warehouse.setLongitude(new BigDecimal("106.7200000"));
        warehouse.setOpeningHours("8h-17h");
        em.persist(warehouse);

        category = new Category();
        category.setName("Áo khoác");
        category.setTryonSlot(TryOnSlot.TOP);
        em.persist(category);

        brand = new Brand();
        brand.setName("Nike");
        em.persist(brand);

        em.flush();
    }

    private Account persistAccount(String email) {
        Account account = Account.builder()
                .email(email)
                .passwordHash("x")
                .role(Role.MEMBER)
                .emailVerified(true)
                .build();
        em.persist(account);
        return account;
    }

    private Product persistProduct(String sku, Account seller) {
        ConsignmentForm form = ConsignmentForm.builder()
                .member(seller)
                .warehouse(warehouse)
                .category(category)
                .brand(brand)
                .productName(sku)
                .description("mô tả")
                .desiredPrice(new BigDecimal("200000"))
                .bringInDeadline(LocalDate.now())
                .build();
        em.persist(form);

        ConsignmentReceipt receipt = ConsignmentReceipt.builder()
                .receiptNo("KG-2026-" + sku)
                .consignmentForm(form)
                .member(seller)
                .moderator(moderator)
                .warehouse(warehouse)
                .conditionNote("Còn 90%")
                .agreedPrice(new BigDecimal("200000"))
                .floorPrice(new BigDecimal("100000"))
                .termLengthDays(30)
                .maxRenewals(2)
                .freeWithdrawalWindowDays(7)
                .commissionRateTerm1(new BigDecimal("0.10"))
                .commissionRateTerm2(new BigDecimal("0.15"))
                .commissionRateTerm3(new BigDecimal("0.20"))
                .withdrawalFee(new BigDecimal("20000"))
                .degradedCompensationRate(new BigDecimal("0.50"))
                .confirmedAt(Instant.now())
                .build();
        em.persist(receipt);

        Product product = Product.builder()
                .id(sku)
                .consignmentReceipt(receipt)
                .member(seller)
                .warehouse(warehouse)
                .category(category)
                .brand(brand)
                .name(sku)
                .conditionNote("Còn 90%")
                .currentPrice(new BigDecimal("200000"))
                .status(ProductStatus.SOLD)
                .currentTerm((short) 1)
                .termStartedAt(Instant.now())
                .build();
        em.persist(product);
        return product;
    }

    private Order persistOrder(String orderNo, OrderStatus status, Instant createdAt) {
        ShippingAddress address = ShippingAddress.builder()
                .member(buyer)
                .recipientName("Người mua")
                .phone("0912345678")
                .addressDetail("12 Nguyễn Trãi")
                .wardName("Bến Nghé")
                .provinceName("TP.HCM")
                .isDefault(true)
                .build();
        em.persist(address);

        Order order = Order.builder()
                .orderNo(orderNo)
                .buyer(buyer)
                .warehouse(warehouse)
                .shippingAddress(address)
                .subtotal(new BigDecimal("200000"))
                .shippingFee(new BigDecimal("25000"))
                .totalAmount(new BigDecimal("225000"))
                .paymentMethod(OrderPaymentMethod.WALLET)
                .status(status)
                .paymentDeadline(Instant.now().plusSeconds(900))
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
        em.persist(order);
        return order;
    }

    private void persistItem(Order order, Product product) {
        em.persist(OrderItem.builder()
                .order(order)
                .product(product)
                .priceAtOrder(new BigDecimal("200000"))
                .commissionRateApplied(new BigDecimal("0.10"))
                .taxRateApplied(BigDecimal.ZERO)
                .termApplied((short) 1)
                .build());
    }

    @Test
    void findOrdersContainingSellerItems_runsOnDb_dedupesAndSortsNewestFirst() {
        Product a1 = persistProduct("Q7-AK-000001", sellerA);
        Product a2 = persistProduct("Q7-AK-000002", sellerA);
        Product b1 = persistProduct("Q7-AK-000003", sellerB);

        Instant older = Instant.parse("2026-10-01T00:00:00Z");
        Instant newer = Instant.parse("2026-10-20T00:00:00Z");

        // Đơn cũ: hai món của sellerA -> distinct phải gộp thành MỘT dòng
        Order old = persistOrder("OD-2026-000001", OrderStatus.COMPLETED, older);
        persistItem(old, a1);
        persistItem(old, a2);

        // Đơn mới: một món của sellerA, một món của sellerB
        Order fresh = persistOrder("OD-2026-000002", OrderStatus.SHIPPING, newer);
        persistItem(fresh, a2);
        persistItem(fresh, b1);

        // Đơn chỉ có hàng của sellerB -> sellerA không được thấy
        Order other = persistOrder("OD-2026-000003", OrderStatus.PAID, newer);
        persistItem(other, b1);

        em.flush();
        em.clear();

        Page<Order> result = orderRepository.findOrdersContainingSellerItems(
                sellerA.getId(),
                List.of(OrderStatus.PAID, OrderStatus.SHIPPING, OrderStatus.COMPLETED),
                PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).extracting(Order::getOrderNo)
                .containsExactly("OD-2026-000002", "OD-2026-000001");
    }

    @Test
    void findSellerItemsByOrderIds_returnsOnlyThatSellersItems() {
        Product a1 = persistProduct("Q7-AK-000001", sellerA);
        Product b1 = persistProduct("Q7-AK-000002", sellerB);

        Order order = persistOrder("OD-2026-000001", OrderStatus.SHIPPING,
                Instant.parse("2026-10-20T00:00:00Z"));
        persistItem(order, a1);
        persistItem(order, b1);

        em.flush();
        UUID orderId = order.getId();
        em.clear();

        List<OrderItem> items = orderItemRepository.findSellerItemsByOrderIds(
                List.of(orderId), sellerA.getId());

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getProduct().getId()).isEqualTo("Q7-AK-000001");
    }

    @Test
    void countGroupedByStatusForSeller_countsEachOrderOnce() {
        Product a1 = persistProduct("Q7-AK-000001", sellerA);
        Product a2 = persistProduct("Q7-AK-000002", sellerA);

        Order order = persistOrder("OD-2026-000001", OrderStatus.COMPLETED,
                Instant.parse("2026-10-01T00:00:00Z"));
        persistItem(order, a1);
        persistItem(order, a2);

        em.flush();
        em.clear();

        List<Object[]> rows = orderRepository.countGroupedByStatusForSeller(sellerA.getId());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)[0]).isEqualTo(OrderStatus.COMPLETED);
        assertThat(rows.get(0)[1]).isEqualTo(1L);
    }
}
