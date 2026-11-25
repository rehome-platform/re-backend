package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.component.AiTryOnQuotaGuard;
import com.rehome.rehome.dto.request.CreateTryOnImageRequest;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.TryOnImageResponse;
import com.rehome.rehome.dto.response.TryOnSuggestionResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.TryOnModelType;
import com.rehome.rehome.entity.enums.TryOnSlot;
import com.rehome.rehome.entity.enums.UploadFolder;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.ProductPhoto;
import com.rehome.rehome.entity.tryon.TryOnImage;
import com.rehome.rehome.entity.tryon.TryOnSharedAsset;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.ProductPhotoRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.repository.TryOnImageRepository;
import com.rehome.rehome.repository.TryOnSharedAssetRepository;
import com.rehome.rehome.service.FileStorageService;
import com.rehome.rehome.service.TryOnImageGenerator;
import com.rehome.rehome.service.TryOnService;
import com.rehome.rehome.util.OutfitMatchingRules;
import com.rehome.rehome.util.TryOnFingerprint;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.Comparator;
import org.springframework.data.domain.Slice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class TryOnServiceImpl implements TryOnService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    // API 96: số món gợi ý tối đa
    private static final int SUGGESTION_LIMIT = 10;
    // Không biết kho của món đang xem thì dùng UUID này: không khớp kho nào nên không ưu tiên kho
    private static final UUID NO_PREFERRED_WAREHOUSE = new UUID(0L, 0L);
    // Chỉ cần biết là ảnh để storage chọn đúng resource_type khi xóa
    private static final String IMAGE_CONTENT_TYPE = "image/png";

    private final TryOnImageRepository tryOnImageRepository;
    private final TryOnSharedAssetRepository sharedAssetRepository;
    // Bounded striped locks prevent duplicate GPU work for the same input in this JVM.
    private final ReentrantLock[] assetLocks =
            java.util.stream.IntStream.range(0, 256)
                    .mapToObj(i -> new ReentrantLock()).toArray(ReentrantLock[]::new);
    private final ProductPhotoRepository productPhotoRepository;
    private final FileStorageService fileStorageService;
    private final ProductRepository productRepository;
    private final TryOnImageGenerator tryOnImageGenerator;
    private final AiTryOnQuotaGuard aiTryOnQuotaGuard;
    private final com.rehome.rehome.client.GeminiOutfitClient geminiOutfitClient;
    @org.springframework.beans.factory.annotation.Value("${tryon.generation-version:v1}")
    private String generationVersion = "v1";

    private final Set<UUID> generatingMembers = ConcurrentHashMap.newKeySet();

    // ================================================================ API 97: F-077, F-078, F-079

    // Cố ý KHÔNG @Transactional: bước sinh ảnh chạy vài chục giây, không được giữ DB connection trong lúc chờ AI.
    // Các repository tự mở transaction ngắn cho từng lệnh; category đã được nạp sẵn bằng EntityGraph.
    @Override
    public TryOnImageResponse createImage(Account account, CreateTryOnImageRequest request) {
        UUID memberId = account.getId();
        if (!generatingMembers.add(memberId)) {
            throw new AppException(ErrorCode.TRYON_IN_PROGRESS);
        }
        try {
            return generateImage(account, request);
        } finally {
            generatingMembers.remove(memberId);
        }
    }

    private TryOnImageResponse generateImage(Account account, CreateTryOnImageRequest request) {
        // Hạn mức phối đồ AI theo gói, tính theo NGÀY (reset 00:00 giờ VN). Kiểm TRƯỚC khi gửi
        // request sang AI server để chặn chi phí thật trước khi phát sinh.
        // Lượt được tính bằng chính row try_on_images lưu sau khi sinh thành công, nên sinh ảnh lỗi
        // (502) KHÔNG mất lượt - khác LiveTryOnQuotaGuard vốn trừ ngay lúc mint token.
        // Xóa ảnh khỏi bộ sưu tập cũng KHÔNG hoàn lượt (đặc tả §8.8).
        // true = quota gói đã hết, lượt này trả bằng lượt thưởng (mua bằng điểm), trừ sau khi lưu ảnh xong.

        String topId = blankToNull(request.getTopProductId());
        String bottomId = blankToNull(request.getBottomProductId());
        String dressId = blankToNull(request.getDressProductId());

        // F-077: đúng 1 bộ, hoặc áo + quần, hoặc đầm
        boolean outfit = topId != null && bottomId != null && dressId == null;
        boolean dressOnly = dressId != null && topId == null && bottomId == null;
        if (!outfit && !dressOnly) {
            throw new AppException(ErrorCode.INCOMPLETE_OUTFIT);                              // 409
        }
        // AI chỉ có người mẫu nữ cho đầm
        if (dressOnly && request.getModelType() == TryOnModelType.MALE) {
            throw new AppException(ErrorCode.DRESS_NOT_SUPPORTED_FOR_MALE);                   // 400
        }

        // Load sản phẩm + kiểm slot + còn bán
        List<String> ids = new ArrayList<>(new LinkedHashSet<>(
                Stream.of(topId, bottomId, dressId).filter(Objects::nonNull).toList()));
        Map<String, Product> products = productRepository.findWithCategoryByIdIn(ids).stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));

        Product top = outfit ? requireUsable(products, topId, TryOnSlot.TOP) : null;
        Product bottom = outfit ? requireUsable(products, bottomId, TryOnSlot.BOTTOM) : null;
        Product dress = dressOnly ? requireUsable(products, dressId, TryOnSlot.DRESS) : null;

        // Ảnh của từng món: ưu tiên ảnh đã tách nền (F-120), không có thì ảnh đầu tiên
        List<ProductPhoto> photos = productPhotoRepository.findByProduct_IdInOrderByDisplayOrderAscIdAsc(ids);

        // Hash the actual input bytes: overwriting a photo at the same URL also invalidates reuse.
        byte[] first = loadPhotoBytes(photos, outfit ? top : dress);
        byte[] second = outfit ? loadPhotoBytes(photos, bottom) : new byte[0];
        String fingerprint = TryOnFingerprint.of(
                request.getModelType(), topId, bottomId, dressId, first, second, generationVersion);
        Map<String, String> thumbnails = photos.stream().collect(Collectors.toMap(
                ph -> ph.getProduct().getId(), ProductPhoto::getImageUrl, (left, right) -> left));
        var lock = assetLocks[Math.floorMod(fingerprint.hashCode(), assetLocks.length)];
        lock.lock();
        try {
            var cached = sharedAssetRepository.findById(fingerprint);
            if (cached.isPresent()) {
                return attachSharedImage(account, request, top, bottom, dress, cached.get(), thumbnails, false);
            }
            boolean viaBonusCredit = aiTryOnQuotaGuard.assertCanGenerate(account.getId());
            byte[] png = outfit
                    ? tryOnImageGenerator.generateOutfit(request.getModelType(), first, second)
                    : tryOnImageGenerator.generateDress(request.getModelType(), first);
            String path = UploadFolder.TRY_ON.getPath() + "/" + UUID.randomUUID() + ".png";
            String imageUrl = fileStorageService.store(path, png, IMAGE_CONTENT_TYPE);
            TryOnSharedAsset asset;
            boolean generated = true;
            try {
                asset = sharedAssetRepository.saveAndFlush(
                        TryOnSharedAsset.builder()
                                .fingerprint(fingerprint).imageUrl(imageUrl).build());
            } catch (DataIntegrityViolationException ex) {
                // Another replica may have published first. Repository transaction has already rolled back.
                var winner = sharedAssetRepository.findById(fingerprint);
                if (winner.isEmpty()) {
                    removeFile(imageUrl, null);
                    throw ex;
                }
                asset = winner.get();
                generated = false;
                if (!Objects.equals(imageUrl, asset.getImageUrl())) removeFile(imageUrl, null);
            } catch (RuntimeException ex) {
                removeFile(imageUrl, null);
                throw ex;
            }
            // Once published, the shared asset owns the file, even if saving a collection item fails.
            TryOnImageResponse response = attachSharedImage(account, request, top, bottom, dress,
                    asset, thumbnails, generated);
            if (generated && viaBonusCredit) aiTryOnQuotaGuard.consumeBonusCredit(account.getId());
            return response;
        } finally {
            lock.unlock();
        }
    }

    private TryOnImageResponse attachSharedImage(Account account, CreateTryOnImageRequest request,
            Product top, Product bottom, Product dress,
            TryOnSharedAsset asset, Map<String, String> thumbnails,
            boolean generated) {
        // Each member gets their own collection row and ID; repeated use does not duplicate that row.
        var existing = tryOnImageRepository
                .findFirstByMember_IdAndGenerationFingerprintAndDeletedAtIsNullOrderByCreatedAtDesc(
                        account.getId(), asset.getFingerprint());
        TryOnImage image = existing.orElseGet(() -> tryOnImageRepository.save(TryOnImage.builder()
                .member(account).modelType(request.getModelType()).topProduct(top).bottomProduct(bottom)
                .dressProduct(dress).generationFingerprint(asset.getFingerprint()).sharedAsset(asset)
                .quotaCharged(generated).imageUrl(asset.getImageUrl()).build()));
        TryOnImageResponse response = toResponse(image, thumbnails);
        response.setReused(!generated);
        return response;
    }

    private Product requireUsable(Map<String, Product> products, String productId, TryOnSlot expectedSlot) {
        Product product = products.get(productId);
        if (product == null) {
            throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);                              // 404
        }
        if (product.getCategory() == null || product.getCategory().getTryonSlot() != expectedSlot) {
            throw new AppException(ErrorCode.TRYON_SLOT_MISMATCH);                            // 400
        }
        if (product.getStatus() != ProductStatus.ON_SALE) {
            throw new AppException(ErrorCode.TRYON_PRODUCT_NOT_AVAILABLE);                    // 409
        }
        return product;
    }

    private byte[] loadPhotoBytes(List<ProductPhoto> photos, Product product) {
        List<ProductPhoto> own = photos.stream()
                .filter(ph -> ph.getProduct().getId().equals(product.getId()))
                .toList();
        ProductPhoto chosen = own.stream()
                .filter(ph -> Boolean.TRUE.equals(ph.getIsBackgroundRemoved()))
                .findFirst()
                .or(() -> own.stream().findFirst())
                .orElseThrow(() -> new AppException(ErrorCode.TRYON_PRODUCT_NOT_AVAILABLE));  // thiếu ảnh
        return fileStorageService.loadByUrl(chosen.getImageUrl())
                .orElseThrow(() -> new AppException(ErrorCode.TRYON_PRODUCT_NOT_AVAILABLE));  // file ảnh không còn
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // ================================================================ F-080

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TryOnImageResponse> getCollection(Account account, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<TryOnImage> result = tryOnImageRepository.findByMember_Id(account.getId(), pageable);

        // Ảnh đại diện của mọi món trong trang lấy bằng một query, tránh N+1
        Map<String, String> thumbnails = loadThumbnails(result.getContent());

        return PageResponse.from(result.map(i -> toResponse(i, thumbnails)));
    }

    // ================================================================ GET /tryon/images/{id}

    @Override
    @Transactional(readOnly = true)
    public TryOnImageResponse getImage(Account account, UUID imageId) {
        TryOnImage image = tryOnImageRepository.findWithProductsById(imageId)
                .orElseThrow(() -> new AppException(ErrorCode.TRYON_IMAGE_NOT_FOUND));      // 404

        // Giống deleteImage: ảnh của người khác cũng trả 404, không lộ ảnh có tồn tại hay không
        if (image.getDeletedAt() != null || !image.getMember().getId().equals(account.getId())) {
            throw new AppException(ErrorCode.TRYON_IMAGE_NOT_FOUND);                        // 404
        }
        return toResponse(image, loadThumbnails(List.of(image)));
    }

    // ================================================================ API 96: F-075

    @Override
    @Transactional(readOnly = true)
    public List<TryOnSuggestionResponse> getSuggestions(String slot, String excludeProductId) {
        TryOnSlot target = parseSuggestionSlot(slot);

        String excludeId = blankToNull(excludeProductId);
        UUID preferredWarehouse = excludeId == null ? NO_PREFERRED_WAREHOUSE
                : productRepository.findWarehouseIdById(excludeId).orElse(NO_PREFERRED_WAREHOUSE);

        List<Product> products = productRepository.findTryOnSuggestions(target,
                excludeId == null ? "" : excludeId,
                preferredWarehouse,
                PageRequest.of(0, SUGGESTION_LIMIT));
        return mapSuggestions(products);
    }

    private List<TryOnSuggestionResponse> mapSuggestions(List<Product> products) {
        if (products.isEmpty()) {
            return List.of();
        }

        // Ảnh đại diện (ảnh đầu tiên) của mọi món gợi ý, lấy bằng một query
        List<String> productIds = products.stream().map(Product::getId).toList();
        Map<String, String> thumbnails = productPhotoRepository
                .findByProduct_IdInOrderByDisplayOrderAscIdAsc(productIds).stream()
                .collect(Collectors.toMap(
                        ph -> ph.getProduct().getId(),
                        ProductPhoto::getImageUrl,
                        (first, second) -> first));

        return products.stream()
                .map(p -> toSuggestion(p, thumbnails.get(p.getId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TryOnSuggestionResponse> getOutfitSuggestions(
            String productId, Double latitude, Double longitude) {
        if ((latitude == null) != (longitude == null)
                || (latitude != null && (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                    || Math.abs(latitude) > 90 || Math.abs(longitude) > 180))) {
            throw new AppException(ErrorCode.INVALID_COORDINATES);
        }

        Product selected = productRepository.findById(productId.trim())
                .filter(product -> product.getStatus() == ProductStatus.ON_SALE)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        TryOnSlot selectedSlot = selected.getCategory().getTryonSlot();
        if (selectedSlot != TryOnSlot.TOP && selectedSlot != TryOnSlot.BOTTOM) {
            throw new AppException(ErrorCode.INVALID_SUGGESTION_SLOT);
        }
        TryOnSlot target = selectedSlot == TryOnSlot.TOP ? TryOnSlot.BOTTOM : TryOnSlot.TOP;
        var selectedTraits = OutfitMatchingRules.traits(selected);
        UUID selectedWarehouse = selected.getWarehouse().getId();

        record Match(Product product, int color, int style, boolean sameWarehouse, double distance) {}
        Comparator<Match> ranking = Comparator.comparingInt(Match::color).reversed()
                .thenComparing(Comparator.comparingInt(Match::style).reversed())
                .thenComparing(Match::sameWarehouse, Comparator.reverseOrder())
                .thenComparingDouble(Match::distance)
                .thenComparing(match -> match.product().getCreatedAt(), Comparator.reverseOrder())
                .thenComparing(match -> match.product().getId());

        List<Match> best = new ArrayList<>();
        Pageable page = PageRequest.of(0, 100, Sort.by("id"));
        Slice<Product> candidates;
        do {
            candidates = productRepository.findByStatusAndCategory_TryonSlot(ProductStatus.ON_SALE, target, page);
            for (Product product : candidates) {
                var traits = OutfitMatchingRules.traits(product);
                best.add(new Match(product, selectedTraits.colorScore(traits), selectedTraits.styleScore(traits),
                        selectedWarehouse.equals(product.getWarehouse().getId()),
                        OutfitMatchingRules.distanceKm(product.getWarehouse(), latitude, longitude)));
            }
            best.sort(ranking);
            if (best.size() > SUGGESTION_LIMIT) {
                best.subList(SUGGESTION_LIMIT, best.size()).clear();
            }
            page = candidates.nextPageable();
        } while (candidates.hasNext());
        List<TryOnSuggestionResponse> suggestions = mapSuggestions(best.stream().map(Match::product).toList());
        return geminiOutfitClient.rank(selected.getName(), selected.getCategory().getName(), suggestions);
    }

    private static TryOnSuggestionResponse toSuggestion(Product p, String thumbnailUrl) {
        return TryOnSuggestionResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .thumbnailUrl(thumbnailUrl)
                .brandName(p.getBrand() == null ? null : p.getBrand().getName())
                .categoryName(p.getCategory() == null ? null : p.getCategory().getName())
                .size(p.getSize())
                .condition(p.getConditionNote())
                .originalPrice(p.getConsignmentReceipt() == null ? null : p.getConsignmentReceipt().getAgreedPrice())
                .currentPrice(p.getCurrentPrice())
                .warehouseId(p.getWarehouse() == null ? null : p.getWarehouse().getId())
                .build();
    }

    // API 96 chỉ nhận TOP hoặc BOTTOM (đầm đi một mình, không cần món bù)
    private static TryOnSlot parseSuggestionSlot(String slot) {
        String value = blankToNull(slot);
        if (value != null) {
            String upper = value.toUpperCase(Locale.ROOT);
            if (upper.equals(TryOnSlot.TOP.name())) return TryOnSlot.TOP;
            if (upper.equals(TryOnSlot.BOTTOM.name())) return TryOnSlot.BOTTOM;
        }
        throw new AppException(ErrorCode.INVALID_SUGGESTION_SLOT);                          // 400
    }

    // Ảnh đại diện (ảnh đầu tiên) của mọi món trong các ảnh thử đồ, lấy bằng một query
    private Map<String, String> loadThumbnails(List<TryOnImage> images) {
        List<String> productIds = images.stream()
                .flatMap(i -> Stream.of(i.getTopProduct(), i.getBottomProduct(), i.getDressProduct()))
                .filter(Objects::nonNull)
                .map(Product::getId)
                .distinct()
                .toList();

        return productIds.isEmpty() ? Map.of()
                : productPhotoRepository.findByProduct_IdInOrderByDisplayOrderAscIdAsc(productIds).stream()
                .collect(Collectors.toMap(
                        ph -> ph.getProduct().getId(),
                        ProductPhoto::getImageUrl,
                        (first, second) -> first));
    }

    private TryOnImageResponse toResponse(TryOnImage image, Map<String, String> thumbnails) {
        return TryOnImageResponse.builder()
                .id(image.getId())
                .modelType(image.getModelType())
                .imageUrl(image.getImageUrl())
                .reused(!image.isQuotaCharged())
                .createdAt(image.getCreatedAt())
                .top(toProductInfo(image.getTopProduct(), thumbnails))
                .bottom(toProductInfo(image.getBottomProduct(), thumbnails))
                .dress(toProductInfo(image.getDressProduct(), thumbnails))
                .build();
    }

    private TryOnImageResponse.ProductInfo toProductInfo(Product product, Map<String, String> thumbnails) {
        if (product == null) return null;
        return TryOnImageResponse.ProductInfo.builder()
                .id(product.getId())
                .name(product.getName())
                .thumbnailUrl(thumbnails.get(product.getId()))
                .currentPrice(product.getCurrentPrice())
                .status(product.getStatus())
                .warehouseId(product.getWarehouse() != null ? product.getWarehouse().getId() : null)
                .build();
    }

    // ================================================================ F-081

    @Override
    @Transactional
    public void deleteImage(Account account, UUID imageId) {
        TryOnImage image = tryOnImageRepository.findById(imageId)
                .orElseThrow(() -> new AppException(ErrorCode.TRYON_IMAGE_NOT_FOUND));      // 404

        // Ảnh của người khác cũng trả 404 (API 99 chỉ định nghĩa 404), đồng thời không lộ ảnh có tồn tại hay không
        if (image.getDeletedAt() != null || !image.getMember().getId().equals(account.getId())) {
            throw new AppException(ErrorCode.TRYON_IMAGE_NOT_FOUND);                        // 404
        }

        if (image.getSharedAsset() != null) {
            image.setDeletedAt(java.time.Instant.now());
            tryOnImageRepository.saveAndFlush(image);
            return; // Shared file and original generation usage survive collection deletion.
        }

        String imageUrl = image.getImageUrl();

        // flush trước để lỗi DB (nếu có) nổ ra trước khi đụng tới file
        tryOnImageRepository.delete(image);
        tryOnImageRepository.flush();

        removeFile(imageUrl, imageId);
    }

    // Xóa file là việc phụ: lỗi hoặc không thấy file thì chỉ ghi log, không làm hỏng việc xóa
    // dòng trong bộ sưu tập mà Member đã yêu cầu.
    private void removeFile(String url, UUID imageId) {
        try {
            if (!fileStorageService.deleteByUrl(url, IMAGE_CONTENT_TYPE)) {
                log.warn(">>>> Khong tim thay file anh thu do de xoa: imageId={}, url={}", imageId, url);
            }
        } catch (RuntimeException e) {
            log.error(">>>> Xoa file anh thu do that bai: imageId={}, url={}: {}", imageId, url, e.getMessage());
        }
    }
}
