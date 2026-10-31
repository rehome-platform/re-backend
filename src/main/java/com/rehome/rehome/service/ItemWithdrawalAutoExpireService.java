package com.rehome.rehome.service;

public interface ItemWithdrawalAutoExpireService {

    // F-035: hủy yêu cầu rút hàng mà Member không đến kho nhận trong hạn, đăng bán lại sản phẩm
    void expireUncollectedWithdrawals();
}
