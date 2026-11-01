package com.rehome.rehome.serviceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * F-139: endpoint xem ảnh giấy tờ đọc byte qua {@code loadByUrl}, nên mỗi provider phải
 * (1) đọc được URL do chính nó sinh ra ở store(), và (2) TỪ CHỐI url lạ — url nằm trong DB,
 * nhận bừa thì endpoint thành bàn đạp SSRF.
 */
class FileStorageLoadByUrlTest {

    private static final byte[] CONTENT = {1, 2, 3, 4};

    private LocalFileStorageServiceImpl localStorage(Path tempDir, String baseUrl) {
        return new LocalFileStorageServiceImpl(tempDir.toString(), baseUrl);
    }

    /** Cloudinary thật chỉ dùng để dựng config cloud_name; phần gọi mạng bị chặn ở fetch(). */
    private static Cloudinary cloudinary() {
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", "demo", "api_key", "k", "api_secret", "s"));
    }

    /** Ghi lại URI đã được chấp nhận thay vì gọi HTTP thật. */
    private static final class RecordingCloudinaryStorage extends CloudinaryFileStorageServiceImpl {
        private final List<URI> fetched = new ArrayList<>();
        private final Optional<byte[]> answer;

        RecordingCloudinaryStorage(Optional<byte[]> answer) {
            super(cloudinary());
            this.answer = answer;
        }

        @Override
        protected Optional<byte[]> fetch(URI uri) {
            fetched.add(uri);
            return answer;
        }
    }

    // ---------------------------------------------------------------- local

    @Test
    void local_loadByUrl_readsStoredFile(@TempDir Path tempDir) {
        LocalFileStorageServiceImpl storage = localStorage(tempDir, "http://localhost:8080/files");

        String url = storage.store("organization-documents/abc.jpg", CONTENT, "image/jpeg");

        assertThat(storage.loadByUrl(url)).contains(CONTENT);
    }

    @Test
    void local_loadByUrl_handlesTrailingSlashInBaseUrl(@TempDir Path tempDir) {
        LocalFileStorageServiceImpl storage = localStorage(tempDir, "http://localhost:8080/files/");

        String url = storage.store("organization-documents/x.jpg", CONTENT, "image/jpeg");

        assertThat(storage.loadByUrl(url)).contains(CONTENT);
    }

    @Test
    void local_loadByUrl_emptyForForeignUrl(@TempDir Path tempDir) {
        LocalFileStorageServiceImpl storage = localStorage(tempDir, "http://localhost:8080/files");

        assertThat(storage.loadByUrl("http://169.254.169.254/latest/meta-data/")).isEmpty();
        assertThat(storage.loadByUrl("https://res.cloudinary.com/demo/image/upload/v1/a.jpg")).isEmpty();
        assertThat(storage.loadByUrl(null)).isEmpty();
        assertThat(storage.loadByUrl("http://localhost:8080/files/")).isEmpty();
    }

    @Test
    void local_loadByUrl_emptyForMissingFile(@TempDir Path tempDir) {
        LocalFileStorageServiceImpl storage = localStorage(tempDir, "http://localhost:8080/files");

        assertThat(storage.loadByUrl("http://localhost:8080/files/organization-documents/khong-co.jpg"))
                .isEmpty();
    }

    @Test
    void local_loadByUrl_emptyForPathTraversalAndDirectory(@TempDir Path tempDir) throws Exception {
        LocalFileStorageServiceImpl storage = localStorage(tempDir, "http://localhost:8080/files");
        Files.createDirectories(tempDir.resolve("organization-documents"));
        Files.write(tempDir.getParent().resolve("ngoai-root.txt"), CONTENT);

        // ../ ra ngoài thư mục uploads
        assertThat(storage.loadByUrl("http://localhost:8080/files/../ngoai-root.txt")).isEmpty();
        // Trỏ vào thư mục chứ không phải file
        assertThat(storage.loadByUrl("http://localhost:8080/files/organization-documents")).isEmpty();
    }

    // ---------------------------------------------------------------- cloudinary

    @Test
    void cloudinary_loadByUrl_fetchesOwnDeliveryUrl() {
        RecordingCloudinaryStorage storage = new RecordingCloudinaryStorage(Optional.of(CONTENT));
        String url = "https://res.cloudinary.com/demo/image/upload/v1699999999/organization-documents/a.jpg";

        assertThat(storage.loadByUrl(url)).contains(CONTENT);
        assertThat(storage.fetched).containsExactly(URI.create(url));
    }

    @Test
    void cloudinary_loadByUrl_emptyWhenFileGone() {
        RecordingCloudinaryStorage storage = new RecordingCloudinaryStorage(Optional.empty());

        assertThat(storage.loadByUrl(
                "https://res.cloudinary.com/demo/raw/upload/v1/organization-documents/a.pdf")).isEmpty();
    }

    @Test
    void cloudinary_loadByUrl_rejectsForeignHostWithoutFetching() {
        RecordingCloudinaryStorage storage = new RecordingCloudinaryStorage(Optional.of(CONTENT));

        // Host lạ, kể cả khi bắt chước đúng dạng đường dẫn của Cloudinary
        assertThat(storage.loadByUrl(
                "https://evil.example.com/demo/image/upload/v1/organization-documents/a.jpg")).isEmpty();
        // Địa chỉ metadata nội bộ - đúng mục tiêu điển hình của SSRF
        assertThat(storage.loadByUrl("http://169.254.169.254/demo/image/upload/v1/a.jpg")).isEmpty();
        // http thay vì https
        assertThat(storage.loadByUrl(
                "http://res.cloudinary.com/demo/image/upload/v1/organization-documents/a.jpg")).isEmpty();
        // Cloud khác trên cùng CDN
        assertThat(storage.loadByUrl(
                "https://res.cloudinary.com/cloud-khac/image/upload/v1/a.jpg")).isEmpty();
        // Không phải URL của storage (thiếu /upload/)
        assertThat(storage.loadByUrl("https://res.cloudinary.com/demo/a.jpg")).isEmpty();
        assertThat(storage.loadByUrl(null)).isEmpty();

        assertThat(storage.fetched).isEmpty();
    }

    @Test
    void cloudinary_loadByUrl_emptyWhenCloudNameNotConfigured() {
        // provider=cloudinary nhưng thiếu env var: không được đoán rồi gọi bừa
        CloudinaryFileStorageServiceImpl storage = new CloudinaryFileStorageServiceImpl(
                new Cloudinary(ObjectUtils.asMap("api_key", "k", "api_secret", "s")));

        assertThat(storage.loadByUrl(
                "https://res.cloudinary.com/demo/image/upload/v1/organization-documents/a.jpg")).isEmpty();
    }

    @Test
    void cloudinary_fetch_wrapsNetworkFailureAsReadFailed() {
        CloudinaryFileStorageServiceImpl storage = new CloudinaryFileStorageServiceImpl(cloudinary());

        // Cổng không ai nghe -> IOException -> 500 FILE_READ_FAILED, không phải 404
        assertThatThrownBy(() -> storage.fetch(URI.create("http://127.0.0.1:1/a.jpg")))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_READ_FAILED);
    }
}
