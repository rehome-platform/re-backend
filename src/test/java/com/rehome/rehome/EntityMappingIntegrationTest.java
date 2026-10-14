package com.rehome.rehome;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.rehome.rehome.entity.Account;
import com.rehome.rehome.entity.Role;
import com.rehome.rehome.entity.audit.AuditLog;
import com.rehome.rehome.entity.auth.MemberProfile;
import com.rehome.rehome.entity.enums.*;
import com.rehome.rehome.entity.marketplace.*;
import com.rehome.rehome.entity.tryon.TryOnImage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true", showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EntityMappingIntegrationTest {
    @Autowired EntityManager em;

    @Test
    void discoversAllEntitiesAndCreatesEveryTableWithoutDdlErrors() {
        assertThat(em.getMetamodel().getEntities()).hasSize(47);
        for (var entity : em.getMetamodel().getEntities()) {
            em.createQuery("select e from " + entity.getName() + " e").setMaxResults(1).getResultList();
        }
    }

    @Test
    void profileSharesAccountKeyAndJsonAuditSurvivesReload() {
        Account member = account(Role.MEMBER);
        MemberProfile profile = MemberProfile.builder().account(member)
                .fullName("Test member").memberCode("MEM-001").build();
        em.persist(profile);
        var detail = JsonNodeFactory.instance.objectNode().put("before", "PENDING").put("after", "VERIFIED");
        AuditLog audit = AuditLog.builder().actorAccount(member).actionType("VERIFY_ORGANIZATION")
                .targetType("PRODUCT").targetId("Q7-AK-000845").detail(detail).build();
        em.persist(audit);
        em.flush();
        em.clear();

        assertThat(profile.getAccountId()).isEqualTo(member.getId());
        assertThat(em.find(MemberProfile.class, member.getId()).getAccount().getEmail()).isEqualTo(member.getEmail());
        assertThat(em.find(AuditLog.class, audit.getId()).getDetail()).isEqualTo(detail);
        assertThat(audit.getCreatedAt()).isNotNull();
    }

    @Test
    void consignmentPreservesSkuMoneyDefaultsAndForeignKeys() {
        Product product = product();
        em.flush();
        em.clear();

        Product saved = em.find(Product.class, product.getId());
        assertThat(saved.getId()).startsWith("Q7-");
        assertThat(saved.getCurrentPrice()).isEqualByComparingTo("150000.25");
        assertThat(saved.getStatus()).isEqualTo(ProductStatus.ON_SALE);
        assertThat(saved.getCurrentTerm()).isEqualTo((short) 1);
        ConsignmentReceipt receipt = saved.getConsignmentReceipt();
        assertThat(receipt.getCommissionRateTerm1()).isEqualByComparingTo("0.10");
        assertThat(receipt.getTermLengthDays()).isEqualTo(30);
        assertThat(receipt.getConsignmentForm().getMember().getId()).isEqualTo(saved.getMember().getId());
        assertThat(saved.getCreatedAt()).isNotNull();
        Instant previousUpdate = Instant.parse("2020-01-01T00:00:00Z");
        saved.setUpdatedAt(previousUpdate);
        saved.setName("Updated coat");
        em.flush();
        assertThat(saved.getUpdatedAt()).isAfter(previousUpdate);
    }

    @Test
    void databaseRejectsDuplicateProductInSameCart() {
        Product product = product();
        Cart cart = Cart.builder().member(product.getMember()).build();
        em.persist(cart);
        em.persist(CartItem.builder().cart(cart).product(product).build());
        em.flush();
        em.persist(CartItem.builder().cart(cart).product(product).build());
        assertThatThrownBy(em::flush).isInstanceOf(PersistenceException.class);
    }

    @Test
    void tryOnAllowsDressButRejectsMixingDressAndTop() {
        Product dress = product();
        TryOnImage image = TryOnImage.builder().member(dress.getMember()).modelType(TryOnModelType.FEMALE)
                .dressProduct(dress).imageUrl("https://example.com/tryon.png").build();
        em.persist(image);
        em.flush();
        em.clear();
        TryOnImage saved = em.find(TryOnImage.class, image.getId());
        assertThat(saved.getDressProduct().getId()).isEqualTo(dress.getId());
        saved.setTopProduct(em.getReference(Product.class, dress.getId()));
        assertThatThrownBy(em::flush).isInstanceOf(PersistenceException.class);
    }

    @Test
    void ledgerStoresPolymorphicOwnersAndLinkedCounterpartEntries() {
        Account member = account(Role.MEMBER);
        Wallet wallet = Wallet.builder().member(member).build();
        PlatformFund fund = PlatformFund.builder().build();
        em.persist(wallet);
        em.persist(fund);
        WalletTransaction debit = WalletTransaction.builder().walletType(WalletType.MEMBER)
                .walletId(wallet.getId()).type(WalletTransactionType.WITHDRAWAL_FEE)
                .amount(new BigDecimal("-10000.00")).build();
        em.persist(debit);
        WalletTransaction credit = WalletTransaction.builder().walletType(WalletType.PLATFORM)
                .walletId(fund.getId()).type(WalletTransactionType.WITHDRAWAL_FEE_REVENUE)
                .amount(new BigDecimal("10000.00")).counterpartTransaction(debit).build();
        em.persist(credit);
        debit.setCounterpartTransaction(credit);
        em.flush();
        em.clear();

        WalletTransaction saved = em.find(WalletTransaction.class, debit.getId());
        assertThat(saved.getCounterpartTransaction().getCounterpartTransaction().getId()).isEqualTo(saved.getId());
        assertThat(saved.getAmount().add(saved.getCounterpartTransaction().getAmount())).isZero();
        assertThat(em.find(Wallet.class, wallet.getId()).getLockedBalance()).isZero();
    }

    private Account account(Role role) {
        Account account = Account.builder().email(UUID.randomUUID() + "@example.com").role(role).build();
        em.persist(account);
        return account;
    }

    private Product product() {
        Account member = account(Role.MEMBER);
        Account moderator = account(Role.MODERATOR);
        Warehouse warehouse = Warehouse.builder().name("Q7").address("Test address")
                .latitude(new BigDecimal("10.7000000")).longitude(new BigDecimal("106.7000000"))
                .openingHours("09:00-18:00").build();
        Category category = Category.builder().name("Coat").tryonSlot(TryOnSlot.TOP).build();
        Brand brand = Brand.builder().name("Test brand").build();
        em.persist(warehouse);
        em.persist(category);
        em.persist(brand);
        ConsignmentForm form = ConsignmentForm.builder().member(member).warehouse(warehouse)
                .category(category).brand(brand).productName("Coat").description("Good condition")
                .desiredPrice(new BigDecimal("150000.25")).build();
        em.persist(form);
        ConsignmentReceipt receipt = ConsignmentReceipt.builder().receiptNo("KG-" + UUID.randomUUID())
                .consignmentForm(form).member(member).moderator(moderator).warehouse(warehouse)
                .agreedPrice(new BigDecimal("150000.25")).floorPrice(new BigDecimal("75000.13"))
                .withdrawalFee(new BigDecimal("10000.00")).confirmedAt(Instant.now())
                .pdfUrl("https://example.com/receipt.pdf").build();
        em.persist(receipt);
        Product product = Product.builder().id("Q7-" + UUID.randomUUID()).consignmentReceipt(receipt)
                .member(member).warehouse(warehouse).category(category).brand(brand).name("Coat")
                .conditionNote("Good condition").currentPrice(new BigDecimal("150000.25"))
                .termStartedAt(Instant.now()).build();
        em.persist(product);
        return product;
    }
}
