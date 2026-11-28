package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.ApplicableVouchersResponse;
import com.rehome.rehome.dto.response.MemberVoucherResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.MemberVoucherStatus;
import java.util.List;
import java.util.UUID;

public interface VoucherService {

    PageResponse<MemberVoucherResponse> getMyVouchers(Account account, MemberVoucherStatus status,
                                                      int page, int size);

    ApplicableVouchersResponse getApplicableVouchers(Account account, List<String> productIds,
                                                     UUID shippingAddressId);
}
