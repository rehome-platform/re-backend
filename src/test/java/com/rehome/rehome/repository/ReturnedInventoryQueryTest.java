package com.rehome.rehome.repository;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.OrderPaymentMethod;
import com.rehome.rehome.entity.enums.OrderStatus;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.enums.ReturnRequestStatus;
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

/** Real SQL regression test for latest return lookup across product resale history. */
@DataJpaTest
class ReturnedInventoryQueryTest {

    @Autowired EntityManager em;
    @Autowired ProductRepository productRepository;
    @Autowired ReturnRequestRepository returnRequestRepository;
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
                .status(ProductStatus.ON_SALE)
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

    private com.rehome.rehome.entity.marketplace.ReturnRequest returned(Order order, Instant createdAt) {
        var request = com.rehome.rehome.entity.marketplace.ReturnRequest.builder()
                .order(order).reason("Damaged").handoverDeadline(createdAt.plusSeconds(3600))
                .status(com.rehome.rehome.entity.enums.ReturnRequestStatus.VALID).createdAt(createdAt).build();
        em.persist(request);
        return request;
    }

    @Test void activeItemsExcludeCancelledAndReturnedOrdersIncludingResaleHistory() {
        Product product = persistProduct("RESOLD-1", sellerA);
        Product historicalOnly = persistProduct("HISTORY-ONLY", sellerB);
        Product unrelated = persistProduct("UNRELATED", sellerB);
        Instant now = Instant.parse("2026-11-27T01:00:00Z");
        java.util.Set<UUID> expectedOrderIds = new java.util.HashSet<>();
        for (OrderStatus status : OrderStatus.values()) {
            Order order = persistOrder("STATUS-" + status.name(), status, now);
            persistItem(order, product);
            if (status == OrderStatus.CANCELLED || status == OrderStatus.RETURNED) {
                persistItem(order, historicalOnly);
            } else {
                expectedOrderIds.add(order.getId());
            }
        }
        persistItem(persistOrder("UNRELATED-ORDER", OrderStatus.PAID, now), unrelated);
        em.flush();
        em.clear();

        var result = orderItemRepository.findActiveByProductIds(List.of(product.getId()));

        assertThat(result).extracting(item -> item.getOrder().getId())
                .containsExactlyInAnyOrderElementsOf(expectedOrderIds);
        assertThat(result).allSatisfy(item -> {
            assertThat(item.getProduct().getId()).isEqualTo(product.getId());
            assertThat(item.getOrder().getStatus()).isNotIn(OrderStatus.CANCELLED, OrderStatus.RETURNED);
        });
        assertThat(orderItemRepository.findActiveByProductIds(List.of(historicalOnly.getId()))).isEmpty();
    }

    @Test void returnCountsGroupByBuyerAndRespectRequestedStatuses() {
        Instant now = Instant.parse("2026-11-27T01:00:00Z");
        returned(persistOrder("PENDING-RETURN", OrderStatus.DELIVERED, now), now)
                .setStatus(ReturnRequestStatus.PENDING);
        returned(persistOrder("APPROVED-RETURN", OrderStatus.DELIVERED, now), now)
                .setStatus(ReturnRequestStatus.APPROVED);
        returned(persistOrder("CLOSED-RETURN", OrderStatus.RETURNED, now), now)
                .setStatus(ReturnRequestStatus.VALID);
        Order otherBuyerOrder = persistOrder("OTHER-BUYER-RETURN", OrderStatus.DELIVERED, now);
        otherBuyerOrder.setBuyer(sellerA);
        returned(otherBuyerOrder, now).setStatus(ReturnRequestStatus.PENDING);
        em.flush();
        em.clear();

        var result = returnRequestRepository.countByOrderBuyerIdsInAndStatusIn(
                List.of(buyer.getId()), List.of(ReturnRequestStatus.PENDING, ReturnRequestStatus.APPROVED));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)[0]).isEqualTo(buyer.getId());
        assertThat(((Number) result.get(0)[1]).longValue()).isEqualTo(2L);
    }

    @Test void inventoryFetchesCategoryAndReceiptWithoutPerRowLazyLoads() {
        persistProduct("INVENTORY-1", sellerA);
        persistProduct("INVENTORY-2", sellerB);
        em.flush();
        em.clear();

        var page = productRepository.findInventory(warehouse.getId(), ProductStatus.ON_SALE, false, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(2);
        var loaded = em.getEntityManagerFactory().getPersistenceUnitUtil();
        for (Product item : page) {
            assertThat(loaded.isLoaded(item, "category")).isTrue();
            assertThat(loaded.isLoaded(item, "consignmentReceipt")).isTrue();
            assertThat(item.getCategory().getTryonSlot()).isEqualTo(TryOnSlot.TOP);
        }
    }

    @Test void oneReturnedOrderCanLinkSeparateCasesForEachProduct() {
        Product first = persistProduct("RETURNED-1", sellerA);
        Product second = persistProduct("RETURNED-2", sellerB);
        Instant now = Instant.parse("2026-11-18T00:00:00Z");
        Order order = persistOrder("RETURN-ORDER", OrderStatus.COMPLETED, now);
        persistItem(order, first);
        persistItem(order, second);
        var returned = returned(order, now);
        for (Product product : List.of(first, second)) {
            em.persist(com.rehome.rehome.entity.marketplace.DegradedItemCase.builder()
                    .product(product).returnRequest(returned).moderator(moderator)
                    .compensationAmount(new BigDecimal("180000")).build());
        }
        em.flush();
        em.clear();
        assertThat(em.createQuery("select count(c) from DegradedItemCase c where c.returnRequest.id = :id", Long.class)
                .setParameter("id", returned.getId()).getSingleResult()).isEqualTo(2L);
    }

    @Test void latestReturnDoesNotFallBackToAnOlderUnprocessedValidReturn() {
        Product p = persistProduct("RETURNED-1", sellerA);
        Instant older = Instant.parse("2026-11-01T00:00:00Z");
        Instant newer = older.plusSeconds(3600);
        Order firstOrder = persistOrder("OLD-ORDER", OrderStatus.COMPLETED, older);
        Order latestOrder = persistOrder("NEW-ORDER", OrderStatus.COMPLETED, newer);
        persistItem(firstOrder, p);
        persistItem(latestOrder, p);
        returned(firstOrder, older);
        var latest = returned(latestOrder, newer);
        latest.setStatus(com.rehome.rehome.entity.enums.ReturnRequestStatus.INVALID);
        em.flush();
        em.clear();
        var references = returnRequestRepository.findLatestReturnsByProductIds(List.of(p.getId()));
        assertThat(references).hasSize(1);
        assertThat(references.get(0).getProductId()).isEqualTo(p.getId());
        assertThat(references.get(0).getReturnRequestId()).isEqualTo(latest.getId());
        assertThat(references.get(0).getStatus()).isEqualTo(com.rehome.rehome.entity.enums.ReturnRequestStatus.INVALID);
    }

    @Test void batchedLookupReturnsEachProductsOwnLatestReturnAndHandlingFlags() {
        Product first = persistProduct("RETURNED-1", sellerA);
        Product second = persistProduct("RETURNED-2", sellerB);
        Product noReturn = persistProduct("NO-RETURN", sellerA);
        Instant now = Instant.parse("2026-11-18T00:00:00Z");
        Order order1 = persistOrder("RETURN-1", OrderStatus.COMPLETED, now);
        Order order2 = persistOrder("RETURN-2", OrderStatus.COMPLETED, now);
        persistItem(order1, first);
        persistItem(order2, second);
        var valid = returned(order1, now);
        var handled = returned(order2, now);
        handled.setDegraded(true);
        handled.setItemAction(com.rehome.rehome.entity.enums.ReturnedItemAction.RETURN_TO_SELLER);
        em.flush();
        em.clear();
        var references = returnRequestRepository.findLatestReturnsByProductIds(List.of(first.getId(), second.getId(), noReturn.getId()));
        assertThat(references).hasSize(2);
        var byProduct = references.stream().collect(java.util.stream.Collectors.toMap(
                ReturnRequestRepository.ProductReturnReference::getProductId, r -> r));
        assertThat(byProduct.get(first.getId()).getReturnRequestId()).isEqualTo(valid.getId());
        assertThat(byProduct.get(first.getId()).getItemAction()).isNull();
        assertThat(byProduct.get(first.getId()).getDegraded()).isNull();
        assertThat(byProduct.get(second.getId()).getDegraded()).isTrue();
        assertThat(byProduct.get(second.getId()).getItemAction()).isEqualTo(com.rehome.rehome.entity.enums.ReturnedItemAction.RETURN_TO_SELLER);
    }
}
