package com.rehome.rehome.service;

public interface MembershipLifecycleService {

    void expireSubscriptions();

    void remindExpiringSubscriptions();

    void issueMonthlyVouchers();

    void expireVouchers();
}
