package com.rehome.rehome.serviceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * F-139 (nửa "restrict"): URL storage của ảnh giấy tờ không ra khỏi server nữa, server tự đọc
 * byte rồi stream. Test khóa hai điều: ai đọc được, và URL thật không bao giờ lọt ra response.
 */
@ExtendWith(MockitoExtension.class)
class OrganizationLegalFileServiceImplTest {

    private static final byte[] BYTES = {1, 2, 3};
    private static final String FRONT_URL =
            "https://res.cloudinary.com/demo/image/upload/v1/organization-documents/front.jpg";
    private static final String BACK_URL =
            "https://res.cloudinary.com/demo/image/upload/v1/organization-documents/back.png";
    private static final String DOC_URL =
            "https://res.cloudinary.com/demo/raw/upload/v1/organization-documents/licence.pdf";

    @Mock private OrganizationProfileRepository organizationProfileRepository;
    @Mock private FileStorageService fileStorageService;

    @InjectMocks private OrganizationLegalFileServiceImpl service;

    private Account account(UUID id, Role role) {
        return Account.builder().id(id).role(role).build();
    }

    private OrganizationProfile profile(UUID orgId, UUID documentId) {
        OrganizationProfile profile = OrganizationProfile.builder()
                .accountId(orgId)
                .representative(new OrganizationRepresentative(
                        "Nguyễn Văn B", "Giám đốc", "079123456789", FRONT_URL, BACK_URL))
                .build();
        List<OrganizationDocument> documents = new ArrayList<>();
        if (documentId != null) {
            documents.add(OrganizationDocument.builder()
                    .id(documentId)
                    .docType("FUND_LICENSE")
                    .fileUrl(DOC_URL)
                    .build());
        }
        profile.setDocuments(documents);
        return profile;
    }

    // ---------------------------------------------------------------- ai đọc được

    @Test
    void download_moderatorReadsCccdFront() {
        UUID orgId = UUID.randomUUID();
        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.of(profile(orgId, null)));
        when(fileStorageService.loadByUrl(FRONT_URL)).thenReturn(Optional.of(BYTES));

        LegalFileDownload download = service.download(
                account(UUID.randomUUID(), Role.MODERATOR), orgId, LegalFileKind.CCCD_FRONT, null);

        assertThat(download.content()).isEqualTo(BYTES);
        assertThat(download.contentType()).isEqualTo("image/jpeg");
        assertThat(download.fileName()).isEqualTo("cccd-front.jpg");
    }

    @Test
    void download_organizationReadsItsOwnFile() {
        UUID orgId = UUID.randomUUID();
        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.of(profile(orgId, null)));
        when(fileStorageService.loadByUrl(BACK_URL)).thenReturn(Optional.of(BYTES));

        LegalFileDownload download = service.download(
                account(orgId, Role.ORGANIZATION), orgId, LegalFileKind.CCCD_BACK, null);

        assertThat(download.content()).isEqualTo(BYTES);
        // Đuôi file quyết định Content-Type trả về
        assertThat(download.contentType()).isEqualTo("image/png");
        assertThat(download.fileName()).isEqualTo("cccd-back.png");
    }

    @Test
    void download_throwsForbidden_whenAnotherOrganizationAsks() {
        UUID orgId = UUID.randomUUID();

        assertThatThrownBy(() -> service.download(
                account(UUID.randomUUID(), Role.ORGANIZATION), orgId, LegalFileKind.CCCD_FRONT, null))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LEGAL_FILE_FORBIDDEN);

        // Chặn trước khi query: không để lộ cả việc hồ sơ có tồn tại hay không
        verifyNoInteractions(organizationProfileRepository);
        verifyNoInteractions(fileStorageService);
    }

    @Test
    void download_throwsForbidden_forMemberAndAdmin() {
        UUID orgId = UUID.randomUUID();

        for (Role role : new Role[]{Role.MEMBER, Role.ADMIN}) {
            // Admin cũng không xem được, đúng như API 128 chỉ mở cho MODERATOR
            assertThatThrownBy(() -> service.download(
                    account(UUID.randomUUID(), role), orgId, LegalFileKind.CCCD_FRONT, null))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LEGAL_FILE_FORBIDDEN);
        }
    }

    // ---------------------------------------------------------------- giấy tờ trong documents

    @Test
    void download_readsDocumentById() {
        UUID orgId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        when(organizationProfileRepository.findById(orgId))
                .thenReturn(Optional.of(profile(orgId, documentId)));
        when(fileStorageService.loadByUrl(DOC_URL)).thenReturn(Optional.of(BYTES));

        LegalFileDownload download = service.download(
                account(UUID.randomUUID(), Role.MODERATOR), orgId, LegalFileKind.DOCUMENT, documentId);

        assertThat(download.content()).isEqualTo(BYTES);
        assertThat(download.contentType()).isEqualTo("application/pdf");
        assertThat(download.fileName()).isEqualTo("giay-to-" + documentId + ".pdf");
    }

    @Test
    void download_throwsNotFound_whenDocumentBelongsToAnotherOrganization() {
        UUID orgId = UUID.randomUUID();
        when(organizationProfileRepository.findById(orgId))
                .thenReturn(Optional.of(profile(orgId, UUID.randomUUID())));

        // documentId của tổ chức khác: tra trong documents của hồ sơ này nên không thấy
        assertThatThrownBy(() -> service.download(account(UUID.randomUUID(), Role.MODERATOR),
                orgId, LegalFileKind.DOCUMENT, UUID.randomUUID()))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LEGAL_FILE_NOT_FOUND);

        verify(fileStorageService, never()).loadByUrl(anyString());
    }

    @Test
    void download_throwsNotFound_whenDocumentIdMissing() {
        UUID orgId = UUID.randomUUID();
        when(organizationProfileRepository.findById(orgId))
                .thenReturn(Optional.of(profile(orgId, UUID.randomUUID())));

        assertThatThrownBy(() -> service.download(
                account(UUID.randomUUID(), Role.MODERATOR), orgId, LegalFileKind.DOCUMENT, null))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LEGAL_FILE_NOT_FOUND);
    }

    // ---------------------------------------------------------------- không còn tệp

    @Test
    void download_throwsNotFound_whenProfileMissing() {
        UUID orgId = UUID.randomUUID();
        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.download(
                account(UUID.randomUUID(), Role.MODERATOR), orgId, LegalFileKind.CCCD_FRONT, null))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LEGAL_FILE_NOT_FOUND);
    }

    @Test
    void download_throwsNotFound_whenImageAlreadyDeletedByRetentionJob() {
        UUID orgId = UUID.randomUUID();
        OrganizationProfile profile = OrganizationProfile.builder()
                .accountId(orgId)
                // Job F-139 đã xóa ảnh và set URL về null
                .representative(new OrganizationRepresentative(
                        "Nguyễn Văn B", "Giám đốc", "079123456789", null, null))
                .build();
        profile.setDocuments(new ArrayList<>());
        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.download(
                account(UUID.randomUUID(), Role.MODERATOR), orgId, LegalFileKind.CCCD_FRONT, null))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LEGAL_FILE_NOT_FOUND);

        verify(fileStorageService, never()).loadByUrl(anyString());
    }

    @Test
    void download_throwsNotFound_whenRepresentativeMissing() {
        UUID orgId = UUID.randomUUID();
        OrganizationProfile profile = OrganizationProfile.builder().accountId(orgId).build();
        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.download(
                account(UUID.randomUUID(), Role.MODERATOR), orgId, LegalFileKind.CCCD_BACK, null))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LEGAL_FILE_NOT_FOUND);
    }

    @Test
    void download_throwsNotFound_whenFileGoneFromStorage() {
        UUID orgId = UUID.randomUUID();
        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.of(profile(orgId, null)));
        // URL còn trong DB nhưng file đã mất trên storage (hoặc URL không thuộc storage này)
        when(fileStorageService.loadByUrl(FRONT_URL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.download(
                account(UUID.randomUUID(), Role.MODERATOR), orgId, LegalFileKind.CCCD_FRONT, null))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LEGAL_FILE_NOT_FOUND);
    }

    @Test
    void download_doesNotSwallowStorageFailure() {
        UUID orgId = UUID.randomUUID();
        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.of(profile(orgId, null)));
        when(fileStorageService.loadByUrl(FRONT_URL))
                .thenThrow(new AppException(ErrorCode.FILE_READ_FAILED));

        // Storage lỗi là 500, không được biến thành 404 "không có giấy tờ"
        assertThatThrownBy(() -> service.download(
                account(UUID.randomUUID(), Role.MODERATOR), orgId, LegalFileKind.CCCD_FRONT, null))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_READ_FAILED);
    }

    @Test
    void download_namesFileWithoutExtension_whenUrlHasNone() {
        UUID orgId = UUID.randomUUID();
        OrganizationProfile profile = OrganizationProfile.builder()
                .accountId(orgId)
                .representative(new OrganizationRepresentative(
                        "Nguyễn Văn B", "Giám đốc", "079123456789", "https://cdn/no-ext", null))
                .build();
        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.of(profile));
        when(fileStorageService.loadByUrl("https://cdn/no-ext")).thenReturn(Optional.of(BYTES));

        LegalFileDownload download = service.download(
                account(orgId, Role.ORGANIZATION), orgId, LegalFileKind.CCCD_FRONT, null);

        assertThat(download.fileName()).isEqualTo("cccd-front");
        assertThat(download.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void download_neverReturnsStorageUrl() {
        UUID orgId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        when(organizationProfileRepository.findById(orgId))
                .thenReturn(Optional.of(profile(orgId, documentId)));
        when(fileStorageService.loadByUrl(any())).thenReturn(Optional.of(BYTES));

        LegalFileDownload download = service.download(
                account(UUID.randomUUID(), Role.MODERATOR), orgId, LegalFileKind.DOCUMENT, documentId);

        // Cả record trả về không chứa URL nào: client chỉ nhận byte
        assertThat(download.fileName()).doesNotContain("http");
        assertThat(download.contentType()).doesNotContain("http");
    }
}
