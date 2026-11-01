package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.LegalFileDownload;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.LegalFileKind;
import java.util.UUID;

/**
 * Đọc ảnh/PDF giấy tờ pháp lý của tổ chức (F-139).
 *
 * <p>File vẫn nằm trên storage như cũ, nhưng URL của nó không ra khỏi server nữa: server tự
 * đọc byte rồi stream qua endpoint có kiểm quyền, nên chỉ Moderator (và chính tổ chức đó)
 * xem được — OVERVIEW mục 9.
 */
public interface OrganizationLegalFileService {

    /**
     * @param documentId chỉ dùng khi kind = {@link LegalFileKind#DOCUMENT}
     * @throws com.rehome.rehome.exception.AppException 403 nếu người gọi không có quyền,
     *         404 nếu hồ sơ/giấy tờ không tồn tại hoặc ảnh đã bị xóa khi hết hạn giữ
     */
    LegalFileDownload download(Account caller, UUID organizationId, LegalFileKind kind, UUID documentId);
}
