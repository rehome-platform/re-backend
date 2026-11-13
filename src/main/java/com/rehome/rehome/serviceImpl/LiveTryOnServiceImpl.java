package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.client.DecartVtonClient;
import com.rehome.rehome.component.LiveTryOnQuotaGuard;
import com.rehome.rehome.component.MembershipQuotaResolver;
import com.rehome.rehome.dto.request.CompleteLiveTryOnRequest;
import com.rehome.rehome.dto.request.StartLiveTryOnRequest;
import com.rehome.rehome.dto.response.LiveTryOnQuotaResponse;
import com.rehome.rehome.dto.response.LiveTryOnSessionResponse;
import com.rehome.rehome.dto.response.LiveTryOnSessionVideoResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.LiveTryOnStatus;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.TryOnSlot;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.ProductPhoto;
import com.rehome.rehome.entity.tryon.LiveTryOnSession;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.LiveTryOnSessionRepository;
import com.rehome.rehome.repository.ProductPhotoRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.service.FileStorageService;
import com.rehome.rehome.service.LiveTryOnService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * F-xxx: thử đồ AI real-time qua camera (Decart Lucy VTON).
 *
 * <p><b>Hạn mức lấy theo GÓI THÀNH VIÊN</b> ({@link MembershipQuotaResolver}): số lượt/ngày và số
 * giây/lượt đều đọc từ snapshot trên subscription ACTIVE, hoặc từ hạng FREE nếu Member chưa mua gói.
 * {@code tryon.live.daily-limit} không còn được dùng; {@code tryon.live.max-session-seconds} chỉ
 * còn là SÀN kỹ thuật (Decart yêu cầu tối thiểu 10 giây), không phải hạn mức nghiệp vụ.
 *
 * <p>Quota bị trừ ngay lúc mint client token ({@link #startSession}), KHÔNG đợi mobile xác nhận đã
 * kết nối camera thành công - đây là checkpoint server-side duy nhất chặn được chi phí thật trước
 * khi phát sinh (xem {@link DecartVtonClient}). Hệ quả: lỗi mạng ngay sau khi start vẫn mất 1 lượt.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveTryOnServiceImpl implements LiveTryOnService {

    private static final byte[] MAGIC_WEBM = {(byte) 0x1A, (byte) 0x45, (byte) 0xDF, (byte) 0xA3};
    private static final byte[] MAGIC_MP4_FTYP = "ftyp".getBytes(StandardCharsets.US_ASCII);

    private final LiveTryOnSessionRepository liveTryOnSessionRepository;
    private final ProductRepository productRepository;
    private final ProductPhotoRepository productPhotoRepository;
    private final FileStorageService fileStorageService;
    private final DecartVtonClient decartVtonClient;
    private final LiveTryOnQuotaGuard quotaGuard;

    @Value("${decart.vton.model-id:lucy-vton-3.5}")
    private String modelId;

    @Value("${tryon.live.max-session-seconds:10}")
    private int maxSessionSeconds;

    @Value("${tryon.live.token-ttl-seconds:30}")
    private int tokenTtlSeconds;

    @Value("${tryon.live.max-video-size-bytes:8388608}")
    private long maxVideoSizeBytes;

    // ================================================================ GET /tryon/live/quota

    @Override
    @Transactional(readOnly = true)
    public LiveTryOnQuotaResponse getQuota(Account account) {
        LiveTryOnQuotaGuard.Usage usage = quotaGuard.usage(account.getId());
        MembershipQuotaResolver.EffectiveQuota quota = usage.quota();
        return LiveTryOnQuotaResponse.builder()
                .tier(quota.tier())
                .tierName(quota.tierName())
                .limitPerDay(quota.liveTryonPerDay())
                .usedToday(usage.usedToday())
                .remainingToday(usage.remainingToday())
                .limitPerMonth(quota.liveTryonPerMonth())
                .usedThisMonth(usage.usedThisMonth())
                .remainingThisMonth(usage.remainingThisMonth())
                .maxSeconds(quota.liveTryonMaxSeconds())
                .canStart(usage.canStart())
                .upsellTier(quotaGuard.upsellTier(quota))
                .build();
    }

    // ================================================================ POST /tryon/live/sessions

    // Cố ý KHÔNG bọc toàn bộ method trong @Transactional: bước mint token là gọi mạng ra Decart,
    // không được giữ DB connection trong lúc chờ. quotaGuard.reserve() tự mở transaction ngắn riêng.
    @Override
    public LiveTryOnSessionResponse startSession(Account account, StartLiveTryOnRequest request) {
        Product product = productRepository.findWithCategoryByIdIn(List.of(request.getProductId()))
                .stream().findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));                   // 404
        if (product.getStatus() != ProductStatus.ON_SALE) {
            throw new AppException(ErrorCode.TRYON_PRODUCT_NOT_AVAILABLE);                           // 409
        }
        String garmentImageUrl = resolveGarmentImageUrl(product);
        String prompt = buildPrompt(product);

        // Thời lượng mỗi phiên lấy theo GÓI của Member, không còn dùng tryon.live.max-session-seconds.
        // Decart tự cắt phiên phía server họ sau đúng N giây, nên đây là chốt chặn chi phí thật.
        // Kẹp sàn theo cấu hình (Decart yêu cầu tối thiểu 10 giây) để Admin cấu hình sai không
        // sinh ra phiên 1 giây vô dụng hoặc bị Decart từ chối.
        int sessionSeconds = Math.max(
                quotaGuard.usage(account.getId()).quota().liveTryonMaxSeconds(), maxSessionSeconds);

        // Quota thực sự bị trừ bên trong quotaGuard.reserve() (bean riêng để @Transactional có hiệu lực)
        LiveTryOnSession session = quotaGuard.reserve(account, product, modelId);

        DecartVtonClient.TokenResult token;
        try {
            token = decartVtonClient.mintClientToken(modelId, tokenTtlSeconds, sessionSeconds);
        } catch (RuntimeException e) {
            markFailed(session.getId());
            throw e;
        }

        Integer remainingToday = quotaGuard.usage(account.getId()).remainingToday();
        return LiveTryOnSessionResponse.builder()
                .sessionId(session.getId())
                .apiKey(token.apiKey())
                .expiresAt(token.expiresAt())
                .modelId(modelId)
                .garmentImageUrl(garmentImageUrl)
                .prompt(prompt)
                .maxDurationSeconds(sessionSeconds)
                // null = không giới hạn, mobile hiện "Không giới hạn" thay vì con số
                .remainingToday(remainingToday)
                .build();
    }

    // Mint Decart thất bại nhưng quota đã trừ ở quotaGuard.reserve(): đánh dấu FAILED để audit,
    // best-effort - lỗi ở đây chỉ ghi log, không được che mất lỗi TRYON_LIVE_TOKEN_FAILED gốc.
    private void markFailed(UUID sessionId) {
        try {
            liveTryOnSessionRepository.findById(sessionId).ifPresent(s -> {
                s.setStatus(LiveTryOnStatus.FAILED);
                s.setCompletedAt(Instant.now());
                liveTryOnSessionRepository.save(s);
            });
        } catch (RuntimeException e) {
            log.warn(">>>> Khong the danh dau LiveTryOnSession FAILED: id={}: {}", sessionId, e.toString());
        }
    }

    private String resolveGarmentImageUrl(Product product) {
        List<ProductPhoto> photos = productPhotoRepository
                .findByProduct_IdOrderByDisplayOrderAscIdAsc(product.getId());
        return photos.stream()
                .filter(ph -> Boolean.TRUE.equals(ph.getIsBackgroundRemoved()))
                .findFirst()
                .or(() -> photos.stream().findFirst())
                .map(ProductPhoto::getImageUrl)
                .orElseThrow(() -> new AppException(ErrorCode.TRYON_PRODUCT_NOT_AVAILABLE));          // 409: thiếu ảnh
    }

    // Prompt tiếng Anh theo đúng pattern Decart khuyến nghị ("Substitute the current X with...")
    private String buildPrompt(Product product) {
        TryOnSlot slot = product.getCategory() == null ? null : product.getCategory().getTryonSlot();
        String name = product.getName();
        if (slot == TryOnSlot.BOTTOM) {
            return "Substitute the current pants or skirt with the garment shown in the reference image (%s). Keep the person's pose, body shape and background unchanged."
                    .formatted(name);
        }
        if (slot == TryOnSlot.DRESS) {
            return "Substitute the current outfit with the dress shown in the reference image (%s). Keep the person's pose, body shape and background unchanged."
                    .formatted(name);
        }
        return "Substitute the current top with the garment shown in the reference image (%s). Keep the person's pose, body shape and background unchanged."
                .formatted(name);
    }

    // ================================================================ PATCH /tryon/live/sessions/{id}/complete

    @Override
    @Transactional
    public void completeSession(Account account, UUID sessionId, CompleteLiveTryOnRequest request) {
        LiveTryOnSession session = findOwnStartedSession(account, sessionId);
        session.setStatus(request.getStatus());
        session.setActualDurationSeconds(request.getActualDurationSeconds());
        session.setCompletedAt(Instant.now());
        liveTryOnSessionRepository.save(session);
    }

    // ================================================================ POST /tryon/live/sessions/{id}/video

    @Override
    @Transactional
    public LiveTryOnSessionVideoResponse uploadVideo(Account account, UUID sessionId, MultipartFile video) {
        LiveTryOnSession session = findOwnStartedSession(account, sessionId);

        byte[] content = readAndValidateVideo(video);
        String contentType = normalizeContentType(video.getContentType());
        String ext = "video/mp4".equals(contentType) ? "mp4" : "webm";
        String path = "try-on-live/" + sessionId + "." + ext;
        String videoUrl = fileStorageService.store(path, content, contentType);

        session.setVideoUrl(videoUrl);
        session.setStatus(LiveTryOnStatus.COMPLETED);
        session.setCompletedAt(Instant.now());
        liveTryOnSessionRepository.save(session);

        return LiveTryOnSessionVideoResponse.builder().videoUrl(videoUrl).build();
    }

    private LiveTryOnSession findOwnStartedSession(Account account, UUID sessionId) {
        LiveTryOnSession session = liveTryOnSessionRepository.findByIdAndMember_Id(sessionId, account.getId())
                .orElseThrow(() -> new AppException(ErrorCode.TRYON_LIVE_SESSION_NOT_FOUND));        // 404
        if (session.getStatus() != LiveTryOnStatus.STARTED) {
            throw new AppException(ErrorCode.TRYON_LIVE_SESSION_ALREADY_FINISHED);                   // 409
        }
        return session;
    }

    private byte[] readAndValidateVideo(MultipartFile video) {
        if (video == null || video.isEmpty()) {
            throw new AppException(ErrorCode.FILE_EMPTY);
        }
        if (video.getSize() > maxVideoSizeBytes) {
            throw new AppException(ErrorCode.FILE_TOO_LARGE);
        }
        String contentType = normalizeContentType(video.getContentType());
        if (!"video/webm".equals(contentType) && !"video/mp4".equals(contentType)) {
            throw new AppException(ErrorCode.TRYON_LIVE_VIDEO_INVALID);
        }
        byte[] content;
        try {
            content = video.getBytes();
        } catch (IOException e) {
            throw new AppException(ErrorCode.FILE_UPLOAD_FAILED);
        }
        if (!matchesVideoMagicBytes(content, contentType)) {
            throw new AppException(ErrorCode.TRYON_LIVE_VIDEO_INVALID);
        }
        return content;
    }

    private boolean matchesVideoMagicBytes(byte[] content, String contentType) {
        if ("video/webm".equals(contentType)) {
            return startsWith(content, MAGIC_WEBM, 0);
        }
        // mp4: 4 byte độ dài box rồi tới "ftyp" ở offset 4 (bỏ qua giá trị 4 byte đầu)
        return content.length >= 8 && startsWith(content, MAGIC_MP4_FTYP, 4);
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

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int separator = contentType.indexOf(';');
        String base = separator < 0 ? contentType : contentType.substring(0, separator);
        return base.trim().toLowerCase(Locale.ROOT);
    }
}
