package com.rehome.rehome.serviceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CloudinaryFileStorageServiceImplTest {

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    private CloudinaryFileStorageServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CloudinaryFileStorageServiceImpl(cloudinary);
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Map<String, Object>> stubUpload(Map<String, Object> result) throws IOException {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(), any())).thenReturn(result);
        return ArgumentCaptor.forClass(Map.class);
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Map<String, Object>> stubDestroy(Map<String, Object> result) throws IOException {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.destroy(any(), any())).thenReturn(result);
        return ArgumentCaptor.forClass(Map.class);
    }

    @Test
    void store_image_stripsExtensionFromPublicIdAndReturnsSecureUrl() throws IOException {
        ArgumentCaptor<Map<String, Object>> options =
                stubUpload(Map.of("secure_url", "https://res.cloudinary.com/demo/image/upload/products/a.jpg"));

        String url = service.store("products/a.jpg", new byte[] {1}, "image/jpeg");

        assertThat(url).isEqualTo("https://res.cloudinary.com/demo/image/upload/products/a.jpg");

        org.mockito.Mockito.verify(uploader).upload(any(), options.capture());
        assertThat(options.getValue())
                .containsEntry("public_id", "products/a")
                .containsEntry("resource_type", "image")
                .containsEntry("overwrite", true);
    }

    @Test
    void store_pdf_keepsExtensionAndUsesRawResourceType() throws IOException {
        ArgumentCaptor<Map<String, Object>> options =
                stubUpload(Map.of("secure_url", "https://res.cloudinary.com/demo/raw/upload/receipts/RC-1.pdf"));

        service.store("receipts/RC-1.pdf", new byte[] {1}, "application/pdf");

        org.mockito.Mockito.verify(uploader).upload(any(), options.capture());
        assertThat(options.getValue())
                .containsEntry("public_id", "receipts/RC-1.pdf")
                .containsEntry("resource_type", "raw");
    }

    @Test
    void store_pathWithoutExtension_keepsPublicIdUnchanged() throws IOException {
        ArgumentCaptor<Map<String, Object>> options = stubUpload(Map.of("secure_url", "https://cdn/x"));

        service.store("avatars/no-extension", new byte[] {1}, "image/png");

        org.mockito.Mockito.verify(uploader).upload(any(), options.capture());
        assertThat(options.getValue()).containsEntry("public_id", "avatars/no-extension");
    }

    @Test
    void store_dotInFolderNameOnly_doesNotTruncatePath() throws IOException {
        ArgumentCaptor<Map<String, Object>> options = stubUpload(Map.of("secure_url", "https://cdn/x"));

        service.store("v1.2/file", new byte[] {1}, "image/png");

        org.mockito.Mockito.verify(uploader).upload(any(), options.capture());
        assertThat(options.getValue()).containsEntry("public_id", "v1.2/file");
    }

    @Test
    void store_throwsUploadFailed_whenResponseHasNoSecureUrl() throws IOException {
        stubUpload(Map.of("error", "something went wrong"));

        assertThatThrownBy(() -> service.store("products/a.jpg", new byte[] {1}, "image/jpeg"))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FILE_UPLOAD_FAILED);
    }

    @Test
    void delete_image_stripsExtensionAndReturnsTrueOnOk() throws IOException {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.destroy(org.mockito.ArgumentMatchers.eq("avatars/a"), any()))
                .thenReturn(Map.of("result", "ok"));

        assertThat(service.delete("avatars/a.jpg", "image/jpeg")).isTrue();
    }

    @Test
    void delete_pdf_keepsExtensionAndUsesRawResourceType() throws IOException {
        ArgumentCaptor<Map<String, Object>> options = stubDestroy(Map.of("result", "ok"));

        service.delete("organization-documents/a.pdf", "application/pdf");

        org.mockito.Mockito.verify(uploader).destroy(
                org.mockito.ArgumentMatchers.eq("organization-documents/a.pdf"), options.capture());
        assertThat(options.getValue()).containsEntry("resource_type", "raw");
    }

    @Test
    void delete_returnsFalse_whenAssetNotFound() throws IOException {
        stubDestroy(Map.of("result", "not found"));

        assertThat(service.delete("avatars/a.jpg", "image/jpeg")).isFalse();
    }

    @Test
    void delete_throwsDeleteFailed_whenCloudinaryThrows() throws IOException {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.destroy(any(), any())).thenThrow(new IOException("network down"));

        assertThatThrownBy(() -> service.delete("avatars/a.jpg", "image/jpeg"))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FILE_DELETE_FAILED);
    }

    @Test
    void store_throwsUploadFailed_whenCloudinaryThrows() throws IOException {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(), any())).thenThrow(new IOException("network down"));

        assertThatThrownBy(() -> service.store("products/a.jpg", new byte[] {1}, "image/jpeg"))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FILE_UPLOAD_FAILED);
    }
}
