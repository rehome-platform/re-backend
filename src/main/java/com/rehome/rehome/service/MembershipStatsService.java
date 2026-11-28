package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.MembershipStatsResponse;
import com.rehome.rehome.dto.response.VoucherStatsResponse;
import com.rehome.rehome.util.AdminDateRange;

public interface MembershipStatsService {

    MembershipStatsResponse getMembershipStats(AdminDateRange range);

    VoucherStatsResponse getVoucherStats(AdminDateRange range);
}
