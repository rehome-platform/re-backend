package com.rehome.rehome.service;

public interface OrganizationLegalImageCleanupService {

    // F-139: xóa ảnh giấy tờ của hồ sơ đã duyệt quá hạn giữ, để không lưu giữ tài liệu nhạy cảm
    void deleteExpiredLegalImages();
}
