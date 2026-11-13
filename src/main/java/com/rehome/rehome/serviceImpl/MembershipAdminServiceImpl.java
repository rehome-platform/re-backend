package com.rehome.rehome.serviceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rehome.rehome.component.AiTryOnQuotaGuard;
import com.rehome.rehome.component.AuditLogger;
import com.rehome.rehome.component.FeaturedListingManager;
import com.rehome.rehome.component.LiveTryOnQuotaGuard;
import com.rehome.rehome.dto.request.CancelMembershipRequest;
import com.rehome.rehome.dto.request.GrantMembershipRequest;
import com.rehome.rehome.dto.request.UpdateMembershipPlanRequest;
import com.rehome.rehome.dto.response.AdminMembershipPlanResponse;
import com.rehome.rehome.dto.response.AdminMembershipSubscriptionResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.MemberProfile;
import com.rehome.rehome.entity.enums.BillingCycle;
import com.rehome.rehome.entity.enums.ConsignmentFormStatus;
import com.rehome.rehome.entity.enums.MembershipTier;
import com.rehome.rehome.entity.enums.NotificationType;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.enums.SubscriptionStatus;
import com.rehome.rehome.entity.membership.MemberSubscription;
import com.rehome.rehome.entity.membership.MemberVoucher;
import com.rehome.rehome.entity.membership.MembershipPayment;
import com.rehome.rehome.entity.membership.MembershipPlan;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.ConsignmentFormRepository;
import com.rehome.rehome.repository.MemberProfileRepository;
import com.rehome.rehome.repository.MemberSubscriptionRepository;
import com.rehome.rehome.repository.MemberVoucherRepository;
import com.rehome.rehome.repository.MembershipPaymentRepository;
import com.rehome.rehome.repository.MembershipPlanRepository;
import com.rehome.rehome.repository.MembershipSubscriptionSearchRepository;
import com.rehome.rehome.repository.OrderRepository;
import com.rehome.rehome.service.MembershipAdminService;
import com.rehome.rehome.service.NotificationService;
import com.rehome.rehome.service.VoucherIssueService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Admin quản lý gói thành viên (màn W-01, W-02, W-03).
 *
 * <p>Mọi thao tác ghi đều ghi {@code AuditLog} - đây là nơi Admin đổi được giá và quyền lợi của
 * sản phẩm đang bán, nên phải truy được ai đổi gì lúc nào.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class MembershipAdminServiceImpl implements MembershipAdminService {

    private static final int MIN_MAX_SECONDS = 5;
    private static final int MAX_MAX_SECONDS = 120;
    // Khớp khoảng đang khai cho max_pending_forms_per_member ở SystemConfigCatalog
    private static final int MIN_PENDING_FORMS = 1;
    private static final int MAX_PENDING_FORMS = 50;
    private static final int MAX_GRANT_MONTHS = 24;

    private final MembershipPlanRepository membershipPlanRepository;
    private final MemberSubscriptionRepository memberSubscriptionRepository;
    private final MembershipSubscriptionSearchRepository subscriptionSearchRepository;
    private final MembershipPaymentRepository membershipPaymentRepository;
    private final MemberVoucherRepository memberVoucherRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final ConsignmentFormRepository consignmentFormRepository;
    private final AccountRepository accountRepository;
    private final OrderRepository orderRepository;
    private final LiveTryOnQuotaGuard liveTryOnQuotaGuard;
    private final AiTryOnQuotaGuard aiTryOnQuotaGuard;
    private final FeaturedListingManager featuredListingManager;
    private final VoucherIssueService voucherIssueService;
    private final NotificationService notificationService;
    private final AuditLogger auditLogger;
    private final ObjectMapper objectMapper;

    // ================================================================ §9.1 Quản lý gói

    @Override
    public List<AdminMembershipPlanResponse> getPlans() {
        List<AdminMembershipPlanResponse> result = new ArrayList<>();
        for (MembershipPlan plan : membershipPlanRepository.findAllByOrderBySortOrderAsc()) {
            result.add(toResponse(plan));
        }
        return result;
    }

    @Override
    public AdminMembershipPlanResponse getPlan(UUID id) {
        return toResponse(loadPlan(id));
    }

    @Override
    @Transactional
    public AdminMembershipPlanResponse updatePlan(Account admin, UUID id, UpdateMembershipPlanRequest request) {
        MembershipPlan plan = loadPlan(id);
        validate(plan, request);

        ObjectNode before = snapshot(plan);

        if (StringUtils.hasText(request.getName())) {
            plan.setName(request.getName().trim());
        }
        plan.setDescription(request.getDescription());

        // Dòng FREE không có giá: bỏ qua giá gửi lên và luôn ép 0 / null, để Admin sửa nhầm cũng
        // không biến hạng mặc định thành hạng phải trả tiền.
        if (plan.getTier() == MembershipTier.FREE) {
            plan.setPricePerMonth(BigDecimal.ZERO);
            plan.setPricePerYear(null);
            plan.setPurchasable(false);
        } else {
            plan.setPricePerMonth(request.getPricePerMonth());
            plan.setPricePerYear(request.getPricePerYear());
            if (request.getActive() != null) {
                plan.setActive(request.getActive());
            }
        }

        plan.setLiveTryonPerDay(request.getLiveTryonPerDay());
        plan.setLiveTryonPerMonth(request.getLiveTryonPerMonth());
        plan.setLiveTryonMaxSeconds(request.getLiveTryonMaxSeconds());
        plan.setAiTryonPerDay(request.getAiTryonPerDay());
        plan.setAiTryonPerMonth(request.getAiTryonPerMonth());
        plan.setFeaturedListing(Boolean.TRUE.equals(request.getFeaturedListing()));
        plan.setMaxPendingConsignmentForms(request.getMaxPendingConsignmentForms());
        if (request.getSortOrder() != null) {
            plan.setSortOrder(request.getSortOrder());
        }
        membershipPlanRepository.save(plan);

        int upgraded = raiseActiveSnapshots(plan);

        ObjectNode detail = objectMapper.createObjectNode();
        detail.set("before", before);
        detail.set("after", snapshot(plan));
        detail.put("activeSubscriptionsUpgraded", upgraded);
        auditLogger.log(admin, "UPDATE_MEMBERSHIP_PLAN", "MEMBERSHIP_PLAN", plan.getId().toString(), detail);

        return toResponse(plan);
    }

    /**
     * Áp hạn mức mới cho các subscription đang {@code ACTIVE} - <b>chỉ theo hướng tăng</b> (§17 mục 15).
     *
     * <p>Giảm hạn mức chỉ áp từ kỳ gia hạn tiếp theo: người đã trả tiền cho mức cũ phải giữ mức cũ,
     * nếu không thì Admin hạ số lượt là cắt quyền lợi của người đang dùng. Form W-01 bắt buộc hiện
     * cảnh báo đúng nội dung này.
     *
     * <p>{@code null} ở plan = không giới hạn, nên luôn là "cao hơn" mọi con số.
     * {@code featured_listing} thì áp ngay cả hai hướng: nó là bật/tắt, giữ nửa vời sẽ khiến
     * danh sách sàn mâu thuẫn với bảng quyền lợi đang hiển thị.
     */
    private int raiseActiveSnapshots(MembershipPlan plan) {
        List<MemberSubscription> actives = memberSubscriptionRepository
                .findAllByTierAndStatus(plan.getTier(), SubscriptionStatus.ACTIVE);

        int changed = 0;
        for (MemberSubscription s : actives) {
            boolean touched = false;

            if (isHigher(plan.getLiveTryonPerDay(), s.getLiveTryonPerDay())) {
                s.setLiveTryonPerDay(plan.getLiveTryonPerDay());
                touched = true;
            }
            if (isHigher(plan.getLiveTryonPerMonth(), s.getLiveTryonPerMonth())) {
                s.setLiveTryonPerMonth(plan.getLiveTryonPerMonth());
                touched = true;
            }
            if (plan.getLiveTryonMaxSeconds() > s.getLiveTryonMaxSeconds()) {
                s.setLiveTryonMaxSeconds(plan.getLiveTryonMaxSeconds());
                touched = true;
            }
            if (isHigher(plan.getAiTryonPerDay(), s.getAiTryonPerDay())) {
                s.setAiTryonPerDay(plan.getAiTryonPerDay());
                touched = true;
            }
            if (isHigher(plan.getAiTryonPerMonth(), s.getAiTryonPerMonth())) {
                s.setAiTryonPerMonth(plan.getAiTryonPerMonth());
                touched = true;
            }
            if (plan.getMaxPendingConsignmentForms() > s.getMaxPendingConsignmentForms()) {
                s.setMaxPendingConsignmentForms(plan.getMaxPendingConsignmentForms());
                touched = true;
            }
            if (!Objects.equals(plan.getFeaturedListing(), s.getFeaturedListing())) {
                s.setFeaturedListing(plan.getFeaturedListing());
                touched = true;
                // Bật/tắt nhãn "Nổi bật" cho toàn bộ hàng của Member theo quyền lợi mới
                memberSubscriptionRepository.save(s);
                featuredListingManager.applyForSubscription(s);
            }

            if (touched) {
                memberSubscriptionRepository.save(s);
                changed++;
            }
        }
        if (changed > 0) {
            log.info(">>>> Nang han muc cho {} subscription ACTIVE cua hang {}", changed, plan.getTier());
        }
        return changed;
    }

    /** {@code null} = không giới hạn nên luôn cao hơn mọi con số; null so với null thì không đổi. */
    private static boolean isHigher(Integer planLimit, Integer currentLimit) {
        if (planLimit == null) {
            return currentLimit != null;
        }
        if (currentLimit == null) {
            return false;
        }
        return planLimit > currentLimit;
    }

    private void validate(MembershipPlan plan, UpdateMembershipPlanRequest r) {
        if (r == null) {
            throw new BadRequestException("Thiếu nội dung cập nhật.");
        }
        if (plan.getTier() != MembershipTier.FREE) {
            if (r.getPricePerMonth() == null || r.getPricePerMonth().signum() < 0) {
                throw new AppException(ErrorCode.MEMBERSHIP_PLAN_INVALID);
            }
            if (r.getPricePerYear() != null && r.getPricePerYear().signum() <= 0) {
                throw new AppException(ErrorCode.MEMBERSHIP_PLAN_INVALID);
            }
        }
        requireNonNegativeOrUnlimited(r.getLiveTryonPerDay());
        requireNonNegativeOrUnlimited(r.getLiveTryonPerMonth());
        requireNonNegativeOrUnlimited(r.getAiTryonPerDay());
        requireNonNegativeOrUnlimited(r.getAiTryonPerMonth());

        // Trần tháng phải >= hạn mức ngày khi cả hai đều có số, nếu không trần tháng vô nghĩa
        // (chặn ngay từ lượt đầu của ngày đầu tháng)
        if (r.getLiveTryonPerDay() != null && r.getLiveTryonPerMonth() != null
                && r.getLiveTryonPerDay() > r.getLiveTryonPerMonth()) {
            throw new AppException(ErrorCode.MEMBERSHIP_PLAN_INVALID);
        }
        if (r.getAiTryonPerDay() != null && r.getAiTryonPerMonth() != null
                && r.getAiTryonPerDay() > r.getAiTryonPerMonth()) {
            throw new AppException(ErrorCode.MEMBERSHIP_PLAN_INVALID);
        }

        if (r.getLiveTryonMaxSeconds() == null
                || r.getLiveTryonMaxSeconds() < MIN_MAX_SECONDS
                || r.getLiveTryonMaxSeconds() > MAX_MAX_SECONDS) {
            throw new AppException(ErrorCode.MEMBERSHIP_PLAN_INVALID);
        }
        if (r.getMaxPendingConsignmentForms() == null
                || r.getMaxPendingConsignmentForms() < MIN_PENDING_FORMS
                || r.getMaxPendingConsignmentForms() > MAX_PENDING_FORMS) {
            throw new AppException(ErrorCode.MEMBERSHIP_PLAN_INVALID);
        }
    }

    private static void requireNonNegativeOrUnlimited(Integer limit) {
        if (limit != null && limit < 0) {
            throw new AppException(ErrorCode.MEMBERSHIP_PLAN_INVALID);
        }
    }

    private MembershipPlan loadPlan(UUID id) {
        return membershipPlanRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.MEMBERSHIP_PLAN_NOT_FOUND));
    }

    private ObjectNode snapshot(MembershipPlan p) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("tier", p.getTier().name());
        n.put("name", p.getName());
        n.put("pricePerMonth", p.getPricePerMonth());
        n.put("pricePerYear", p.getPricePerYear());
        n.put("liveTryonPerDay", p.getLiveTryonPerDay());
        n.put("liveTryonPerMonth", p.getLiveTryonPerMonth());
        n.put("liveTryonMaxSeconds", p.getLiveTryonMaxSeconds());
        n.put("aiTryonPerDay", p.getAiTryonPerDay());
        n.put("aiTryonPerMonth", p.getAiTryonPerMonth());
        n.put("featuredListing", p.getFeaturedListing());
        n.put("maxPendingConsignmentForms", p.getMaxPendingConsignmentForms());
        n.put("active", p.getActive());
        n.put("sortOrder", p.getSortOrder());
        return n;
    }

    private AdminMembershipPlanResponse toResponse(MembershipPlan p) {
        return AdminMembershipPlanResponse.builder()
                .id(p.getId().toString())
                .tier(p.getTier())
                .name(p.getName())
                .description(p.getDescription())
                .pricePerMonth(p.getPricePerMonth())
                .pricePerYear(p.getPricePerYear())
                .liveTryonPerDay(p.getLiveTryonPerDay())
                .liveTryonPerMonth(p.getLiveTryonPerMonth())
                .liveTryonMaxSeconds(p.getLiveTryonMaxSeconds())
                .aiTryonPerDay(p.getAiTryonPerDay())
                .aiTryonPerMonth(p.getAiTryonPerMonth())
                .featuredListing(p.getFeaturedListing())
                .maxPendingConsignmentForms(p.getMaxPendingConsignmentForms())
                .purchasable(p.getPurchasable())
                .active(p.getActive())
                .sortOrder(p.getSortOrder())
                .activeSubscriberCount(memberSubscriptionRepository
                        .countByPlan_IdAndStatus(p.getId(), SubscriptionStatus.ACTIVE))
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    // ================================================================ §9.2 Quản lý thành viên VIP

    @Override
    public PageResponse<AdminMembershipSubscriptionResponse> searchSubscriptions(
            MembershipTier tier, SubscriptionStatus status, String q, Integer expiringInDays,
            int page, int size) {

        String keyword = StringUtils.hasText(q) ? "%" + q.trim().toLowerCase() + "%" : null;
        Instant expiresBefore = expiringInDays == null
                ? null
                : Instant.now().plus(Math.max(expiringInDays, 0), ChronoUnit.DAYS);

        var result = subscriptionSearchRepository.search(tier, status, keyword, expiresBefore,
                PageRequest.of(Math.max(page, 0), Math.max(size, 1)));

        Map<UUID, MemberProfile> profiles = loadProfiles(
                result.getContent().stream().map(s -> s.getMember().getId()).toList());

        Instant now = Instant.now();
        return PageResponse.from(result.map(s -> toListItem(s, profiles.get(s.getMember().getId()), now)));
    }

    private Map<UUID, MemberProfile> loadProfiles(List<UUID> accountIds) {
        Map<UUID, MemberProfile> byAccount = new LinkedHashMap<>();
        if (accountIds.isEmpty()) {
            return byAccount;
        }
        for (MemberProfile p : memberProfileRepository.findAllById(accountIds)) {
            byAccount.put(p.getAccountId(), p);
        }
        return byAccount;
    }

    private AdminMembershipSubscriptionResponse toListItem(MemberSubscription s, MemberProfile profile,
                                                            Instant now) {
        return AdminMembershipSubscriptionResponse.builder()
                .id(s.getId().toString())
                .memberId(s.getMember().getId().toString())
                .memberEmail(s.getMember().getEmail())
                .memberName(profile == null ? null : profile.getFullName())
                .memberCode(profile == null ? null : profile.getMemberCode())
                .tier(s.getTier())
                .tierName(s.getPlan().getName())
                .status(s.getStatus())
                .billingCycle(s.getBillingCycle())
                .pricePaid(s.getPricePaid())
                .startedAt(s.getStartedAt())
                .expiresAt(s.getExpiresAt())
                .daysRemaining(daysRemaining(s.getExpiresAt(), now))
                .grantedByAdmin(s.getGrantedByAdmin() != null)
                .cancelReason(s.getCancelReason())
                .build();
    }

    private static Integer daysRemaining(Instant expiresAt, Instant now) {
        if (expiresAt == null) {
            return null;
        }
        if (!expiresAt.isAfter(now)) {
            return 0;
        }
        return (int) ((Duration.between(now, expiresAt).toHours() + 23) / 24);
    }

    @Override
    public AdminMembershipSubscriptionResponse getSubscription(UUID id) {
        MemberSubscription s = memberSubscriptionRepository.findWithDetailsById(id)
                .orElseThrow(() -> new AppException(ErrorCode.MEMBERSHIP_SUBSCRIPTION_NOT_FOUND));
        UUID memberId = s.getMember().getId();
        Instant now = Instant.now();

        AdminMembershipSubscriptionResponse base =
                toListItem(s, memberProfileRepository.findById(memberId).orElse(null), now);

        base.setQuotaUsage(AdminMembershipSubscriptionResponse.QuotaUsage.builder()
                .liveTryonUsedToday(liveTryOnQuotaGuard.countToday(memberId))
                .liveTryonUsedThisMonth(liveTryOnQuotaGuard.countThisMonth(memberId))
                .aiTryonUsedToday(aiTryOnQuotaGuard.countToday(memberId))
                .aiTryonUsedThisMonth(aiTryOnQuotaGuard.countThisMonth(memberId))
                .featuredListing(Boolean.TRUE.equals(s.getFeaturedListing()))
                .featuredProductCount(featuredListingManager.countFeatured(memberId))
                .pendingConsignmentForms(consignmentFormRepository
                        .countByMember_IdAndStatus(memberId, ConsignmentFormStatus.PENDING))
                .build());

        List<AdminMembershipSubscriptionResponse.PaymentRef> payments = new ArrayList<>();
        for (MembershipPayment p : membershipPaymentRepository
                .findAllBySubscription_IdOrderByCreatedAtAsc(id)) {
            payments.add(AdminMembershipSubscriptionResponse.PaymentRef.builder()
                    .amount(p.getAmount())
                    .method(p.getMethod())
                    .status(p.getStatus())
                    .paidAt(p.getPaidAt())
                    .build());
        }
        base.setPayments(payments);

        List<MemberVoucher> vouchers =
                memberVoucherRepository.findAllBySourceSubscription_IdOrderByIssuedAtAsc(id);
        Map<UUID, List<String>> orderNos = new LinkedHashMap<>();
        if (!vouchers.isEmpty()) {
            for (Object[] row : orderRepository.findVoucherIdAndOrderNoByVoucherIds(
                    vouchers.stream().map(MemberVoucher::getId).toList())) {
                orderNos.computeIfAbsent((UUID) row[0], k -> new ArrayList<>()).add((String) row[1]);
            }
        }
        List<AdminMembershipSubscriptionResponse.VoucherRef> voucherRefs = new ArrayList<>();
        for (MemberVoucher v : vouchers) {
            voucherRefs.add(AdminMembershipSubscriptionResponse.VoucherRef.builder()
                    .code(v.getCode())
                    .name(v.getVoucherTemplate().getName())
                    .status(v.getStatus().name())
                    .usedOrderNos(orderNos.getOrDefault(v.getId(), List.of()))
                    .build());
        }
        base.setVouchers(voucherRefs);

        return base;
    }

    // ---- Tặng gói ----

    @Override
    @Transactional
    public AdminMembershipSubscriptionResponse grant(Account admin, GrantMembershipRequest request) {
        if (request == null || request.getMemberId() == null || request.getTier() == null) {
            throw new BadRequestException("memberId và tier là bắt buộc.");
        }
        if (request.getTier() == MembershipTier.FREE) {
            throw new BadRequestException("Không tặng được hạng FREE - đây là hạng mặc định của mọi Member.");
        }
        int months = request.getMonths() == null ? 1 : request.getMonths();
        if (months < 1 || months > MAX_GRANT_MONTHS) {
            throw new BadRequestException("months phải từ 1 đến " + MAX_GRANT_MONTHS + ".");
        }
        if (!StringUtils.hasText(request.getReason())) {
            throw new BadRequestException("reason là bắt buộc để ghi audit log.");
        }

        Account member = accountRepository.findById(request.getMemberId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản."));
        if (member.getRole() != Role.MEMBER) {
            throw new BadRequestException("Chỉ tặng gói cho tài khoản Member.");
        }

        MembershipPlan plan = membershipPlanRepository.findByTier(request.getTier())
                .orElseThrow(() -> new AppException(ErrorCode.MEMBERSHIP_PLAN_NOT_FOUND));
        // Cố ý KHÔNG kiểm tra plan.active/purchasable: Admin tặng được cả gói đã ngừng bán
        // (ví dụ đền bù cho người từng dùng gói đó).

        MemberSubscription current = memberSubscriptionRepository
                .findActiveByMemberIdForUpdate(member.getId())
                .filter(s -> s.isEffectiveAt(Instant.now()))
                .orElse(null);

        MemberSubscription subscription = grantActivate(member, plan, months, current, admin,
                request.getReason());

        int vouchersIssued = voucherIssueService.issueForSubscription(subscription);

        notificationService.notify(member, NotificationType.MEMBERSHIP_EVENT,
                "Bạn được tặng " + plan.getName(),
                "Gói có hiệu lực đến " + MembershipServiceImpl.formatVnDate(subscription.getExpiresAt())
                        + ". Lý do: " + request.getReason(),
                null, null, null);

        ObjectNode detail = objectMapper.createObjectNode();
        detail.put("memberId", member.getId().toString());
        detail.put("tier", plan.getTier().name());
        detail.put("months", months);
        detail.put("reason", request.getReason());
        detail.put("vouchersIssued", vouchersIssued);
        detail.put("expiresAt", String.valueOf(subscription.getExpiresAt()));
        auditLogger.log(admin, "GRANT_MEMBERSHIP", "MEMBER_SUBSCRIPTION",
                subscription.getId().toString(), detail);

        return toListItem(subscription, memberProfileRepository.findById(member.getId()).orElse(null),
                Instant.now());
    }

    /**
     * Cùng hai nhánh cộng dồn / thay thế như luồng mua ({@code MembershipServiceImpl.activate}),
     * nhưng {@code price_paid = 0} và {@code granted_by_admin_id} khác null nên W-02 hiện nguồn
     * "Admin tặng", và không ghi dòng {@code membership_payments} nào - không có tiền nào được thu.
     */
    private MemberSubscription grantActivate(Account member, MembershipPlan plan, int months,
                                              MemberSubscription current, Account admin, String reason) {
        Instant now = Instant.now();

        if (current != null && current.getTier() == plan.getTier()) {
            current.setExpiresAt(current.getExpiresAt().atZone(com.rehome.rehome.util.VnTimeWindow.VN_ZONE)
                    .plusMonths(months).toInstant());
            current.setExpiryReminderSentAt(null);
            memberSubscriptionRepository.save(current);
            featuredListingManager.applyForSubscription(current);
            return current;
        }

        if (current != null) {
            current.setStatus(SubscriptionStatus.REPLACED);
            current.setCancelledAt(now);
            current.setCancelReason("Admin tặng gói " + plan.getName() + ": " + reason);
            memberSubscriptionRepository.save(current);
        }

        MemberSubscription fresh = MemberSubscription.builder()
                .member(member)
                .plan(plan)
                .tier(plan.getTier())
                .billingCycle(months % 12 == 0 ? BillingCycle.YEARLY : BillingCycle.MONTHLY)
                .pricePaid(BigDecimal.ZERO)
                .liveTryonPerDay(plan.getLiveTryonPerDay())
                .liveTryonPerMonth(plan.getLiveTryonPerMonth())
                .liveTryonMaxSeconds(plan.getLiveTryonMaxSeconds())
                .aiTryonPerDay(plan.getAiTryonPerDay())
                .aiTryonPerMonth(plan.getAiTryonPerMonth())
                .featuredListing(plan.getFeaturedListing())
                .maxPendingConsignmentForms(plan.getMaxPendingConsignmentForms())
                .status(SubscriptionStatus.ACTIVE)
                .startedAt(now)
                .expiresAt(now.atZone(com.rehome.rehome.util.VnTimeWindow.VN_ZONE)
                        .plusMonths(months).toInstant())
                .grantedByAdmin(admin)
                .build();
        memberSubscriptionRepository.save(fresh);
        featuredListingManager.applyForSubscription(fresh);
        return fresh;
    }

    // ---- Hủy gói ----

    @Override
    @Transactional
    public AdminMembershipSubscriptionResponse cancel(Account admin, UUID id, CancelMembershipRequest request) {
        if (request == null || !StringUtils.hasText(request.getReason())) {
            throw new BadRequestException("reason là bắt buộc để ghi audit log.");
        }
        MemberSubscription s = memberSubscriptionRepository.findWithDetailsById(id)
                .orElseThrow(() -> new AppException(ErrorCode.MEMBERSHIP_SUBSCRIPTION_NOT_FOUND));

        if (s.getStatus() != SubscriptionStatus.ACTIVE
                && s.getStatus() != SubscriptionStatus.PENDING_PAYMENT) {
            throw new AppException(ErrorCode.MEMBERSHIP_SUBSCRIPTION_NOT_ACTIVE);
        }

        s.setStatus(SubscriptionStatus.CANCELLED);
        s.setCancelledAt(Instant.now());
        s.setCancelReason(request.getReason().trim());
        memberSubscriptionRepository.save(s);

        // Tắt nhãn "Nổi bật" trên mọi món của Member - quyền lợi dừng ngay khi gói bị hủy.
        // Voucher đã phát vẫn dùng được tới khi hết hạn của chính nó: thu hồi mã đã tặng là
        // thay đổi hợp đồng với người mua, không phải việc của thao tác hủy gói.
        featuredListingManager.clearForMember(s.getMember().getId());

        notificationService.notify(s.getMember(), NotificationType.MEMBERSHIP_EVENT,
                "Gói thành viên đã bị hủy",
                s.getPlan().getName() + " của bạn đã dừng. Lý do: " + request.getReason().trim(),
                null, null, null);

        ObjectNode detail = objectMapper.createObjectNode();
        detail.put("memberId", s.getMember().getId().toString());
        detail.put("tier", s.getTier().name());
        detail.put("reason", s.getCancelReason());
        auditLogger.log(admin, "CANCEL_MEMBERSHIP", "MEMBER_SUBSCRIPTION", s.getId().toString(), detail);

        return toListItem(s, memberProfileRepository.findById(s.getMember().getId()).orElse(null),
                Instant.now());
    }
}
