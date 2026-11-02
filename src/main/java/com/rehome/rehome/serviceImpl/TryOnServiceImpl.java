package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.TryOnImageResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.ProductPhoto;
import com.rehome.rehome.entity.tryon.TryOnImage;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.ProductPhotoRepository;
import com.rehome.rehome.repository.TryOnImageRepository;
import com.rehome.rehome.service.FileStorageService;
import com.rehome.rehome.service.TryOnService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class TryOnServiceImpl implements TryOnService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    // Chỉ cần biết là ảnh để storage chọn đúng resource_type khi xóa
    private static final String IMAGE_CONTENT_TYPE = "image/png";

    private final TryOnImageRepository tryOnImageRepository;
    private final ProductPhotoRepository productPhotoRepository;
    private final FileStorageService fileStorageService;

    // ================================================================ F-080

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TryOnImageResponse> getCollection(Account account, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<TryOnImage> result = tryOnImageRepository.findByMember_Id(account.getId(), pageable);

        // Ảnh đại diện của mọi món trong trang lấy bằng một query, tránh N+1
        List<String> productIds = result.getContent().stream()
                .flatMap(i -> Stream.of(i.getTopProduct(), i.getBottomProduct(), i.getDressProduct()))
                .filter(Objects::nonNull)
                .map(Product::getId)
                .distinct()
                .toList();

        Map<String, String> thumbnails = productIds.isEmpty() ? Map.of()
                : productPhotoRepository.findByProduct_IdInOrderByDisplayOrderAscIdAsc(productIds).stream()
                .collect(Collectors.toMap(
                        ph -> ph.getProduct().getId(),
                        ProductPhoto::getImageUrl,
                        (first, second) -> first));

        return PageResponse.from(result.map(i -> toResponse(i, thumbnails)));
    }

    private TryOnImageResponse toResponse(TryOnImage image, Map<String, String> thumbnails) {
        return TryOnImageResponse.builder()
                .id(image.getId())
                .modelType(image.getModelType())
                .imageUrl(image.getImageUrl())
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
                .build();
    }

    // ================================================================ F-081

    @Override
    @Transactional
    public void deleteImage(Account account, UUID imageId) {
        TryOnImage image = tryOnImageRepository.findById(imageId)
                .orElseThrow(() -> new AppException(ErrorCode.TRYON_IMAGE_NOT_FOUND));      // 404

        // Ảnh của người khác cũng trả 404 (API 99 chỉ định nghĩa 404), đồng thời không lộ ảnh có tồn tại hay không
        if (!image.getMember().getId().equals(account.getId())) {
            throw new AppException(ErrorCode.TRYON_IMAGE_NOT_FOUND);                        // 404
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