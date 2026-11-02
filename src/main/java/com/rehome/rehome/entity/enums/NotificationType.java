package com.rehome.rehome.entity.enums;

public enum NotificationType {
    ORDER_UPDATE,
    WALLET_EVENT,
    TERM_EXPIRY,
    VERIFICATION_RESULT,
    QUEUE_TASK,

    // Moderator ghi nhận 1 lượt quyên góp cho chiến dịch của tổ chức (F-163) -> báo cho tổ chức
    // đó, kèm targetType/targetId/warehouseId để app mở đúng màn chờ lấy (OC-07)
    DONATION_RECORDED,

    // Tổ chức trả lời bình luận của Member trên bài viết -> báo cho Member đó (targetType=POST)
    COMMENT_REPLIED,

    // Member bình luận hoặc trả lời trên bài viết -> báo cho tổ chức sở hữu bài (targetType=POST)
    POST_COMMENTED
}
