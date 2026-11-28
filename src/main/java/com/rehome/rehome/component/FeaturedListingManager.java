package com.rehome.rehome.component;

import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.membership.MemberSubscription;
import com.rehome.rehome.repository.MemberSubscriptionRepository;
import com.rehome.rehome.repository.ProductRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class FeaturedListingManager {

    private static final List<ProductStatus> SELLABLE_STATUSES =
            List.of(ProductStatus.ON_SALE, ProductStatus.WAREHOUSE, ProductStatus.RESERVED);

    private final ProductRepository productRepository;
    private final MemberSubscriptionRepository memberSubscriptionRepository;

    @Transactional
    public void applyForSubscription(MemberSubscription subscription) {
        UUID memberId = subscription.getMember().getId();

        if (!Boolean.TRUE.equals(subscription.getFeaturedListing())) {
            clearForMember(memberId);
            return;
        }

        List<Product> products = productRepository.findAllByMember_IdAndStatusIn(memberId, SELLABLE_STATUSES);
        for (Product p : products) {
            applyRank(p, subscription);
        }
        productRepository.saveAll(products);
        log.info(">>>> Bat uu tien hien thi cho {} mon cua memberId={} den {}",
                products.size(), memberId, subscription.getExpiresAt());
    }

    public void applyForNewProduct(Product product) {
        Instant now = Instant.now();
        memberSubscriptionRepository.findActiveByMemberId(product.getMember().getId())
                .filter(s -> s.isEffectiveAt(now))
                .filter(s -> Boolean.TRUE.equals(s.getFeaturedListing()))
                .ifPresentOrElse(s -> applyRank(product, s), () -> {
                    product.setFeaturedRank(null);
                    product.setFeaturedUntil(null);
                });
    }

    private void applyRank(Product product, MemberSubscription subscription) {
        Integer rank = switch (subscription.getTier()) {
            case DIAMOND -> 3;
            case GOLD -> 2;
            case SILVER -> 1;
            default -> null;
        };
        product.setFeaturedRank(rank);
        product.setFeaturedUntil(rank == null ? null : subscription.getExpiresAt());
    }

    @Transactional
    public void clearForMember(UUID memberId) {
        int cleared = productRepository.clearFeaturedByMemberId(memberId);
        if (cleared > 0) {
            log.info(">>>> Tat uu tien hien thi cho {} mon cua memberId={}", cleared, memberId);
        }
    }

    @Transactional(readOnly = true)
    public long countFeatured(UUID memberId) {
        return productRepository.countFeaturedByMemberId(memberId, Instant.now());
    }

    public static boolean isFeatured(Product product) {
        return product.getFeaturedUntil() != null && product.getFeaturedUntil().isAfter(Instant.now());
    }
}
