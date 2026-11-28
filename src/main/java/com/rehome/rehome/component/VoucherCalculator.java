package com.rehome.rehome.component;

import com.rehome.rehome.constant.SystemConfigKeys;
import com.rehome.rehome.entity.enums.MemberVoucherStatus;
import com.rehome.rehome.entity.enums.VoucherDiscountKind;
import com.rehome.rehome.entity.enums.VoucherType;
import com.rehome.rehome.entity.membership.MemberVoucher;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.service.SystemConfigReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VoucherCalculator {

    private static final int MONEY_SCALE = 2;

    private final SystemConfigReader systemConfigReader;

    public record OrderLine(
            String orderKey,
            BigDecimal subtotal,
            BigDecimal shippingFee,
            BigDecimal priceAtOrder,
            BigDecimal commissionRate) {}

    public record Allocation(String orderKey, BigDecimal discount) {}

    public record Result(BigDecimal totalDiscount, List<Allocation> allocations) {

        public BigDecimal discountOf(String orderKey) {
            return allocations.stream()
                    .filter(a -> a.orderKey().equals(orderKey))
                    .map(Allocation::discount)
                    .findFirst()
                    .orElse(BigDecimal.ZERO.setScale(MONEY_SCALE));
        }
    }

    public void assertUsable(MemberVoucher voucher, UUID callerId, BigDecimal totalSubtotal, Instant now) {
        if (!voucher.getMember().getId().equals(callerId)) {
            throw new AppException(ErrorCode.VOUCHER_FORBIDDEN);
        }
        if (voucher.getStatus() != MemberVoucherStatus.UNUSED) {
            throw new AppException(ErrorCode.VOUCHER_ALREADY_USED);
        }
        if (!voucher.getExpiresAt().isAfter(now)) {
            throw new AppException(ErrorCode.VOUCHER_EXPIRED);
        }
        if (totalSubtotal.compareTo(voucher.getMinOrderAmount()) < 0) {
            throw new AppException(ErrorCode.VOUCHER_MIN_ORDER_NOT_MET);
        }
    }

    public String describeIneligibility(MemberVoucher voucher, BigDecimal totalSubtotal, Instant now) {
        if (voucher.getStatus() != MemberVoucherStatus.UNUSED) {
            return "Mã đã được sử dụng";
        }
        if (!voucher.getExpiresAt().isAfter(now)) {
            return "Mã đã hết hạn";
        }
        if (totalSubtotal.compareTo(voucher.getMinOrderAmount()) < 0) {
            return "Đơn hàng chưa đạt " + formatDong(voucher.getMinOrderAmount());
        }
        return null;
    }

    public Result calculate(MemberVoucher voucher, List<OrderLine> lines, boolean throwWhenZero) {
        if (lines == null || lines.isEmpty()) {
            if (throwWhenZero) {
                throw new AppException(ErrorCode.VOUCHER_NOT_APPLICABLE);
            }
            return new Result(zero(), List.of());
        }

        if (voucher.getType() == VoucherType.SHIPPING && voucher.getDiscountKind() == VoucherDiscountKind.FIXED) {
            return allocateFixedShipping(voucher, lines, throwWhenZero);
        }

        BigDecimal commissionCapRatio = systemConfigReader.getDecimal(
                SystemConfigKeys.VOUCHER_MAX_RATIO_OF_COMMISSION,
                SystemConfigKeys.DEFAULT_VOUCHER_MAX_RATIO_OF_COMMISSION);

        List<BigDecimal> raw = new ArrayList<>(lines.size());
        BigDecimal total = BigDecimal.ZERO;
        for (OrderLine line : lines) {
            BigDecimal d = rawDiscountForLine(voucher, line, commissionCapRatio);
            raw.add(d);
            total = total.add(d);
        }

        BigDecimal cap = voucher.getMaxDiscountAmount();
        if (cap != null && total.compareTo(cap) > 0 && total.signum() > 0) {
            BigDecimal k = cap.divide(total, 10, RoundingMode.HALF_UP);
            for (int i = 0; i < raw.size(); i++) {
                raw.set(i, raw.get(i).multiply(k));
            }
            total = cap;
        }

        List<BigDecimal> rounded = new ArrayList<>(raw.size());
        BigDecimal sumRounded = BigDecimal.ZERO;
        for (BigDecimal d : raw) {
            BigDecimal r = d.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            rounded.add(r);
            sumRounded = sumRounded.add(r);
        }
        total = total.setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal drift = total.subtract(sumRounded);
        if (drift.signum() != 0) {
            int biggest = indexOfBiggestSubtotal(lines);
            rounded.set(biggest, rounded.get(biggest).add(drift));
        }

        if (total.signum() <= 0) {
            if (throwWhenZero) {
                throw new AppException(ErrorCode.VOUCHER_NOT_APPLICABLE);
            }
            return new Result(zero(), buildZeroAllocations(lines));
        }

        List<Allocation> allocations = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            allocations.add(new Allocation(lines.get(i).orderKey(), rounded.get(i)));
        }
        return new Result(total, allocations);
    }

    private Result allocateFixedShipping(MemberVoucher voucher, List<OrderLine> lines, boolean throwWhenZero) {
        BigDecimal shipping = lines.stream()
                .map(line -> line.shippingFee().max(BigDecimal.ZERO).setScale(MONEY_SCALE, RoundingMode.DOWN))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = min(shipping, voucher.getDiscountValue());
        if (voucher.getMaxDiscountAmount() != null) {
            total = min(total, voucher.getMaxDiscountAmount());
        }
        total = total.setScale(MONEY_SCALE, RoundingMode.DOWN);
        if (total.signum() <= 0) {
            if (throwWhenZero) {
                throw new AppException(ErrorCode.VOUCHER_NOT_APPLICABLE);
            }
            return new Result(zero(), buildZeroAllocations(lines));
        }

        List<Allocation> allocations = new ArrayList<>(lines.size());
        BigDecimal shippingSoFar = BigDecimal.ZERO;
        BigDecimal allocated = zero();
        for (OrderLine line : lines) {
            shippingSoFar = shippingSoFar.add(line.shippingFee().max(BigDecimal.ZERO)
                    .setScale(MONEY_SCALE, RoundingMode.DOWN));
            BigDecimal cumulative = total.multiply(shippingSoFar).divide(shipping, MONEY_SCALE, RoundingMode.HALF_UP);
            allocations.add(new Allocation(line.orderKey(), cumulative.subtract(allocated)));
            allocated = cumulative;
        }
        return new Result(total, allocations);
    }

    private BigDecimal rawDiscountForLine(MemberVoucher voucher, OrderLine line, BigDecimal commissionCapRatio) {
        BigDecimal base = voucher.getType() == VoucherType.SHIPPING
                ? line.shippingFee()
                : line.subtotal();

        BigDecimal raw = voucher.getDiscountKind() == VoucherDiscountKind.FIXED
                ? voucher.getDiscountValue()
                : base.multiply(voucher.getDiscountValue());

        if (voucher.getType() == VoucherType.SHIPPING) {
            return min(raw, line.shippingFee());
        }

        BigDecimal commission = line.priceAtOrder().multiply(line.commissionRate());
        BigDecimal commissionCap = commission.multiply(commissionCapRatio);
        return min(min(raw, commissionCap), line.subtotal());
    }

    private static int indexOfBiggestSubtotal(List<OrderLine> lines) {
        int biggest = 0;
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).subtotal().compareTo(lines.get(biggest).subtotal()) > 0) {
                biggest = i;
            }
        }
        return biggest;
    }

    private static List<Allocation> buildZeroAllocations(List<OrderLine> lines) {
        List<Allocation> zeros = new ArrayList<>(lines.size());
        for (OrderLine line : lines) {
            zeros.add(new Allocation(line.orderKey(), zero()));
        }
        return zeros;
    }

    private static BigDecimal min(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE);
    }

    private static String formatDong(BigDecimal amount) {
        return String.format("%,d", amount.setScale(0, RoundingMode.HALF_UP).longValueExact())
                .replace(',', '.') + "đ";
    }
}
