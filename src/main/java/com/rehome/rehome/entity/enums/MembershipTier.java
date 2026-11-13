package com.rehome.rehome.entity.enums;

/**
 * Hạng thành viên. FREE là hạng mặc định của mọi Member, KHÔNG mua được - nó tồn tại như một dòng
 * dữ liệu trong membership_plans để hạn mức miễn phí và hạn mức trả phí cùng đọc từ một nguồn.
 *
 * <p>Thứ tự khai báo chính là thứ tự từ thấp lên cao, dùng cho {@code ordinal()} khi tính
 * "hạng thấp nhất cao hơn hạng hiện tại" (upsellTier ở GET /tryon/live/quota).
 */
public enum MembershipTier {
    FREE,
    SILVER,
    GOLD,
    DIAMOND
}
