package com.rehome.rehome.serviceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rehome.rehome.dto.response.UploadedFileResponse;
import com.rehome.rehome.entity.enums.UploadFolder;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.service.FileStorageService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class FileUploadServiceImplTest {

    private static final long MAX_SIZE = 1024;
    private static final int MAX_FILES = 3;

    @Mock
    private FileStorageService fileStorageService;

    private FileUploadServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FileUploadServiceImpl(fileStorageService, MAX_SIZE, MAX_FILES);
    }

    // ------------------------------------------------------------------
    // Fixtures: nội dung file phải có magic bytes thật vì service kiểm tra
    // ------------------------------------------------------------------

    private static byte[] withPadding(byte[] magic) {
        byte[] content = new byte[magic.length + 16];
        System.arraycopy(magic, 0, content, 0, magic.length);
        return content;
    }

    private static byte[] jpeg() {
        return withPadding(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
    }

    private static byte[] png() {
        return withPadding(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
    }

    private static byte[] webp() {
        byte[] content = withPadding("RIFF".getBytes(StandardCharsets.US_ASCII));
        byte[] webp = "WEBP".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(webp, 0, content, 8, webp.length);
        return content;
    }

    private static byte[] pdf() {
        return withPadding("%PDF-".getBytes(StandardCharsets.US_ASCII));
    }

    private static MultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("file", name, contentType, content);
    }

    private void assertFails(MultipartFile file, UploadFolder folder, ErrorCode expected) {
        assertThatThrownBy(() -> service.upload(file, folder))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(expected);

        verify(fileStorageService, never()).store(anyString(), any(), anyString());
    }

    // ------------------------------------------------------------------
    // Happy path
    // ------------------------------------------------------------------

    @Test
    void upload_storesFileAndReturnsUrlFromStorage() {
        byte[] content = jpeg();
        when(fileStorageService.store(anyString(), eq(content), eq("image/jpeg")))
                .thenReturn("https://cdn.test/products/abc.jpg");

        UploadedFileResponse response =
                service.upload(file("anh-san-pham.jpg", "image/jpeg", content), UploadFolder.PRODUCTS);

        assertThat(response.getUrl()).isEqualTo("https://cdn.test/products/abc.jpg");
        assertThat(response.getOriginalFileName()).isEqualTo("anh-san-pham.jpg");
        assertThat(response.getContentType()).isEqualTo("image/jpeg");
        assertThat(response.getSizeBytes()).isEqualTo(content.length);
    }

    @Test
    void upload_usesUuidPathUnderFolder_notClientFileName() {
        when(fileStorageService.store(anyString(), any(), anyString())).thenReturn("https://cdn.test/x");

        service.upload(file("../../etc/passwd.png", "image/png", png()), UploadFolder.AVATARS);

        ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
        verify(fileStorageService).store(path.capture(), any(), anyString());

        assertThat(path.getValue())
                .startsWith("avatars/")
                .endsWith(".png")
                .doesNotContain("..")
                .doesNotContain("passwd");
    }

    @Test
    void upload_webpIsAllowed() {
        when(fileStorageService.store(anyString(), any(), anyString())).thenReturn("https://cdn.test/x");

        service.upload(file("a.webp", "image/webp", webp()), UploadFolder.COMMUNITY_POSTS);

        verify(fileStorageService).store(anyString(), any(), eq("image/webp"));
    }

    @Test
    void upload_stripsCharsetFromContentType() {
        when(fileStorageService.store(anyString(), any(), anyString())).thenReturn("https://cdn.test/x");

        service.upload(file("a.jpg", "image/JPEG; charset=UTF-8", jpeg()), UploadFolder.PRODUCTS);

        verify(fileStorageService).store(anyString(), any(), eq("image/jpeg"));
    }

    @Test
    void upload_pdfAllowed_forOrganizationDocuments() {
        when(fileStorageService.store(anyString(), any(), anyString())).thenReturn("https://cdn.test/x");

        service.upload(file("giay-phep.pdf", "application/pdf", pdf()), UploadFolder.ORGANIZATION_DOCUMENTS);

        ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
        verify(fileStorageService).store(path.capture(), any(), eq("application/pdf"));
        assertThat(path.getValue()).startsWith("organization-documents/").endsWith(".pdf");
    }

    // ------------------------------------------------------------------
    // Validate
    // ------------------------------------------------------------------

    @Test
    void upload_throwsFileEmpty_whenFileHasNoContent() {
        assertFails(file("a.jpg", "image/jpeg", new byte[0]), UploadFolder.PRODUCTS, ErrorCode.FILE_EMPTY);
    }

    @Test
    void upload_throwsFileEmpty_whenFileIsNull() {
        assertFails(null, UploadFolder.PRODUCTS, ErrorCode.FILE_EMPTY);
    }

    @Test
    void upload_throwsInvalidUploadFolder_whenFolderIsNull() {
        assertFails(file("a.jpg", "image/jpeg", jpeg()), null, ErrorCode.INVALID_UPLOAD_FOLDER);
    }

    @Test
    void upload_throwsFileTooLarge_whenOverMaxSize() {
        byte[] big = new byte[(int) MAX_SIZE + 1];
        System.arraycopy(jpeg(), 0, big, 0, 3);

        assertFails(file("a.jpg", "image/jpeg", big), UploadFolder.PRODUCTS, ErrorCode.FILE_TOO_LARGE);
    }

    @Test
    void upload_throwsFileTypeNotAllowed_whenContentTypeUnsupported() {
        assertFails(file("a.gif", "image/gif", jpeg()), UploadFolder.PRODUCTS, ErrorCode.FILE_TYPE_NOT_ALLOWED);
    }

    @Test
    void upload_throwsFileTypeNotAllowed_whenContentTypeMissing() {
        assertFails(file("a.jpg", null, jpeg()), UploadFolder.PRODUCTS, ErrorCode.FILE_TYPE_NOT_ALLOWED);
    }

    @Test
    void upload_throwsFileTypeNotAllowed_whenPdfIntoImageOnlyFolder() {
        assertFails(file("a.pdf", "application/pdf", pdf()), UploadFolder.PRODUCTS, ErrorCode.FILE_TYPE_NOT_ALLOWED);
    }

    @Test
    void upload_throwsFileContentMismatch_whenMagicBytesDoNotMatchDeclaredType() {
        // File .exe/.sh đổi tên + khai Content-Type là image/png
        byte[] fake = "MZ not an image at all".getBytes(StandardCharsets.US_ASCII);

        assertFails(file("a.png", "image/png", fake), UploadFolder.PRODUCTS, ErrorCode.FILE_CONTENT_MISMATCH);
    }

    @Test
    void upload_throwsFileContentMismatch_whenFileTooShortForMagicBytes() {
        assertFails(file("a.png", "image/png", new byte[] {(byte) 0x89, 'P'}),
                UploadFolder.PRODUCTS, ErrorCode.FILE_CONTENT_MISMATCH);
    }

    @Test
    void upload_throwsFileContentMismatch_whenRiffIsNotWebp() {
        // RIFF nhưng là WAVE (audio), không phải WEBP
        byte[] wave = withPadding("RIFF".getBytes(StandardCharsets.US_ASCII));
        byte[] marker = "WAVE".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(marker, 0, wave, 8, marker.length);

        assertFails(file("a.webp", "image/webp", wave), UploadFolder.PRODUCTS, ErrorCode.FILE_CONTENT_MISMATCH);
    }

    // ------------------------------------------------------------------
    // uploadAll
    // ------------------------------------------------------------------

    @Test
    void uploadAll_storesEveryFile() {
        when(fileStorageService.store(anyString(), any(), anyString())).thenReturn("https://cdn.test/x");

        List<UploadedFileResponse> uploaded = service.uploadAll(
                List.of(file("a.jpg", "image/jpeg", jpeg()), file("b.png", "image/png", png())),
                UploadFolder.PRODUCTS);

        assertThat(uploaded).hasSize(2);
        verify(fileStorageService, org.mockito.Mockito.times(2)).store(anyString(), any(), anyString());
    }

    @Test
    void uploadAll_throwsFileEmpty_whenListIsEmpty() {
        assertThatThrownBy(() -> service.uploadAll(List.of(), UploadFolder.PRODUCTS))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FILE_EMPTY);

        verify(fileStorageService, never()).store(anyString(), any(), anyString());
    }

    @Test
    void uploadAll_throwsTooManyFiles_whenOverLimit() {
        List<MultipartFile> files = List.of(
                file("a.jpg", "image/jpeg", jpeg()),
                file("b.jpg", "image/jpeg", jpeg()),
                file("c.jpg", "image/jpeg", jpeg()),
                file("d.jpg", "image/jpeg", jpeg()));

        assertThatThrownBy(() -> service.uploadAll(files, UploadFolder.PRODUCTS))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TOO_MANY_FILES);

        verify(fileStorageService, never()).store(anyString(), any(), anyString());
    }

    @Test
    void uploadAll_validatesAllFilesBeforeStoringAny() {
        // File thứ 2 sai -> không được lưu file thứ 1
        List<MultipartFile> files = List.of(
                file("ok.jpg", "image/jpeg", jpeg()),
                file("bad.gif", "image/gif", jpeg()));

        assertThatThrownBy(() -> service.uploadAll(files, UploadFolder.PRODUCTS))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FILE_TYPE_NOT_ALLOWED);

        verify(fileStorageService, never()).store(anyString(), any(), anyString());
    }
}
