package com.rehome.rehome.mapper;

import com.rehome.rehome.dto.response.MyProductResponse;
import com.rehome.rehome.entity.enums.EndOfTermChoice;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.EndOfTermDecision;
import com.rehome.rehome.entity.marketplace.Order;
import com.rehome.rehome.entity.marketplace.OrderItem;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.util.ConsignmentCommission;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Component
public class ProductMapper {

    // Dòng của danh sách /products/mine: không có priceChanges, termHistory
    public MyProductResponse toMyProduct(Product product, String photoUrl,
                                         EndOfTermDecision decision, OrderItem orderItem) {
        if (product == null) {
            return null;
        }
        return base(product, photoUrl, decision, orderItem).build();
    }

    // /products/mine/{id}: thêm lịch sử giảm giá và các kỳ
    public MyProductResponse toMyProductDetail(Product product, String photoUrl,
                                               EndOfTermDecision decision, OrderItem orderItem,
                                               List<MyProductResponse.PriceChange> priceChanges,
                                               List<MyProductResponse.TermInfo> termHistory) {
        if (product == null) {
            return null;
        }
        return base(product, photoUrl, decision, orderItem)
                .priceChanges(priceChanges)
                .termHistory(termHistory)
                .build();
    }

    private MyProductResponse.MyProductResponseBuilder base(Product p, String photoUrl,
                                                            EndOfTermDecision d, OrderItem oi) {
        ConsignmentReceipt r = p.getConsignmentReceipt();
        Order o = oi == null ? null : oi.getOrder();
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
                                o.getId(), o.getOrderNo(), o.getStatus(),
                                oi.getCommissionRateApplied(), oi.getTermApplied(), o.getCreatedAt(),
                                o.getAutoCompleteDeadline(), o.getPayoutAt()))
                .expectedPayout(ConsignmentCommission.payout(
                        p.getCurrentPrice(),
                        ConsignmentCommission.rateForTerm(r, p.getCurrentTerm())))
                .termLengthDays(r.getTermLengthDays())
                .maxRenewals(r.getMaxRenewals())
                .commissionRateTerm1(r.getCommissionRateTerm1())
                .commissionRateTerm2(r.getCommissionRateTerm2())
                .commissionRateTerm3(r.getCommissionRateTerm3())
                .receiptId(currentReceiptId(r, d));
    }

    // Biên nhận đang áp dụng = biên nhận mới nhất đã xác nhận. Món đã ký gửi lại (RE_CONSIGN)
    // thì trả biên nhận của lần ký gửi lại, nhưng chỉ khi Member đã xác nhận; chưa xác nhận
    // thì biên nhận cũ vẫn là hợp đồng đang có hiệu lực.
    private static UUID currentReceiptId(ConsignmentReceipt original, EndOfTermDecision d) {
        if (d != null && d.getDecision() == EndOfTermChoice.RE_CONSIGN) {
            ConsignmentReceipt reConsigned = d.getNewReceipt();
            if (reConsigned != null && reConsigned.getConfirmedAt() != null) {
                return reConsigned.getId();
            }
        }
        return original.getId();
    }
}