package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.ApplicableVouchersResponse;
import com.rehome.rehome.dto.response.MemberVoucherResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.MemberVoucherStatus;
import java.util.List;
import java.util.UUID;

public interface VoucherService {

    // API §8.5 - ví voucher. status null = tất cả. UNUSED trước, trong đó sắp hết hạn lên đầu.
    PageResponse<MemberVoucherResponse> getMyVouchers(Account account, MemberVoucherStatus status,
                                                      int page, int size);

    /**
     * API §8.6 - voucher dùng được cho đơn sắp đặt, kèm số tiền giảm THẬT của từng mã.
     * Server tự tính subtotal và shippingFee từ productIds + địa chỉ, mobile không tự tính.
     *
     * @param productIds để trống = lấy cả giỏ hàng, giống POST /orders
     */
    ApplicableVouchersResponse getApplicableVouchers(Account account, List<String> productIds,
                                                     UUID shippingAddressId);
}
