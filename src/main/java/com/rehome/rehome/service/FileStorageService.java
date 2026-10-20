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
}