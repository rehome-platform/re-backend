package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.MembershipStatsResponse;
import com.rehome.rehome.dto.response.VoucherStatsResponse;
import com.rehome.rehome.util.AdminDateRange;

/** §9.4 - thống kê gói và voucher cho màn W-05. Chỉ đọc, không ghi audit. */
public interface MembershipStatsService {

    MembershipStatsResponse getMembershipStats(AdminDateRange range);

    VoucherStatsResponse getVoucherStats(AdminDateRange range);
}
