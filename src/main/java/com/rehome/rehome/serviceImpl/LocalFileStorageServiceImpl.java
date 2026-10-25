package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.service.FileStorageService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Lưu file vào ổ đĩa của server. CHỈ dùng cho dev: khi deploy bằng Docker lên Render,
 * filesystem của container bị xóa mỗi lần redeploy/restart nên file sẽ mất.
 * Khi deploy dùng {@link CloudinaryFileStorageServiceImpl} (app.storage.provider=cloudinary).
 *
 * <p>Là bản mặc định nếu không khai báo {@code app.storage.provider}.
 */
@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageServiceImpl implements FileStorageService {

    private final Path root;
    private final String publicBaseUrl;

    public LocalFileStorageServiceImpl(
            @Value("${app.storage.local-dir:uploads}") String localDir,
            @Value("${app.storage.public-base-url:http://localhost:8080/files}") String publicBaseUrl) {
        this.root = Path.of(localDir).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
    }

    @Override
    public String store(String path, byte[] content, String contentType) {
        Path target = root.resolve(path).normalize();
        // Chặn path traversal (../)
        if (!target.startsWith(root)) {
            throw new AppException(ErrorCode.FILE_UPLOAD_FAILED);
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new AppException(ErrorCode.FILE_UPLOAD_FAILED);
        }
        return publicBaseUrl + "/" + path.replace('\\', '/');
    }

    @Override
    public boolean delete(String path, String contentType) {
        Path target = root.resolve(path).normalize();
        if (!target.startsWith(root)) {
            throw new AppException(ErrorCode.FILE_UPLOAD_FAILED);
        }
        try {
            return Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new AppException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }
}