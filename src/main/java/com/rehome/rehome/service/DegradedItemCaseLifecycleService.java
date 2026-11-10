package com.rehome.rehome.service;

public interface DegradedItemCaseLifecycleService {

    // F-036: nhắc Member chốt phương án đền bù cách 7 ngày 1 lần trong hạn 30 ngày, và tự chuyển
    // sản phẩm sang vô thừa nhận nếu quá hạn mà Member vẫn chưa chọn
    void processRemindersAndDeadlines();
}
