package com.rehome.rehome.repository;

import com.rehome.rehome.client.DecartVtonClient;
import com.rehome.rehome.component.LiveTryOnQuotaGuard;
import com.rehome.rehome.component.MembershipQuotaResolver;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.LiveTryOnStatus;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.enums.TryOnSlot;
import com.rehome.rehome.entity.marketplace.*;
import com.rehome.rehome.entity.tryon.LiveTryOnSession;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.service.FileStorageService;
import com.rehome.rehome.serviceImpl.LiveTryOnServiceImpl;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest
@Import(LiveTryOnServiceImpl.class)
class LiveTryOnDeletionPersistenceTest {
    @Autowired EntityManager em;
    @Autowired LiveTryOnSessionRepository sessions;
    @Autowired LiveTryOnServiceImpl service;
    @MockBean ProductPhotoRepository photos;
    @MockBean FileStorageService storage;
    @MockBean DecartVtonClient decart;
    @MockBean LiveTryOnQuotaGuard quota;

    private Account account(String email) {
        var account = Account.builder().email(email).passwordHash("test").role(Role.MEMBER).emailVerified(true).build();
        em.persist(account);
        return account;
    }

    private LiveTryOnSession completedSession(Account member) {
        var warehouse = Warehouse.builder().name("Test warehouse").address("Test address").wardName("Ward")
                .provinceName("Province").latitude(BigDecimal.TEN).longitude(BigDecimal.TEN).openingHours("8-17").build();
        em.persist(warehouse);
        var category = Category.builder().name("Top").tryonSlot(TryOnSlot.TOP).build();
        em.persist(category);
        var brand = Brand.builder().name("Brand").build();
        em.persist(brand);
        var form = ConsignmentForm.builder().member(member).warehouse(warehouse).category(category).brand(brand)
                .productName("Shirt").description("Test").desiredPrice(BigDecimal.TEN).bringInDeadline(LocalDate.now()).build();
        em.persist(form);
        var receipt = ConsignmentReceipt.builder().receiptNo("LIVE-DELETE-RECEIPT").consignmentForm(form)
                .member(member).moderator(member).warehouse(warehouse).conditionNote("Good")
                .agreedPrice(BigDecimal.TEN).floorPrice(BigDecimal.ONE).termLengthDays(30).maxRenewals(2)
                .freeWithdrawalWindowDays(7).commissionRateTerm1(BigDecimal.ZERO).commissionRateTerm2(BigDecimal.ZERO)
                .commissionRateTerm3(BigDecimal.ZERO).withdrawalFee(BigDecimal.ZERO)
                .degradedCompensationRate(BigDecimal.ZERO).confirmedAt(Instant.now()).build();
        em.persist(receipt);
        var product = Product.builder().id("LIVE-DELETE-PRODUCT").consignmentReceipt(receipt).member(member)
                .warehouse(warehouse).category(category).brand(brand).name("Shirt").conditionNote("Good")
                .currentPrice(BigDecimal.TEN).status(ProductStatus.ON_SALE).termStartedAt(Instant.now()).build();
        em.persist(product);
        var session = LiveTryOnSession.builder().member(member).product(product).modelId("test")
                .status(LiveTryOnStatus.COMPLETED).videoUrl("https://storage/try-on-live/test.mp4")
                .firstFrameAt(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS))
                .completedAt(Instant.now()).build();
        em.persist(session);
        em.flush();
        return session;
    }

    @Test void deletionHidesHistoryButDoesNotRefundDailyOrMonthlyUsage() {
        var member = account("owner@example.com");
        var session = completedSession(member);
        var id = session.getId();
        var firstFrameAt = session.getFirstFrameAt();
        var counts = new LiveTryOnQuotaGuard(sessions, mock(MembershipQuotaResolver.class),
                mock(MemberProfileRepository.class), mock(AccountRepository.class));
        assertThat(counts.countToday(member.getId())).isEqualTo(1);
        assertThat(counts.countThisMonth(member.getId())).isEqualTo(1);
        assertThat(sessions.findByMember_IdAndStatusAndVideoUrlIsNotNull(
                member.getId(), LiveTryOnStatus.COMPLETED, PageRequest.of(0, 20))).hasSize(1);

        service.deleteVideo(member, id);
        em.flush();
        em.clear();

        var retained = sessions.findById(id).orElseThrow();
        assertThat(retained.getVideoUrl()).isNull();
        assertThat(retained.getFirstFrameAt()).isEqualTo(firstFrameAt);
        assertThat(retained.getStatus()).isEqualTo(LiveTryOnStatus.COMPLETED);
        assertThat(counts.countToday(member.getId())).isEqualTo(1);
        assertThat(counts.countThisMonth(member.getId())).isEqualTo(1);
        assertThat(sessions.findByMember_IdAndStatusAndVideoUrlIsNotNull(
                member.getId(), LiveTryOnStatus.COMPLETED, PageRequest.of(0, 20))).isEmpty();
    }

    @Test void anotherMemberCannotDeleteAnExistingVideo() {
        var owner = account("owner@example.com");
        var other = account("other@example.com");
        var session = completedSession(owner);
        String url = session.getVideoUrl();

        assertThatThrownBy(() -> service.deleteVideo(other, session.getId()))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TRYON_LIVE_SESSION_NOT_FOUND));
        assertThat(session.getVideoUrl()).isEqualTo(url);
        verifyNoInteractions(storage);
    }
}
