package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.response.AdminMembershipPaymentResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.auth.MemberProfile;
import com.rehome.rehome.entity.enums.MembershipTier;
import com.rehome.rehome.entity.enums.OrderPaymentMethod;
import com.rehome.rehome.entity.membership.MembershipPayment;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.repository.MemberProfileRepository;
import com.rehome.rehome.repository.MembershipPaymentRepository;
import com.rehome.rehome.service.MembershipPaymentAdminService;
import com.rehome.rehome.util.AdminDateRange;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MembershipPaymentAdminServiceImpl implements MembershipPaymentAdminService {

    private static final int MAX_PAGE_SIZE = 100;

    private final MembershipPaymentRepository membershipPaymentRepository;
    private final MemberProfileRepository memberProfileRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminMembershipPaymentResponse> search(
            String from, String to, String tier, String method, int page, int size) {
        // Sai định dạng hoặc from > to đều ra 400 INVALID_DATE_RANGE
        AdminDateRange range = AdminDateRange.optional(from, to);
        MembershipTier tierFilter = parseEnum(MembershipTier.class, tier, "tier");
        OrderPaymentMethod methodFilter = parseEnum(OrderPaymentMethod.class, method, "method");
        if (page < 0) {
            throw new BadRequestException("page phải >= 0.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size phải từ 1 đến " + MAX_PAGE_SIZE + ".");
        }

        boolean hasFrom = range.fromDate() != null;
        boolean hasTo = range.toDate() != null;
        Specification<MembershipPayment> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (hasFrom) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), range.from()));
            }
            if (hasTo) {
                predicates.add(cb.lessThan(root.<Instant>get("createdAt"), range.to()));
            }
            if (tierFilter != null) {
                predicates.add(cb.equal(root.get("subscription").get("tier"), tierFilter));
            }
            if (methodFilter != null) {
                predicates.add(cb.equal(root.get("method"), methodFilter));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<MembershipPayment> result = membershipPaymentRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));

        // Tên Member lấy theo lô, tránh N+1
        List<UUID> memberIds = result.getContent().stream().map(p -> p.getMember().getId()).distinct().toList();
        Map<UUID, String> nameByAccountId = new HashMap<>();
        if (!memberIds.isEmpty()) {
            for (MemberProfile profile : memberProfileRepository.findAllByAccount_IdIn(memberIds)) {
                nameByAccountId.put(profile.getAccountId(), profile.getFullName());
            }
        }

        return PageResponse.from(result.map(p -> AdminMembershipPaymentResponse.from(
                p, nameByAccountId.get(p.getMember().getId()))));
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(name + " không hợp lệ.");
        }
    }
}
