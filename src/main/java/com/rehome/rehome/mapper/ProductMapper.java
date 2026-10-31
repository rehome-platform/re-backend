package com.rehome.rehome.mapper;

import com.rehome.rehome.dto.response.MyProductResponse;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.EndOfTermDecision;
import com.rehome.rehome.entity.marketplace.Order;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.util.ConsignmentCommission;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class ProductMapper {

    // Dòng của danh sách /products/mine: không có priceChanges, termHistory
    public MyProductResponse toMyProduct(Product product, String photoUrl,
                                         EndOfTermDecision decision, Order order) {
        if (product == null) {
            return null;
        }
        return base(product, photoUrl, decision, order).build();
    }

    // /products/mine/{id}: thêm lịch sử giảm giá và các kỳ
    public MyProductResponse toMyProductDetail(Product product, String photoUrl,
                                               EndOfTermDecision decision, Order order,
                                               List<MyProductResponse.PriceChange> priceChanges,
                                               List<MyProductResponse.TermInfo> termHistory) {
        if (product == null) {
            return null;
        }
        return base(product, photoUrl, decision, order)
                .priceChanges(priceChanges)
                .termHistory(termHistory)
                .build();
    }

    private MyProductResponse.MyProductResponseBuilder base(Product p, String photoUrl,
                                                            EndOfTermDecision d, Order o) {
        ConsignmentReceipt r = p.getConsignmentReceipt();
        return MyProductResponse.builder()
                .id(p.getId())
                .productName(p.getName())
                .status(p.getStatus())
                .photoUrl(photoUrl)
                .brandName(p.getBrand().getName())
                .size(p.getSize())
                .agreedPrice(r.getAgreedPrice())
                .floorPrice(r.getFloorPrice())
                .currentPrice(p.getCurrentPrice())
                .warehouseId(p.getWarehouse().getId())
                .warehouseName(p.getWarehouse().getName())
                .currentTerm(p.getCurrentTerm())
                .termStartedAt(p.getTermStartedAt())
                .currentTermEndsAt(p.getTermStartedAt().plus(Duration.ofDays(r.getTermLengthDays())))
                .endOfTermDeadline(p.getEndOfTermDeadline())
                .lastPriceChangedAt(p.getLastPriceChangedAt())
                .endOfTermDecision(d == null ? null
                        : new MyProductResponse.EndOfTermDecisionInfo(
                        d.getId(), d.getDeadline(), d.getDecision()))
                .order(o == null ? null
                        : new MyProductResponse.OrderRef(
                                o.getId(), o.getOrderNo(), o.getAutoCompleteDeadline(), o.getPayoutAt()))
                .expectedPayout(ConsignmentCommission.payout(
                        p.getCurrentPrice(),
                        ConsignmentCommission.rateForTerm(r, p.getCurrentTerm())));
    }
}