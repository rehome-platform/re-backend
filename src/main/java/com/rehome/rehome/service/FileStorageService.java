package com.rehome.rehome.service;

public interface FileStorageService {

    /**
     * Lưu file và trả về URL công khai để truy cập file.
     *
     * @param path        đường dẫn tương đối, ví dụ "receipts/RC-2026-000045.pdf"
     * @param content     nội dung file
     * @param contentType MIME type, ví dụ "application/pdf"
     * @return URL truy cập file
     */
    String store(String path, byte[] content, String contentType);

    /**
     * Xóa file đã lưu. Dùng cho yêu cầu "ảnh CCCD tự xóa sau khi hồ sơ được duyệt"
     * (docs/OVERVIEW.md mục 1, ràng buộc tệp hồ sơ tổ chức).
     *
     * @param path        đường dẫn tương đối đã dùng khi gọi {@link #store}
     * @param contentType MIME type lúc lưu - cần để biết tìm file ở resource_type nào trên Cloudinary
     * @return true nếu file tồn tại và đã xóa, false nếu không có gì để xóa
     */
    boolean delete(String path, String contentType);
}