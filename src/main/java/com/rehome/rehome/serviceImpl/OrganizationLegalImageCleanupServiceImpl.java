package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.constant.SystemConfigKeys;
import com.rehome.rehome.entity.auth.OrganizationDocument;
import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.entity.auth.OrganizationRepresentative;
import com.rehome.rehome.entity.enums.VerificationStatus;
import com.rehome.rehome.repository.OrganizationProfileRepository;
import com.rehome.rehome.service.FileStorageService;
import com.rehome.rehome.service.OrganizationLegalImageCleanupService;
import com.rehome.rehome.service.SystemConfigReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

// F-139: hồ sơ đã duyệt quá thời hạn giữ ảnh -> xóa file trên storage và bỏ URL trong DB.
// Bản ghi giấy tờ vẫn giữ lại (doc_type, số, ngày cấp, cơ quan cấp) để còn dấu vết đã kiểm gì.
@Service
@RequiredArgsConstructor
@Slf4j
public class OrganizationLegalImageCleanupServiceImpl implements OrganizationLegalImageCleanupService {

    private static final String IMAGE_CONTENT_TYPE = "image/jpeg";

    private final OrganizationProfileRepository organizationProfileRepository;
    private final FileStorageService fileStorageService;
    private final SystemConfigReader systemConfigReader;

    @Override
    @Scheduled(fixedRateString = "${app.organization.legal-image-cleanup-check-interval-ms}")
    @Transactional
    public void deleteExpiredLegalImages() {

        int retentionDays = systemConfigReader.getInt(
                SystemConfigKeys.LEGAL_IMAGE_RETENTION_DAYS,
                SystemConfigKeys.DEFAULT_LEGAL_IMAGE_RETENTION_DAYS);
        Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));

        List<OrganizationProfile> expired = organizationProfileRepository
                .findAllByVerificationStatusAndVerifiedAtBefore(VerificationStatus.VERIFIED, cutoff);

        for (OrganizationProfile profile : expired) {
            int deleted = deleteRepresentativeCccd(profile) + deleteDocumentFiles(profile);
            if (deleted > 0) {
                organizationProfileRepository.save(profile);
                log.info(">>>> Da xoa anh giay to het han giu: organizationId={}, soFile={}",
                        profile.getAccountId(), deleted);
            }
        }
    }

    private int deleteRepresentativeCccd(OrganizationProfile profile) {
        OrganizationRepresentative representative = profile.getRepresentative();
        if (representative == null) {
            return 0;
        }

        int deleted = 0;
        if (StringUtils.hasText(representative.getCccdFrontUrl())) {
            removeFile(representative.getCccdFrontUrl(), profile);
            representative.setCccdFrontUrl(null);
            deleted++;
        }
        if (StringUtils.hasText(representative.getCccdBackUrl())) {
            removeFile(representative.getCccdBackUrl(), profile);
            representative.setCccdBackUrl(null);
            deleted++;
        }
        return deleted;
    }

    private int deleteDocumentFiles(OrganizationProfile profile) {
        if (profile.getDocuments() == null) {
            return 0;
        }

        int deleted = 0;
        for (OrganizationDocument document : profile.getDocuments()) {
            if (StringUtils.hasText(document.getFileUrl())) {
                removeFile(document.getFileUrl(), profile);
                document.setFileUrl(null);
                deleted++;
            }
        }
        return deleted;
    }

    /**
     * Xóa file trên storage. Lỗi hoặc không tìm thấy thì chỉ ghi log rồi vẫn bỏ URL trong DB:
     * mục tiêu F-139 là không còn truy cập được tài liệu, nên để sót URL còn tệ hơn.
     */
    private void removeFile(String url, OrganizationProfile profile) {
        try {
            if (!fileStorageService.deleteByUrl(url, contentTypeOf(url))) {
                log.warn(">>>> Khong tim thay file giay to de xoa: organizationId={}, url={}",
                        profile.getAccountId(), url);
            }
        } catch (RuntimeException e) {
            log.error(">>>> Xoa file giay to that bai: organizationId={}, url={}: {}",
                    profile.getAccountId(), url, e.getMessage());
        }
    }

    /** PDF cũng nằm trong giấy tờ tổ chức; đuôi .pdf quyết định resource_type trên Cloudinary. */
    private static String contentTypeOf(String url) {
        return url.toLowerCase().endsWith(".pdf") ? "application/pdf" : IMAGE_CONTENT_TYPE;
    }
}
