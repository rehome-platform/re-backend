package com.rehome.rehome.entity.enums;

/**
 * Loại tệp giấy tờ pháp lý của tổ chức mà endpoint có kiểm quyền trả về (F-139).
 * Không lưu vào DB — chỉ để endpoint nói rõ nó đang lấy tệp nào trong hồ sơ.
 */
public enum LegalFileKind {

    /** Ảnh CCCD mặt trước của người đại diện */
    CCCD_FRONT,

    /** Ảnh CCCD mặt sau của người đại diện */
    CCCD_BACK,

    /** Một giấy tờ trong danh sách documents, xác định thêm bằng documentId */
    DOCUMENT
}
