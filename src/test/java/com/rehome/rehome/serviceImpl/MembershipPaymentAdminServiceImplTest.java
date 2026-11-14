package com.rehome.rehome.serviceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rehome.rehome.dto.response.AdminMembershipPaymentResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.MemberProfile;
import com.rehome.rehome.entity.enums.MembershipTier;
import com.rehome.rehome.entity.enums.OrderPaymentMethod;
import com.rehome.rehome.entity.enums.PaymentStatus;
import com.rehome.rehome.entity.membership.MemberSubscription;
import com.rehome.rehome.entity.membership.MembershipPayment;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.MemberProfileRepository;
import com.rehome.rehome.repository.MembershipPaymentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class MembershipPaymentAdminServiceImplTest {

    @Mock MembershipPaymentRepository membershipPaymentRepository;
    @Mock MemberProfileRepository memberProfileRepository;
    @InjectMocks MembershipPaymentAdminServiceImpl service;

    @Test
    void search_rejectsFromAfterTo() {
        assertThatThrownBy(() -> service.search("2026-11-10", "2026-11-01", null, null, 0, 20))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_DATE_RANGE);
        verify(membershipPaymentRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void search_rejectsMalformedDate() {
        assertThatThrownBy(() -> service.search("14/11/2026", null, null, null, 0, 20))
                .isInstanceOf(AppException.class);
    }

    @Test
    void search_rejectsUnknownTierOrMethod() {
        assertThatThrownBy(() -> service.search(null, null, "PLATINUM", null, 0, 20))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.search(null, null, null, "CASH", 0, 20))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void search_rejectsNegativePageOrBadSize() {
        assertThatThrownBy(() -> service.search(null, null, null, null, -1, 20))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.search(null, null, null, null, 0, 0))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void search_mapsRowsAndSortsNewestFirst() {
        Account member = Account.builder().id(UUID.randomUUID()).email("member@example.com").build();
        MemberSubscription subscription = MemberSubscription.builder().id(UUID.randomUUID())
                .tier(MembershipTier.GOLD).build();
        Instant created = Instant.parse("2026-11-10T03:00:00Z");
        MembershipPayment payment = MembershipPayment.builder().id(UUID.randomUUID()).member(member)
                .subscription(subscription).amount(new BigDecimal("199000")).method(OrderPaymentMethod.MOMO)
                .status(PaymentStatus.PENDING).createdAt(created).build();
        when(membershipPaymentRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(payment)));
        when(memberProfileRepository.findAllByAccount_IdIn(any()))
                .thenReturn(List.of(MemberProfile.builder().accountId(member.getId()).fullName("Trần B").build()));

        PageResponse<AdminMembershipPaymentResponse> result =
                service.search("2026-11-01", "2026-11-14", "GOLD", "momo", 1, 10);

        AdminMembershipPaymentResponse row = result.getContent().get(0);
        assertThat(row.getMemberName()).isEqualTo("Trần B");
        assertThat(row.getMemberEmail()).isEqualTo("member@example.com");
        assertThat(row.getSubscriptionId()).isEqualTo(subscription.getId().toString());
        assertThat(row.getTier()).isEqualTo("GOLD");
        assertThat(row.getMethod()).isEqualTo("MOMO");
        assertThat(row.getStatus()).isEqualTo("PENDING");
        assertThat(row.getAmount()).isEqualByComparingTo("199000");
        assertThat(row.getCreatedAt()).isEqualTo(created);
        assertThat(row.getPaidAt()).isNull();

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(membershipPaymentRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
        assertThat(pageable.getValue().getSort().getOrderFor("id").isDescending()).isTrue();
    }
}
