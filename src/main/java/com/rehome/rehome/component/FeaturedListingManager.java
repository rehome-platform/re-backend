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

/**
 * Bật/tắt cột {@code products.featured_until} - quyền lợi "ưu tiên hiển thị + nhãn Nổi bật" của
 * gói VIP. <b>Không có endpoint nào cho Member</b>: hệ thống tự làm, Member không bấm gì.
 *
 * <p>Đặc tả §8.10 nói rõ phải set/clear ở đúng 4 thời điểm, và đây là nơi tập trung cả 4 để không
 * bỏ sót chỗ nào:
 * <ol>
 *   <li>Subscription chuyển {@code ACTIVE} (mua mới / gia hạn / Admin tặng) -> {@link #applyForSubscription}</li>
 *   <li>Product mới được tạo (Moderator nhập kho) hoặc ký gửi lại -> {@link #applyForNewProduct}</li>
 *   <li>Subscription chuyển {@code EXPIRED} / {@code CANCELLED} / {@code REPLACED} -> {@link #clearForMember}</li>
 *   <li>Đổi sang hạng KHÔNG có {@code featured_listing} -> cũng {@link #applyForSubscription}, tự clear</li>
 * </ol>
 *
 * <p>Đánh đổi đã biết: dùng một cột tính sẵn thay vì join sang {@code member_subscriptions} lúc
 * query, vì {@code GET /products} là màn nóng nhất và join cho mọi dòng sẽ đắt. Giá phải trả là
 * phải nhớ gọi đúng 4 chỗ trên.
 *
 * <p>Không cần job dọn: "đang nổi bật" = {@code featured_until > now()}, nên mốc quá khứ tự hết
 * tác dụng lúc query.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FeaturedListingManager {

    // Món còn trong vòng đời bán thì mới có ý nghĩa gắn nhãn:
    //  - ON_SALE: đang trên sàn, đúng nhóm được ưu tiên hiển thị
    //  - WAREHOUSE: còn trong kho, tạm ẩn khỏi sàn chờ Member quyết định - quay lại sàn (ký gửi
    //    lại) thì đã có nhãn sẵn
    //  - RESERVED: đang có người đặt; giữ nhãn để đơn bị hủy, món về ON_SALE thì không mất ưu tiên
    private static final List<ProductStatus> SELLABLE_STATUSES =
            List.of(ProductStatus.ON_SALE, ProductStatus.WAREHOUSE, ProductStatus.RESERVED);

    private final ProductRepository productRepository;
    private final MemberSubscriptionRepository memberSubscriptionRepository;

    /**
     * Thời điểm 1 và 4. Gọi ngay sau khi subscription chuyển ACTIVE: hạng có
     * {@code featured_listing} thì set {@code featured_until = subscription.expires_at} cho MỌI
     * món còn bán được của Member; hạng không có thì xóa sạch.
     */
    @Transactional
    public void applyForSubscription(MemberSubscription subscription) {
        UUID memberId = subscription.getMember().getId();

        if (!Boolean.TRUE.equals(subscription.getFeaturedListing())) {
            clearForMember(memberId);
            return;
        }

        List<Product> products = productRepository.findAllByMember_IdAndStatusIn(memberId, SELLABLE_STATUSES);
        for (Product p : products) {
            p.setFeaturedUntil(subscription.getExpiresAt());
        }
        productRepository.saveAll(products);
        log.info(">>>> Bat uu tien hien thi cho {} mon cua memberId={} den {}",
                products.size(), memberId, subscription.getExpiresAt());
    }

    /**
     * Thời điểm 2. Product vừa được tạo (nhập kho) hoặc ký gửi lại: nếu Member đang có gói ACTIVE
     * còn hạn và gói đó có {@code featured_listing} thì gắn nhãn ngay, không đợi tới lần gia hạn.
     *
     * <p>Không tự lưu product: nơi gọi đang trong transaction của mình và sẽ {@code save} sau, nên
     * chỉ set field để tránh hai lần UPDATE.
     */
    public void applyForNewProduct(Product product) {
        Instant now = Instant.now();
        memberSubscriptionRepository.findActiveByMemberId(product.getMember().getId())
                .filter(s -> s.isEffectiveAt(now))
                .filter(s -> Boolean.TRUE.equals(s.getFeaturedListing()))
                .ifPresent(s -> product.setFeaturedUntil(s.getExpiresAt()));
    }

    /** Thời điểm 3. Gói hết hạn / bị hủy / bị thay thế: xóa nhãn trên mọi món của Member. */
    @Transactional
    public void clearForMember(UUID memberId) {
        int cleared = productRepository.clearFeaturedByMemberId(memberId);
        if (cleared > 0) {
            log.info(">>>> Tat uu tien hien thi cho {} mon cua memberId={}", cleared, memberId);
        }
    }

    /** Số món đang thực sự nổi bật, cho {@code GET /memberships/me} và banner M-09. */
    @Transactional(readOnly = true)
    public long countFeatured(UUID memberId) {
        return productRepository.countFeaturedByMemberId(memberId, Instant.now());
    }

    public static boolean isFeatured(Product product) {
        return product.getFeaturedUntil() != null && product.getFeaturedUntil().isAfter(Instant.now());
    }
}
