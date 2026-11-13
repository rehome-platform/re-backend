package com.rehome.rehome.service;

import com.rehome.rehome.entity.membership.MemberSubscription;

/**
 * Phát voucher định kỳ cho một subscription. Tách khỏi {@code MembershipService} vì có hai nơi gọi
 * với kỳ vọng khác nhau: lúc kích hoạt gói (phát đợt đầu ngay, nằm trong transaction mua gói) và
 * job 00:15 hằng ngày (phát đợt tháng tiếp theo).
 */
public interface VoucherIssueService {

    /**
     * Phát voucher đợt này cho subscription, theo {@code voucher_template_tier_rules} của hạng đó,
     * rồi cập nhật {@code last_voucher_issued_at}.
     *
     * <p><b>Idempotent</b>: chạy lại hai lần trong cùng một tháng không phát đôi. Chốt là
     * {@code last_voucher_issued_at} <i>và</i> số mã đã phát từ subscription này trong tháng, kiểm
     * tra trong cùng transaction với lúc insert.
     *
     * @return số mã vừa phát (0 nếu tháng này đã phát rồi, hoặc hạng không có rule nào)
     */
    int issueForSubscription(MemberSubscription subscription);
}
