package com.rehome.rehome.serviceImpl;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F-139: job tự xóa ảnh chỉ còn URL trong DB nên deleteByUrl phải đảo ngược đúng
 * URL mà store() đã sinh ra, ở cả hai provider.
 */
@ExtendWith(MockitoExtension.class)
class FileStorageDeleteByUrlTest {

    @Mock private Cloudinary cloudinary;
    @Mock private Uploader uploader;

    private LocalFileStorageServiceImpl localStorage(Path tempDir, String baseUrl) {
        return new LocalFileStorageServiceImpl(tempDir.toString(), baseUrl);
    }

    private CloudinaryFileStorageServiceImpl cloudinaryStorage() {
        when(cloudinary.uploader()).thenReturn(uploader);
        return new CloudinaryFileStorageServiceImpl(cloudinary);
    }

    // ---------------------------------------------------------------- local

    @Test
    void local_deleteByUrl_removesStoredFile(@TempDir Path tempDir) {
        LocalFileStorageServiceImpl storage = localStorage(tempDir, "http://localhost:8080/files");

        String url = storage.store("organization-documents/abc.jpg", new byte[]{1, 2, 3}, "image/jpeg");

        assertThat(url).isEqualTo("http://localhost:8080/files/organization-documents/abc.jpg");
        assertThat(Files.exists(tempDir.resolve("organization-documents/abc.jpg"))).isTrue();

        assertThat(storage.deleteByUrl(url, "image/jpeg")).isTrue();
        assertThat(Files.exists(tempDir.resolve("organization-documents/abc.jpg"))).isFalse();
    }

    @Test
    void local_deleteByUrl_handlesTrailingSlashInBaseUrl(@TempDir Path tempDir) {
        LocalFileStorageServiceImpl storage = localStorage(tempDir, "http://localhost:8080/files/");

        String url = storage.store("organization-documents/x.jpg", new byte[]{1}, "image/jpeg");

        assertThat(storage.deleteByUrl(url, "image/jpeg")).isTrue();
    }

    @Test
    void local_deleteByUrl_foreignUrl_returnsFalse(@TempDir Path tempDir) {
        LocalFileStorageServiceImpl storage = localStorage(tempDir, "http://localhost:8080/files");

        assertThat(storage.deleteByUrl(
                "https://res.cloudinary.com/demo/image/upload/v1/a.jpg", "image/jpeg")).isFalse();
        assertThat(storage.deleteByUrl(null, "image/jpeg")).isFalse();
    }

    @Test
    void local_deleteByUrl_missingFile_returnsFalse(@TempDir Path tempDir) {
        LocalFileStorageServiceImpl storage = localStorage(tempDir, "http://localhost:8080/files");

        assertThat(storage.deleteByUrl(
                "http://localhost:8080/files/organization-documents/khong-co.jpg", "image/jpeg")).isFalse();
    }

    // ---------------------------------------------------------------- cloudinary

    @Test
    void cloudinary_deleteByUrl_image_stripsVersionAndExtension() throws Exception {
        CloudinaryFileStorageServiceImpl storage = cloudinaryStorage();
        when(uploader.destroy(anyString(), anyMap())).thenReturn(Map.of("result", "ok"));

        boolean deleted = storage.deleteByUrl(
                "https://res.cloudinary.com/demo/image/upload/v1699999999/organization-documents/abc.jpg",
                "image/jpeg");

        assertThat(deleted).isTrue();
        // store() bỏ đuôi khi làm public_id cho ảnh nên xóa cũng phải bỏ đuôi
        verify(uploader).destroy(eq("organization-documents/abc"), anyMap());
    }

    @Test
    void cloudinary_deleteByUrl_rawPdf_keepsExtension() throws Exception {
        CloudinaryFileStorageServiceImpl storage = cloudinaryStorage();
        when(uploader.destroy(anyString(), anyMap())).thenReturn(Map.of("result", "ok"));

        boolean deleted = storage.deleteByUrl(
                "https://res.cloudinary.com/demo/raw/upload/v1699999999/organization-documents/licence.pdf",
                "application/pdf");

        assertThat(deleted).isTrue();
        verify(uploader).destroy(eq("organization-documents/licence.pdf"), anyMap());
    }

    @Test
    void cloudinary_deleteByUrl_withoutVersionSegment_stillParses() throws Exception {
        CloudinaryFileStorageServiceImpl storage = cloudinaryStorage();
        when(uploader.destroy(anyString(), anyMap())).thenReturn(Map.of("result", "ok"));

        storage.deleteByUrl(
                "https://res.cloudinary.com/demo/image/upload/organization-documents/abc.jpg",
                "image/jpeg");

        verify(uploader).destroy(eq("organization-documents/abc"), anyMap());
    }

    @Test
    void cloudinary_deleteByUrl_notFoundOnCloudinary_returnsFalse() throws Exception {
        CloudinaryFileStorageServiceImpl storage = cloudinaryStorage();
        when(uploader.destroy(anyString(), anyMap())).thenReturn(Map.of("result", "not found"));

        assertThat(storage.deleteByUrl(
                "https://res.cloudinary.com/demo/image/upload/v1/organization-documents/abc.jpg",
                "image/jpeg")).isFalse();
    }

    @Test
    void cloudinary_deleteByUrl_notACloudinaryUrl_returnsFalseWithoutCallingApi() {
        CloudinaryFileStorageServiceImpl storage = new CloudinaryFileStorageServiceImpl(cloudinary);

        assertThat(storage.deleteByUrl("http://localhost:8080/files/a.jpg", "image/jpeg")).isFalse();
        assertThat(storage.deleteByUrl(null, "image/jpeg")).isFalse();
    }
}
