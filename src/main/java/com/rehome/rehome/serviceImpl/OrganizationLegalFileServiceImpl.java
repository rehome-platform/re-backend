package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.response.LegalFileDownload;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.OrganizationDocument;
import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.entity.auth.OrganizationRepresentative;
import com.rehome.rehome.entity.enums.LegalFileKind;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.OrganizationProfileRepository;
import com.rehome.rehome.service.FileStorageService;
import com.rehome.rehome.service.OrganizationLegalFileService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class OrganizationLegalFileServiceImpl implements OrganizationLegalFileService {

    private final OrganizationProfileRepository organizationProfileRepository;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional(readOnly = true)
    public LegalFileDownload download(Account caller, UUID organizationId,
                                     LegalFileKind kind, UUID documentId) {

        requireReader(caller, organizationId);

        OrganizationProfile profile = organizationProfileRepository.findById(organizationId)
                .orElseThrow(() -> new AppException(ErrorCode.LEGAL_FILE_NOT_FOUND));

        String storedUrl = storedUrl(profile, kind, documentId);
        if (!StringUtils.hasText(storedUrl)) {
            // Hồ sơ không nộp tệp này, hoặc ảnh đã bị job F-139 xóa khi hết hạn giữ
            throw new AppException(ErrorCode.LEGAL_FILE_NOT_FOUND);
        }

        byte[] content = fileStorageService.loadByUrl(storedUrl)
                .orElseThrow(() -> new AppException(ErrorCode.LEGAL_FILE_NOT_FOUND));

        return new LegalFileDownload(fileName(kind, documentId, storedUrl), contentTypeOf(storedUrl), content);
    }

    /**
     * Chỉ Moderator, hoặc chính tổ chức sở hữu hồ sơ. Admin cũng không xem được, đúng như
     * API 128 chỉ mở cho MODERATOR (OVERVIEW dòng 563).
     */
    private void requireReader(Account caller, UUID organizationId) {
        boolean moderator = caller.getRole() == Role.MODERATOR;
        boolean owner = caller.getRole() == Role.ORGANIZATION && caller.getId().equals(organizationId);
        if (!moderator && !owner) {
            throw new AppException(ErrorCode.LEGAL_FILE_FORBIDDEN);
        }
    }

    /** URL thật trên storage, lấy từ đúng field của hồ sơ. Không bao giờ ra khỏi service này. */
    private String storedUrl(OrganizationProfile profile, LegalFileKind kind, UUID documentId) {
        if (kind == LegalFileKind.DOCUMENT) {
            return documentUrl(profile, documentId);
        }

        OrganizationRepresentative representative = profile.getRepresentative();
        if (representative == null) {
            return null;
        }
        return kind == LegalFileKind.CCCD_FRONT
                ? representative.getCccdFrontUrl()
                : representative.getCccdBackUrl();
    }

    private String documentUrl(OrganizationProfile profile, UUID documentId) {
        List<OrganizationDocument> documents = profile.getDocuments();
        if (documentId == null || documents == null) {
            return null;
        }
        // Giấy tờ phải thuộc đúng tổ chức trên đường dẫn, không cho đổi {id} để mượn quyền
        return documents.stream()
                .filter(document -> documentId.equals(document.getId()))
                .map(OrganizationDocument::getFileUrl)
                .findFirst()
                .orElse(null);
    }

    /**
     * Tên file do server tự đặt theo loại giấy tờ, không lấy tên client đặt lúc upload —
     * tên đó đi thẳng vào header Content-Disposition.
     */
    private static String fileName(LegalFileKind kind, UUID documentId, String storedUrl) {
        String extension = extensionOf(storedUrl);
        return switch (kind) {
            case CCCD_FRONT -> "cccd-front" + extension;
            case CCCD_BACK -> "cccd-back" + extension;
            case DOCUMENT -> "giay-to-" + documentId + extension;
        };
    }

    private static String contentTypeOf(String url) {
        String lower = url.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        // Mọi đuôi còn lại đều là ảnh: /uploads chỉ nhận jpeg/png/webp và pdf
        return "image/jpeg";
    }

    private static String extensionOf(String url) {
        String lower = url.toLowerCase();
        for (String extension : new String[]{".pdf", ".png", ".webp", ".jpg", ".jpeg"}) {
            if (lower.endsWith(extension)) {
                return extension;
            }
        }
        return "";
    }
}
