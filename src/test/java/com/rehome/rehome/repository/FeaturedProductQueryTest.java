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

/** Real SQL regression test for featured-first sorting before pagination. */
@DataJpaTest
class FeaturedProductQueryTest {

    @Autowired EntityManager em;
    @Autowired ProductRepository productRepository;

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

    @Test
    void featuredBadgesWithMissingRank_stayAheadOfOrdinaryAcrossPages() {
        Instant now = Instant.now();
        Product ordinary = persistProduct("REGULAR", sellerA);
        Product legacy = persistProduct("LEGACY", sellerA);
        Product gold = persistProduct("GOLD", sellerA);
        Product diamond = persistProduct("DIAMOND", sellerA);
        Product expired = persistProduct("EXPIRED", sellerA);
        legacy.setFeaturedUntil(now.plusSeconds(900));
        gold.setFeaturedUntil(now.plusSeconds(3600));
        gold.setFeaturedRank(2);
        diamond.setFeaturedUntil(now.plusSeconds(600));
        diamond.setFeaturedRank(3);
        expired.setFeaturedUntil(now.minusSeconds(60));
        expired.setFeaturedRank(3);
        em.flush();
        // createdAt is immutable in the entity: deliberately make the ordinary item newest in SQL.
        for (String sku : List.of("LEGACY", "GOLD", "DIAMOND")) {
            em.createNativeQuery("update products set created_at = :created where id = :id")
                    .setParameter("created", now.minusSeconds(7200)).setParameter("id", sku).executeUpdate();
        }
        em.clear();
        var spec = com.rehome.rehome.repository.spec.ProductSpecifications.filter(null, null, null, null, null, null, null)
                .and(com.rehome.rehome.repository.spec.ProductSpecifications.featuredFirst(now));
        var first = productRepository.findAll(spec, PageRequest.of(0, 2));
        var second = productRepository.findAll(spec, PageRequest.of(1, 2));
        assertThat(first.getTotalElements()).isEqualTo(5);
        assertThat(first.getContent()).extracting(Product::getId).containsExactly("DIAMOND", "GOLD");
        assertThat(second.getContent().get(0).getId()).isEqualTo("LEGACY");
        assertThat(second.getContent().get(1).getId()).isIn("REGULAR", "EXPIRED");
    }

    @Test
    void warehouseAndCategoryFiltersPreserveFeaturedPriority() {
        Instant now = Instant.now();
        persistProduct("ORDINARY", sellerA);
        Product legacy = persistProduct("FEATURED", sellerA);
        legacy.setFeaturedUntil(now.plusSeconds(600));
        Product hidden = persistProduct("HIDDEN-FEATURED", sellerB);
        hidden.setStatus(ProductStatus.WAREHOUSE);
        hidden.setFeaturedUntil(now.plusSeconds(600));
        hidden.setFeaturedRank(3);
        em.flush();
        var spec = com.rehome.rehome.repository.spec.ProductSpecifications.filter(null, category.getId(), null,
                null, null, warehouse.getId(), null)
                .and(com.rehome.rehome.repository.spec.ProductSpecifications.featuredFirst(now));
        var page = productRepository.findAll(spec, PageRequest.of(0, 20));
        assertThat(page.getContent()).extracting(Product::getId).containsExactly("FEATURED", "ORDINARY");
    }
}
