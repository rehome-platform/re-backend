package com.rehome.rehome.entity.enums;

public enum LiveTryOnStatus {
    // Vừa mint token Decart, chưa rõ Member có thực sự kết nối camera hay không
    STARTED,
    // Mobile báo đã quay xong và tải video kết quả lên
    COMPLETED,
    // Mobile báo lỗi (không kết nối được, bị thoát giữa chừng...) hoặc không có video nào được tải lên
    FAILED
}
