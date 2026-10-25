package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.response.UploadedFileResponse;
import com.rehome.rehome.entity.enums.UploadFolder;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.service.FileStorageService;
import com.rehome.rehome.service.FileUploadService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileUploadServiceImpl implements FileUploadService {

    private static final String PDF = "application/pdf";

    /** MIME type được phép -> phần mở rộng dùng khi lưu */
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            PDF, "pdf");

    private static final byte[] MAGIC_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] MAGIC_PNG =
            {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] MAGIC_PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final FileStorageService fileStorageService;
    private final long maxFileSizeBytes;
    private final int maxFilesPerRequest;

    public FileUploadServiceImpl(
            FileStorageService fileStorageService,
            @Value("${app.storage.max-file-size-bytes:10485760}") long maxFileSizeBytes,
            @Value("${app.storage.max-files-per-request:10}") int maxFilesPerRequest) {
        this.fileStorageService = fileStorageService;
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.maxFilesPerRequest = maxFilesPerRequest;
    }

    @Override
    public UploadedFileResponse upload(MultipartFile file, UploadFolder folder) {
        if (folder == null) {
            throw new AppException(ErrorCode.INVALID_UPLOAD_FOLDER);
        }
        return store(file, folder, read(file, folder));
    }

    @Override
    public List<UploadedFileResponse> uploadAll(List<MultipartFile> files, UploadFolder folder) {
        if (folder == null) {
            throw new AppException(ErrorCode.INVALID_UPLOAD_FOLDER);
        }
        if (files == null || files.isEmpty()) {
            throw new AppException(ErrorCode.FILE_EMPTY);
        }
        if (files.size() > maxFilesPerRequest) {
            throw new AppException(ErrorCode.TOO_MANY_FILES);
        }

        // Validate + đọc hết vào RAM trước khi upload, để không lưu nửa vời rồi mới báo lỗi
        List<byte[]> contents = new ArrayList<>(files.size());
        for (MultipartFile file : files) {
            contents.add(read(file, folder));
        }

        List<UploadedFileResponse> uploaded = new ArrayList<>(files.size());
        for (int i = 0; i < files.size(); i++) {
            uploaded.add(store(files.get(i), folder, contents.get(i)));
        }
        return uploaded;
    }

    // ------------------------------------------------------------------
    // Validate
    // ------------------------------------------------------------------

    /** Kiểm tra toàn bộ điều kiện rồi trả về nội dung file. */
    private byte[] read(MultipartFile file, UploadFolder folder) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.FILE_EMPTY);
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new AppException(ErrorCode.FILE_TOO_LARGE);
        }

        String contentType = normalizeContentType(file.getContentType());
        if (!ALLOWED_TYPES.containsKey(contentType)) {
            throw new AppException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
        if (PDF.equals(contentType) && !folder.isPdfAllowed()) {
            throw new AppException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new AppException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        // Content-Type do client khai báo nên không tin được - đối chiếu với magic bytes
        if (!matchesMagicBytes(content, contentType)) {
            throw new AppException(ErrorCode.FILE_CONTENT_MISMATCH);
        }
        return content;
    }

    private UploadedFileResponse store(MultipartFile file, UploadFolder folder, byte[] content) {
        String contentType = normalizeContentType(file.getContentType());
        // Tên file do client đặt không dùng làm path (tránh path traversal và trùng tên) -> UUID
        String path = folder.getPath() + "/" + UUID.randomUUID() + "." + ALLOWED_TYPES.get(contentType);
        String url = fileStorageService.store(path, content, contentType);

        return UploadedFileResponse.builder()
                .url(url)
                .originalFileName(file.getOriginalFilename())
                .contentType(contentType)
                .sizeBytes(content.length)
                .build();
    }

    /** Bỏ phần "; charset=..." và chuẩn hóa về chữ thường. */
    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int separator = contentType.indexOf(';');
        String base = separator < 0 ? contentType : contentType.substring(0, separator);
        return base.trim().toLowerCase();
    }

    private boolean matchesMagicBytes(byte[] content, String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> startsWith(content, MAGIC_JPEG, 0);
            case "image/png" -> startsWith(content, MAGIC_PNG, 0);
            // WebP = container RIFF: "RIFF" + 4 byte độ dài + "WEBP"
            case "image/webp" -> startsWith(content, "RIFF".getBytes(StandardCharsets.US_ASCII), 0)
                    && startsWith(content, "WEBP".getBytes(StandardCharsets.US_ASCII), 8);
            case PDF -> startsWith(content, MAGIC_PDF, 0);
            default -> false;
        };
    }

    private boolean startsWith(byte[] content, byte[] magic, int offset) {
        if (content.length < offset + magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (content[offset + i] != magic[i]) {
                return false;
            }
        }
        return true;
    }
}
