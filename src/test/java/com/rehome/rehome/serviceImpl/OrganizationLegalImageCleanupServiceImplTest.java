package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.constant.SystemConfigKeys;
import com.rehome.rehome.entity.auth.OrganizationDocument;
import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.entity.auth.OrganizationRepresentative;
import com.rehome.rehome.entity.enums.VerificationStatus;
import com.rehome.rehome.repository.OrganizationProfileRepository;
import com.rehome.rehome.service.FileStorageService;
import com.rehome.rehome.service.SystemConfigReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationLegalImageCleanupServiceImplTest {

    @Mock private OrganizationProfileRepository organizationProfileRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private SystemConfigReader systemConfigReader;

    @InjectMocks private OrganizationLegalImageCleanupServiceImpl service;

    private void stubRetentionDays(int days) {
        when(systemConfigReader.getInt(SystemConfigKeys.LEGAL_IMAGE_RETENTION_DAYS,
                SystemConfigKeys.DEFAULT_LEGAL_IMAGE_RETENTION_DAYS)).thenReturn(days);
    }

    private OrganizationProfile profileWithImages(UUID id) {
        OrganizationProfile profile = OrganizationProfile.builder()
                .accountId(id)
                .verificationStatus(VerificationStatus.VERIFIED)
                .verifiedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .representative(new OrganizationRepresentative(
                        "Nguyễn Văn B", "Giám đốc", "079123456789",
                        "https://cdn/uploads/organization-documents/front.jpg",
                        "https://cdn/uploads/organization-documents/back.jpg"))
                .build();
        List<OrganizationDocument> documents = new ArrayList<>();
        documents.add(OrganizationDocument.builder()
                .id(UUID.randomUUID())
                .docType("OPERATING_LICENSE")
                .docNumber("142/QD-BNV")
                .fileUrl("https://cdn/uploads/organization-documents/licence.pdf")
                .build());
        profile.setDocuments(documents);
        return profile;
    }

    @Test
    void deleteExpiredLegalImages_deletesFilesAndClearsUrls() {
        UUID orgId = UUID.randomUUID();
        OrganizationProfile profile = profileWithImages(orgId);
        stubRetentionDays(30);
        when(organizationProfileRepository.findAllByVerificationStatusAndVerifiedAtBefore(
                eq(VerificationStatus.VERIFIED), any(Instant.class))).thenReturn(List.of(profile));
        when(fileStorageService.deleteByUrl(anyString(), anyString())).thenReturn(true);

        service.deleteExpiredLegalImages();

        // Ảnh CCCD và file giấy tờ đều bị xóa khỏi storage
        verify(fileStorageService).deleteByUrl(
                "https://cdn/uploads/organization-documents/front.jpg", "image/jpeg");
        verify(fileStorageService).deleteByUrl(
                "https://cdn/uploads/organization-documents/back.jpg", "image/jpeg");
        verify(fileStorageService).deleteByUrl(
                "https://cdn/uploads/organization-documents/licence.pdf", "application/pdf");

        // URL trong DB bị bỏ, nhưng bản ghi đã kiểm thì giữ lại
        assertThat(profile.getRepresentative().getCccdFrontUrl()).isNull();
        assertThat(profile.getRepresentative().getCccdBackUrl()).isNull();
        assertThat(profile.getDocuments().get(0).getFileUrl()).isNull();
        assertThat(profile.getDocuments().get(0).getDocType()).isEqualTo("OPERATING_LICENSE");
        assertThat(profile.getDocuments().get(0).getDocNumber()).isEqualTo("142/QD-BNV");
        assertThat(profile.getRepresentative().getCccdNumber()).isEqualTo("079123456789");

        verify(organizationProfileRepository).save(profile);
    }

    @Test
    void deleteExpiredLegalImages_onlyScansVerifiedProfilesPastRetention() {
        stubRetentionDays(30);
        when(organizationProfileRepository.findAllByVerificationStatusAndVerifiedAtBefore(
                eq(VerificationStatus.VERIFIED), any(Instant.class))).thenReturn(List.of());

        Instant before = Instant.now();
        service.deleteExpiredLegalImages();
        Instant after = Instant.now();

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(organizationProfileRepository).findAllByVerificationStatusAndVerifiedAtBefore(
                eq(VerificationStatus.VERIFIED), captor.capture());
        // Mốc cắt = thời điểm chạy job - 30 ngày; hồ sơ REJECTED không bị quét dù cũng có verifiedAt
        assertThat(captor.getValue())
                .isBetween(before.minus(Duration.ofDays(30)), after.minus(Duration.ofDays(30)));
    }

    @Test
    void deleteExpiredLegalImages_retentionComesFromSystemConfig() {
        stubRetentionDays(7);
        when(organizationProfileRepository.findAllByVerificationStatusAndVerifiedAtBefore(
                eq(VerificationStatus.VERIFIED), any(Instant.class))).thenReturn(List.of());

        Instant before = Instant.now();
        service.deleteExpiredLegalImages();

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(organizationProfileRepository).findAllByVerificationStatusAndVerifiedAtBefore(
                eq(VerificationStatus.VERIFIED), captor.capture());
        assertThat(captor.getValue()).isAfter(before.minus(Duration.ofDays(8)));
    }

    @Test
    void deleteExpiredLegalImages_nothingExpired_savesNothing() {
        stubRetentionDays(30);
        when(organizationProfileRepository.findAllByVerificationStatusAndVerifiedAtBefore(
                eq(VerificationStatus.VERIFIED), any(Instant.class))).thenReturn(List.of());

        service.deleteExpiredLegalImages();

        verifyNoInteractions(fileStorageService);
        verify(organizationProfileRepository, never()).save(any());
    }

    @Test
    void deleteExpiredLegalImages_alreadyCleaned_isNotSavedAgain() {
        UUID orgId = UUID.randomUUID();
        OrganizationProfile profile = OrganizationProfile.builder()
                .accountId(orgId)
                .verificationStatus(VerificationStatus.VERIFIED)
                .verifiedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .representative(new OrganizationRepresentative(
                        "Nguyễn Văn B", "Giám đốc", "079123456789", null, null))
                .build();
        profile.setDocuments(new ArrayList<>());
        stubRetentionDays(30);
        when(organizationProfileRepository.findAllByVerificationStatusAndVerifiedAtBefore(
                eq(VerificationStatus.VERIFIED), any(Instant.class))).thenReturn(List.of(profile));

        service.deleteExpiredLegalImages();

        verifyNoInteractions(fileStorageService);
        verify(organizationProfileRepository, never()).save(any());
    }

    @Test
    void deleteExpiredLegalImages_fileMissingOnStorage_stillClearsUrl() {
        UUID orgId = UUID.randomUUID();
        OrganizationProfile profile = profileWithImages(orgId);
        stubRetentionDays(30);
        when(organizationProfileRepository.findAllByVerificationStatusAndVerifiedAtBefore(
                eq(VerificationStatus.VERIFIED), any(Instant.class))).thenReturn(List.of(profile));
        when(fileStorageService.deleteByUrl(anyString(), anyString())).thenReturn(false);

        service.deleteExpiredLegalImages();

        // Để sót URL còn tệ hơn: vẫn phải bỏ URL trong DB
        assertThat(profile.getRepresentative().getCccdFrontUrl()).isNull();
        assertThat(profile.getDocuments().get(0).getFileUrl()).isNull();
        verify(organizationProfileRepository).save(profile);
    }

    @Test
    void deleteExpiredLegalImages_storageThrows_stillClearsUrl() {
        UUID orgId = UUID.randomUUID();
        OrganizationProfile profile = profileWithImages(orgId);
        stubRetentionDays(30);
        when(organizationProfileRepository.findAllByVerificationStatusAndVerifiedAtBefore(
                eq(VerificationStatus.VERIFIED), any(Instant.class))).thenReturn(List.of(profile));
        when(fileStorageService.deleteByUrl(anyString(), anyString()))
                .thenThrow(new RuntimeException("cloudinary down"));

        service.deleteExpiredLegalImages();

        assertThat(profile.getRepresentative().getCccdFrontUrl()).isNull();
        assertThat(profile.getRepresentative().getCccdBackUrl()).isNull();
        assertThat(profile.getDocuments().get(0).getFileUrl()).isNull();
        verify(organizationProfileRepository).save(profile);
    }
}
