package com.rehome.rehome.service;

public interface CampaignAutoCloseService {

    // F-102: đóng các chiến dịch đã hết thời gian nhận đồ, để không còn hiển thị chiến dịch quá hạn
    void closeExpiredCampaigns();
}
