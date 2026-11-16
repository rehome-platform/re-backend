package com.rehome.rehome.serviceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rehome.rehome.constant.SystemConfigKeys;
import com.rehome.rehome.dto.request.RedeemPointsRequest;
import com.rehome.rehome.dto.response.PointsSummaryResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.MemberProfile;
import com.rehome.rehome.entity.enums.PointRedeemType;
import com.rehome.rehome.entity.marketplace.Order;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.MemberProfileRepository;
import com.rehome.rehome.service.SystemConfigReader;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PointsServiceImplTest {

    @Mock AccountRepository accountRepository;
    @Mock MemberProfileRepository memberProfileRepository;
    @Mock SystemConfigReader systemConfigReader;

    @InjectMocks PointsServiceImpl pointsService;

    private final UUID memberId = UUID.randomUUID();
    private Account member;
    private MemberProfile profile;

    @BeforeEach
    void setUp() {
        member = Account.builder().id(memberId).email("m@example.com").build();
        profile = MemberProfile.builder().accountId(memberId).pointsBalance(100).build();
        when(accountRepository.findByEmail("m@example.com")).thenReturn(Optional.of(member));
        when(memberProfileRepository.findByAccountId(memberId)).thenReturn(Optional.of(profile));
        // Mặc định mọi key lấy giá trị default truyền vào
        when(systemConfigReader.getInt(any(), anyInt())).thenAnswer(inv -> inv.getArgument(1));
    }

    private Order order(String total) {
        return Order.builder().orderNo("OD-1").buyer(member).totalAmount(new BigDecimal(total)).build();
    }

    // ---------- awardForOrder

    @Test
    void awardForOrder_floorsToWholePoints() {
        int awarded = pointsService.awardForOrder(order("199999"));   // 10.000đ / điểm -> 19

        assertThat(awarded).isEqualTo(19);
        verify(memberProfileRepository).addPoints(memberId, 19);
    }

    @Test
    void awardForOrder_awardsNothing_whenOrderBelowOnePoint() {
        assertThat(pointsService.awardForOrder(order("9999"))).isZero();
        verify(memberProfileRepository, never()).addPoints(any(), anyInt());
    }

    @Test
    void awardForOrder_awardsNothing_whenFeatureDisabled() {
        when(systemConfigReader.getInt(eq(SystemConfigKeys.POINTS_VND_PER_POINT), anyInt())).thenReturn(0);

        assertThat(pointsService.awardForOrder(order("500000"))).isZero();
        verify(memberProfileRepository, never()).addPoints(any(), anyInt());
    }

    // ---------- redeem

    @Test
    void redeem_aiTryon_chargesUnitCostTimesQuantity() {
        when(memberProfileRepository.redeemForAiTryon(memberId, 30, 3)).thenReturn(1);

        PointsSummaryResponse res = pointsService.redeem("m@example.com",
                new RedeemPointsRequest(PointRedeemType.AI_TRYON, 3));

        verify(memberProfileRepository).redeemForAiTryon(memberId, 30, 3);      // 3 x 10 điểm
        assertThat(res.getCostPerAiTryon()).isEqualTo(10);
    }

    @Test
    void redeem_liveTryon_usesLiveUnitCost() {
        when(memberProfileRepository.redeemForLiveTryon(memberId, 40, 2)).thenReturn(1);

        pointsService.redeem("m@example.com", new RedeemPointsRequest(PointRedeemType.LIVE_TRYON, 2));

        verify(memberProfileRepository).redeemForLiveTryon(memberId, 40, 2);    // 2 x 20 điểm
    }

    @Test
    void redeem_throwsConflict_whenConditionalUpdateMatchesNoRow() {
        when(memberProfileRepository.redeemForAiTryon(any(), anyInt(), anyInt())).thenReturn(0);

        assertThatThrownBy(() -> pointsService.redeem("m@example.com",
                new RedeemPointsRequest(PointRedeemType.AI_TRYON, 50)))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.POINTS_NOT_ENOUGH));
    }

    @Test
    void redeem_rejectsQuantityOutOfRange() {
        for (int bad : new int[] {0, -1, 101}) {
            assertThatThrownBy(() -> pointsService.redeem("m@example.com",
                    new RedeemPointsRequest(PointRedeemType.AI_TRYON, bad)))
                    .isInstanceOfSatisfying(AppException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.POINTS_INVALID_QUANTITY));
        }
        verify(memberProfileRepository, never()).redeemForAiTryon(any(), anyInt(), anyInt());
    }

    @Test
    void redeem_isClosed_whenUnitCostConfiguredToZero() {
        when(systemConfigReader.getInt(eq(SystemConfigKeys.POINTS_COST_PER_AI_TRYON), anyInt())).thenReturn(0);

        assertThatThrownBy(() -> pointsService.redeem("m@example.com",
                new RedeemPointsRequest(PointRedeemType.AI_TRYON, 1)))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.POINTS_REDEEM_UNAVAILABLE));
        verify(memberProfileRepository, never()).redeemForAiTryon(any(), anyInt(), anyInt());
    }
}
