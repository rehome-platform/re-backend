package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.RevenueItemResponse;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.repository.AdminReportQueryRepository;
import com.rehome.rehome.repository.AdminReportQueryRepository.SoldItemRow;
import com.rehome.rehome.service.AdminRevenueService;
import com.rehome.rehome.util.AdminDateRange;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class AdminRevenueServiceImpl implements AdminRevenueService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminReportQueryRepository reportRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RevenueItemResponse> listSoldItems(String from, String to, Integer term, int page, int size) {
        AdminDateRange range = AdminDateRange.optional(from, to);
        if (term != null && (term < 1 || term > Short.MAX_VALUE)) {
            throw new BadRequestException("term phải là số nguyên dương (kỳ 1, 2, 3...).");
        }

        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        Page<SoldItemRow> rows = reportRepository.findSoldItems(
                range.from(), range.to(), term == null ? null : term.shortValue(), pageable);
        return PageResponse.from(rows.map(AdminRevenueServiceImpl::toResponse));
    }

    private static RevenueItemResponse toResponse(SoldItemRow r) {
        return RevenueItemResponse.builder()
                .orderItemId(r.orderItemId())
                .productId(r.productId())
                .productName(r.productName())
                .orderId(r.orderId())
                .orderNo(r.orderNo())
                .price(toLong(r.priceAtOrder()))
                .termApplied(r.termApplied())
                .commissionRate(r.commissionRateApplied())
                .commissionAmount(toLong(r.priceAtOrder().multiply(r.commissionRateApplied())))
                .completedAt(r.completedAt())
                .build();
    }

    private static long toLong(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).longValue();
    }
}
