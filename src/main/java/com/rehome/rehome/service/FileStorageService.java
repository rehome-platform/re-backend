package com.rehome.rehome.service;

import java.util.Optional;

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

    /**
     * Xóa file khi chỉ còn giữ URL công khai chứ không giữ path. Entity lưu URL
     * (OrganizationDocument.fileUrl, ảnh CCCD người đại diện) nên F-139 cần đường này.
     * Mỗi impl tự đảo ngược URL do chính nó sinh ra ở {@link #store}.
     *
     * @param url         URL đã trả về từ {@link #store}
     * @param contentType MIME type lúc lưu
     * @return true nếu file tồn tại và đã xóa; false nếu URL không thuộc storage này hoặc không có gì để xóa
     */
    boolean deleteByUrl(String url, String contentType);

    /**
     * Đọc nội dung file từ URL đã lưu. F-139: ảnh giấy tờ tổ chức chỉ được ra qua endpoint
     * có kiểm quyền, nên server phải tự đọc byte rồi stream, chứ không trả URL cho client.
     *
     * <p>Mỗi impl CHỈ nhận URL do chính nó sinh ra ở {@link #store} và trả
     * {@link Optional#empty()} với URL lạ. Đây là ràng buộc bắt buộc, không phải tiện lợi:
     * URL được đọc từ DB (do người dùng nộp lên), nhận bừa thì endpoint đọc file thành
     * bàn đạp để server đi gọi địa chỉ nội bộ bất kỳ (SSRF).
     *
     * @return nội dung file, hoặc empty nếu URL không thuộc storage này / file không còn
     */
    Optional<byte[]> loadByUrl(String url);
}