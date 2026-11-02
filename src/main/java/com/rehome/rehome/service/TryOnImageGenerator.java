package com.rehome.rehome.service;

import com.rehome.rehome.entity.enums.TryOnModelType;

/**
 * Cổng gọi mô hình AI sinh ảnh thử đồ (F-078). Mô hình tự host, chạy đồng bộ nên có thể mất vài chục giây.
 *
 * <p>Mọi lỗi phía AI (không cấu hình, timeout, GPU bận, mask lỗi, model lỗi, không tải được ảnh kết quả)
 * đều được impl đổi thành {@code AppException(TRYON_GENERATION_FAILED)} -> 502, để service không phải
 * biết chi tiết giao thức của AI.
 */
public interface TryOnImageGenerator {

    /** Mặc áo + quần lên người mẫu. @return bytes ảnh PNG kết quả */
    byte[] generateOutfit(TryOnModelType modelType, byte[] topImage, byte[] bottomImage);

    /** Mặc đầm lên người mẫu (AI hiện chỉ có mẫu nữ). @return bytes ảnh PNG kết quả */
    byte[] generateDress(TryOnModelType modelType, byte[] dressImage);
}