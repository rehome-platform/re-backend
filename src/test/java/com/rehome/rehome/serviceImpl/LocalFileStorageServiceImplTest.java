package com.rehome.rehome.serviceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageServiceImplTest {

    private static final String BASE_URL = "http://localhost:8080/files";

    @TempDir
    Path tempDir;

    private void assertStoreFails(LocalFileStorageServiceImpl storage, String path) {
        assertThatThrownBy(() -> storage.store(path, "x".getBytes(StandardCharsets.UTF_8), "application/pdf"))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RECEIPT_PDF_GENERATION_FAILED);
    }

    @Test
    void store_writesFileAndReturnsPublicUrl() throws IOException {
        LocalFileStorageServiceImpl storage = new LocalFileStorageServiceImpl(tempDir.toString(), BASE_URL);
        byte[] content = "%PDF-fake".getBytes(StandardCharsets.UTF_8);

        String url = storage.store("receipts/RC-2026-000045.pdf", content, "application/pdf");

        assertThat(url).isEqualTo(BASE_URL + "/receipts/RC-2026-000045.pdf");
        Path written = tempDir.resolve("receipts/RC-2026-000045.pdf");
        assertThat(written).exists();
        assertThat(Files.readAllBytes(written)).isEqualTo(content);
    }

    @Test
    void store_baseUrlWithTrailingSlash_doesNotProduceDoubleSlash() {
        LocalFileStorageServiceImpl storage =
                new LocalFileStorageServiceImpl(tempDir.toString(), BASE_URL + "/");

        String url = storage.store("receipts/a.pdf", new byte[] {1}, "application/pdf");

        assertThat(url).isEqualTo(BASE_URL + "/receipts/a.pdf");
    }

    @Test
    void store_samepath_overwritesExistingFile() throws IOException {
        LocalFileStorageServiceImpl storage = new LocalFileStorageServiceImpl(tempDir.toString(), BASE_URL);

        storage.store("receipts/a.pdf", "old".getBytes(StandardCharsets.UTF_8), "application/pdf");
        storage.store("receipts/a.pdf", "new".getBytes(StandardCharsets.UTF_8), "application/pdf");

        assertThat(Files.readString(tempDir.resolve("receipts/a.pdf"))).isEqualTo("new");
    }

    @Test
    void store_pathTraversal_throwsAndWritesNothingOutsideRoot() {
        Path root = tempDir.resolve("root");
        LocalFileStorageServiceImpl storage = new LocalFileStorageServiceImpl(root.toString(), BASE_URL);

        assertStoreFails(storage, "../evil.pdf");

        assertThat(tempDir.resolve("evil.pdf")).doesNotExist();
        assertThat(root.resolve("../evil.pdf").normalize()).doesNotExist();
    }

    @Test
    void store_ioError_throwsAppException() throws IOException {
        LocalFileStorageServiceImpl storage = new LocalFileStorageServiceImpl(tempDir.toString(), BASE_URL);
        // "receipts" là file thường nên không thể tạo thư mục cùng tên -> IOException
        Files.writeString(tempDir.resolve("receipts"), "i am a file");

        assertStoreFails(storage, "receipts/a.pdf");
    }
}