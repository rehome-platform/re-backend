package com.rehome.rehome.service;

public interface ReturnRequestAutoExpireService {

    // F-069: hủy yêu cầu trả hàng không được bàn giao cho GHN trong hạn
    void cancelNotHandedOverRequests();

    // F-071: đóng yêu cầu trả hàng không hợp lệ mà Member không đến nhận lại trong hạn
    void expireUncollectedInvalidReturns();
}
