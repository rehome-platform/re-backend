package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.client.DecartVtonClient;
import com.rehome.rehome.component.LiveTryOnQuotaGuard;
import com.rehome.rehome.component.MembershipQuotaResolver;
import com.rehome.rehome.dto.request.CompleteLiveTryOnRequest;
import com.rehome.rehome.dto.request.StartLiveTryOnRequest;
import com.rehome.rehome.dto.response.LiveTryOnHistoryItemResponse;
import com.rehome.rehome.dto.response.LiveTryOnQuotaResponse;
import com.rehome.rehome.dto.response.PageResponse;
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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveTryOnServiceImpl implements LiveTryOnService {

    private static final byte[] MAGIC_WEBM = {(byte) 0x1A, (byte) 0x45, (byte) 0xDF, (byte) 0xA3};
    private static final byte[] MAGIC_MP4_FTYP = "ftyp".getBytes(StandardCharsets.US_ASCII);

    private static final int HISTORY_DEFAULT_SIZE = 20;
    private static final int HISTORY_MAX_SIZE = 50;

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
                .bonusCredits(usage.bonusCredits())
                .upsellTier(quotaGuard.upsellTier(quota))
                .build();
    }

    @Override
    public LiveTryOnSessionResponse startSession(Account account, StartLiveTryOnRequest request) {
        Product product = productRepository.findWithCategoryByIdIn(List.of(request.getProductId()))
                .stream().findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        if (product.getStatus() != ProductStatus.ON_SALE) {
            throw new AppException(ErrorCode.TRYON_PRODUCT_NOT_AVAILABLE);
        }
        String garmentImageUrl = resolveGarmentImageUrl(product);
        String prompt = buildPrompt(product);

        int sessionSeconds = Math.max(
                quotaGuard.usage(account.getId()).quota().liveTryonMaxSeconds(), maxSessionSeconds);

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
                .remainingToday(remainingToday)
                .build();
    }

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
                .orElseThrow(() -> new AppException(ErrorCode.TRYON_PRODUCT_NOT_AVAILABLE));
    }

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

    @Override
    public void confirmFirstFrame(Account account, UUID sessionId) {
        quotaGuard.confirmFirstFrame(account, sessionId);
    }

    @Override
    @Transactional
    public void completeSession(Account account, UUID sessionId, CompleteLiveTryOnRequest request) {
        LiveTryOnSession session = findOwnStartedSession(account, sessionId);
        session.setStatus(request.getStatus());
        session.setActualDurationSeconds(request.getActualDurationSeconds());
        session.setCompletedAt(Instant.now());
        liveTryOnSessionRepository.save(session);
    }

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

    @Override
    @Transactional(readOnly = true)
    public PageResponse<LiveTryOnHistoryItemResponse> getHistory(Account account, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size < 1 ? HISTORY_DEFAULT_SIZE : Math.min(size, HISTORY_MAX_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<LiveTryOnSession> result = liveTryOnSessionRepository
                .findByMember_IdAndStatusAndVideoUrlIsNotNull(account.getId(), LiveTryOnStatus.COMPLETED, pageable);

        List<String> productIds = result.getContent().stream()
                .map(s -> s.getProduct().getId())
                .distinct()
                .toList();
        Map<String, String> thumbnails = productIds.isEmpty() ? Map.of()
                : productPhotoRepository.findByProduct_IdInOrderByDisplayOrderAscIdAsc(productIds).stream()
                .collect(Collectors.toMap(
                        ph -> ph.getProduct().getId(),
                        ProductPhoto::getImageUrl,
                        (first, second) -> first));

        return PageResponse.from(result.map(s -> LiveTryOnHistoryItemResponse.builder()
                .sessionId(s.getId())
                .productId(s.getProduct().getId())
                .productName(s.getProduct().getName())
                .productThumbnailUrl(thumbnails.get(s.getProduct().getId()))
                .productStatus(s.getProduct().getStatus())
                .isMine(s.getProduct().getMember() != null
                        && account.getId().equals(s.getProduct().getMember().getId()))
                .videoUrl(s.getVideoUrl())
                .actualDurationSeconds(s.getActualDurationSeconds())
                .createdAt(s.getCreatedAt())
                .completedAt(s.getCompletedAt())
                .build()));
    }

    @Override
    @Transactional
    public void deleteVideo(Account account, UUID sessionId) {
        LiveTryOnSession session = liveTryOnSessionRepository.findByIdAndMember_Id(sessionId, account.getId())
                .orElseThrow(() -> new AppException(ErrorCode.TRYON_LIVE_SESSION_NOT_FOUND));
        String videoUrl = session.getVideoUrl();
        if (videoUrl == null) {
            return;
        }

        session.setVideoUrl(null);
        liveTryOnSessionRepository.save(session);

        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    removeVideoFile(videoUrl, sessionId);
                }
            });
        } else {
            removeVideoFile(videoUrl, sessionId);
        }
    }

    private void removeVideoFile(String videoUrl, UUID sessionId) {
        String contentType = videoUrl.toLowerCase(Locale.ROOT).endsWith(".mp4") ? "video/mp4" : "video/webm";
        try {
            if (!fileStorageService.deleteByUrl(videoUrl, contentType)) {
                log.warn("Live try-on video file not found: sessionId={}", sessionId);
            }
        } catch (RuntimeException e) {
            log.warn("Could not delete live try-on video file: sessionId={}", sessionId, e);
        }
    }

    private LiveTryOnSession findOwnStartedSession(Account account, UUID sessionId) {
        LiveTryOnSession session = liveTryOnSessionRepository.findByIdAndMember_Id(sessionId, account.getId())
                .orElseThrow(() -> new AppException(ErrorCode.TRYON_LIVE_SESSION_NOT_FOUND));
        if (session.getStatus() != LiveTryOnStatus.STARTED) {
            throw new AppException(ErrorCode.TRYON_LIVE_SESSION_ALREADY_FINISHED);
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
