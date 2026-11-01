package com.rehome.rehome.serviceImpl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.service.FileStorageService;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Lưu file lên Cloudinary. Dùng cho deploy (Azure Web App) vì mỗi lần deploy là thay sạch
 * wwwroot nên {@link LocalFileStorageServiceImpl} không giữ được file.
 *
 * <p>Bật bằng {@code app.storage.provider=cloudinary}.
 */
@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "cloudinary")
@RequiredArgsConstructor
public class CloudinaryFileStorageServiceImpl implements FileStorageService {

    private static final String DELIVERY_HOST = "res.cloudinary.com";

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

    @Override
    public boolean deleteByUrl(String url, String contentType) {
        String path = pathFromUrl(url);
        if (path == null) {
            return false;
        }
        // delete() đã tự bỏ đuôi cho ảnh và giữ đuôi cho raw, đúng như lúc store()
        return delete(path, contentType);
    }

    @Override
    public Optional<byte[]> loadByUrl(String url) {
        URI uri = ownDeliveryUri(url);
        if (uri == null) {
            return Optional.empty();
        }
        return fetch(uri);
    }

    /**
     * Tải nội dung từ CDN của Cloudinary. Tách thành method riêng để test được phần
     * kiểm URL ở trên mà không cần gọi mạng thật.
     */
    protected Optional<byte[]> fetch(URI uri) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                // Không theo redirect: tránh bị dẫn sang host khác
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response =
                    client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 200) {
                return Optional.of(response.body());
            }
            // File đã bị xóa (job F-139) hoặc không tồn tại
            if (response.statusCode() == 404 || response.statusCode() == 403) {
                return Optional.empty();
            }
            throw new AppException(ErrorCode.FILE_READ_FAILED);
        } catch (IOException e) {
            throw new AppException(ErrorCode.FILE_READ_FAILED);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AppException(ErrorCode.FILE_READ_FAILED);
        }
    }

    /**
     * URL phải đúng là URL phân phối của chính cloud này — https, host res.cloudinary.com,
     * segment đầu là cloud_name, và có "/upload/". Trả null nếu không khớp.
     *
     * <p>Không được nới lỏng: URL đọc từ DB (do người dùng nộp lên), nhận bừa thì
     * {@link #loadByUrl} thành bàn đạp SSRF. Hệ quả: nếu sau này bật CNAME riêng cho
     * Cloudinary thì phải sửa {@link #DELIVERY_HOST} theo.
     */
    private URI ownDeliveryUri(String url) {
        if (url == null || pathFromUrl(url) == null) {
            return null;
        }

        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return null;
        }
        if (!"https".equals(uri.getScheme()) || !DELIVERY_HOST.equals(uri.getHost())) {
            return null;
        }

        String cloudName = cloudinary.config == null ? null : cloudinary.config.cloudName;
        if (!StringUtils.hasText(cloudName)) {
            return null;
        }
        String path = uri.getPath() == null ? "" : uri.getPath();
        return path.startsWith("/" + cloudName + "/") ? uri : null;
    }

    /**
     * URL Cloudinary có dạng .../{image|raw}/upload/v1699999999/organization-documents/uuid.jpg
     * nên phần sau "/upload/" (đã bỏ segment version) chính là path đã truyền cho store().
     * Trả null nếu URL không phải do Cloudinary sinh ra hoặc có transformation lạ.
     */
    private String pathFromUrl(String url) {
        if (url == null) {
            return null;
        }
        int uploadAt = url.indexOf("/upload/");
        if (uploadAt < 0) {
            return null;
        }
        String remainder = url.substring(uploadAt + "/upload/".length());
        int firstSlash = remainder.indexOf('/');
        if (firstSlash > 0) {
            String maybeVersion = remainder.substring(0, firstSlash);
            // Segment version luôn là "v" + số; transformation thì không khớp nên để nguyên
            if (maybeVersion.length() > 1 && maybeVersion.charAt(0) == 'v'
                    && maybeVersion.substring(1).chars().allMatch(Character::isDigit)) {
                remainder = remainder.substring(firstSlash + 1);
            }
        }
        return remainder.isBlank() ? null : remainder;
    }

    private String stripExtension(String path) {
        int dot = path.lastIndexOf('.');
        int slash = path.lastIndexOf('/');
        return (dot > slash && dot > 0) ? path.substring(0, dot) : path;
    }
}
