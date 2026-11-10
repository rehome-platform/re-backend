package com.rehome.rehome.entity.enums;

public enum CampaignStatus {
    OPEN,
    CLOSED,
    // Moderator ẩn do bị xử lý vi phạm (ViolationResolutionAction.HIDE_CAMPAIGN) - không lộ trên
    // mọi API công khai/tìm kiếm/cộng đồng, chỉ tổ chức sở hữu xem được kèm lý do xử lý
    HIDDEN
}
