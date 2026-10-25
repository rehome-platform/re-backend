package com.rehome.rehome.serviceImpl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.service.FileStorageService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Lưu file lên Cloudinary. Dùng cho deploy (Render) vì filesystem của container
 * bị xóa mỗi lần redeploy nên {@link LocalFileStorageServiceImpl} không giữ được file.
 *
 * <p>Bật bằng {@code app.storage.provider=cloudinary}.
 */
@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "cloudinary")
@RequiredArgsConstructor
public class CloudinaryFileStorageServiceImpl implements FileStorageService {

    private final Cloudinary cloudinary;

    @Override
    public String store(String path, byte[] content, String contentType) {
        boolean image = contentType != null && contentType.startsWith("image/");

        // Với ảnh, Cloudinary tự nối phần mở rộng theo format nên public_id phải bỏ đuôi,
        // nếu không URL thành "...jpg.jpg". Với "raw" (PDF) thì giữ nguyên đuôi
        // để link tải về vẫn là .pdf.
        String resourceType = image ? "image" : "raw";
        String publicId = image ? stripExtension(path) : path;

        try {
            Map<?, ?> result = cloudinary.uploader().upload(content, ObjectUtils.asMap(
                    "public_id", publicId,
                    "resource_type", resourceType,
                    // Ghi đè khi gọi lại cùng path (vd. regenerate PDF phiếu ký gửi)
                    "overwrite", true,
                    "invalidate", true));

            Object secureUrl = result.get("secure_url");
            if (secureUrl == null) {
                throw new AppException(ErrorCode.FILE_UPLOAD_FAILED);
            }
            return secureUrl.toString();
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    @Override
    public boolean delete(String path, String contentType) {
        boolean image = contentType != null && contentType.startsWith("image/");

        try {
            Map<?, ?> result = cloudinary.uploader().destroy(
                    image ? stripExtension(path) : path,
                    ObjectUtils.asMap(
                            "resource_type", image ? "image" : "raw",
                            "invalidate", true));

            // destroy trả {"result":"ok"} hoặc {"result":"not found"}
            return "ok".equals(String.valueOf(result.get("result")));
        } catch (Exception e) {
            throw new AppException(ErrorCode.FILE_DELETE_FAILED);
        }
    }

    private String stripExtension(String path) {
        int dot = path.lastIndexOf('.');
        int slash = path.lastIndexOf('/');
        return (dot > slash && dot > 0) ? path.substring(0, dot) : path;
    }
}
