package com.rehome.rehome.service;

import com.rehome.rehome.component.OutfitPreferenceModel;
import com.rehome.rehome.dto.response.MlOutfitSuggestionsResponse;
import com.rehome.rehome.dto.response.TryOnSuggestionResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.TryOnSlot;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.ProductPhoto;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.OrderItemRepository;
import com.rehome.rehome.repository.ProductPhotoRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.util.OutfitMatchingRules;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MlOutfitSuggestionService {
    private static final int HISTORY_LIMIT = 200;
    private static final int CANDIDATE_LIMIT = 500;
    private static final int RESULT_LIMIT = 10;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductPhotoRepository productPhotoRepository;

    @Transactional(readOnly = true)
    public MlOutfitSuggestionsResponse suggest(Account member, String productId, Double latitude, Double longitude) {
        if (member == null) throw new AppException(ErrorCode.UNAUTHORIZED);
        if ((latitude == null) != (longitude == null)
                || (latitude != null && (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || Math.abs(latitude) > 90 || Math.abs(longitude) > 180))) {
            throw new AppException(ErrorCode.INVALID_COORDINATES);
        }
        if (productId == null || productId.isBlank()) throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
        Product selected = productRepository.findWithCategoryByIdIn(List.of(productId.trim())).stream()
                .filter(p -> p.getStatus() == ProductStatus.ON_SALE).findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        TryOnSlot slot = selected.getCategory().getTryonSlot();
        if (slot != TryOnSlot.TOP && slot != TryOnSlot.BOTTOM) throw new AppException(ErrorCode.INVALID_SUGGESTION_SLOT);
        TryOnSlot target = slot == TryOnSlot.TOP ? TryOnSlot.BOTTOM : TryOnSlot.TOP;
        List<Product> candidates = productRepository.findMlOutfitCandidates(target, selected.getId(), member.getId(),
                PageRequest.of(0, CANDIDATE_LIMIT));
        var history = orderItemRepository.findOutfitPreferenceHistory(member.getId(), PageRequest.of(0, HISTORY_LIMIT));
        var purchases = history.stream().map(item -> {
            Product p = item.getProduct();
            return new OutfitPreferenceModel.Item(p.getName(), p.getCategory().getName(),
                    p.getBrand().getName(), p.getSize(), item.getPriceAtOrder());
        }).toList();
        var model = new OutfitPreferenceModel(purchases, candidates.stream().map(MlOutfitSuggestionService::features).toList());
        boolean personalized = !purchases.isEmpty();
        var selectedTraits = OutfitMatchingRules.traits(selected);
        record Match(Product product, double score, boolean sameWarehouse, double distance) {}
        List<Match> ranked = candidates.stream().map(p -> {
            var traits = OutfitMatchingRules.traits(p);
            double compatibility = 0.35 * selectedTraits.colorScore(traits) / 3.0
                    + 0.25 * selectedTraits.styleScore(traits) / 2.0;
            double preference = personalized ? 0.40 * Math.tanh(model.score(features(p))) : 0;
            return new Match(p, compatibility + preference,
                    Objects.equals(selected.getWarehouse().getId(), p.getWarehouse().getId()),
                    OutfitMatchingRules.distanceKm(p.getWarehouse(), latitude, longitude));
        }).sorted(Comparator.comparingDouble(Match::score).reversed()
                .thenComparing(Match::sameWarehouse, Comparator.reverseOrder())
                .thenComparingDouble(Match::distance)
                .thenComparing(m -> m.product().getCreatedAt(), Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(m -> m.product().getId())).limit(RESULT_LIMIT).toList();
        List<String> ids = ranked.stream().map(m -> m.product().getId()).toList();
        Map<String, String> photos = ids.isEmpty() ? Map.of() : productPhotoRepository
                .findByProduct_IdInOrderByDisplayOrderAscIdAsc(ids).stream()
                .collect(Collectors.toMap(ph -> ph.getProduct().getId(), ProductPhoto::getImageUrl, (first, next) -> first));
        List<TryOnSuggestionResponse> items = ranked.stream().map(match -> {
            Product p = match.product();
            return TryOnSuggestionResponse.builder().id(p.getId()).name(p.getName()).thumbnailUrl(photos.get(p.getId()))
                    .brandName(p.getBrand().getName()).categoryName(p.getCategory().getName()).size(p.getSize())
                    .condition(p.getConditionNote()).currentPrice(p.getCurrentPrice())
                    .originalPrice(p.getConsignmentReceipt().getAgreedPrice()).warehouseId(p.getWarehouse().getId())
                    .reason(personalized ? "Xếp hạng theo lịch sử mua và độ phù hợp với món đang chọn"
                            : "Chưa có lịch sử mua hợp lệ; gợi ý theo màu sắc và phong cách món đang chọn")
                    .build();
        }).toList();
        return MlOutfitSuggestionsResponse.builder().model(OutfitPreferenceModel.VERSION).personalized(personalized)
                .historyItemsUsed(purchases.size()).candidateItemsScored(candidates.size()).items(items).build();
    }

    private static OutfitPreferenceModel.Item features(Product p) {
        return new OutfitPreferenceModel.Item(p.getName(), p.getCategory().getName(), p.getBrand().getName(),
                p.getSize(), p.getCurrentPrice());
    }
}
