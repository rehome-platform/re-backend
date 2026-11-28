package com.rehome.rehome.service;

import com.rehome.rehome.entity.membership.MemberSubscription;

public interface VoucherIssueService {

    int issueForSubscription(MemberSubscription subscription);
}
