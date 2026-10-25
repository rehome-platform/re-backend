package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.client.GhnShippingFeeCalculator;
import com.rehome.rehome.dto.request.AddCartItemRequest;
import com.rehome.rehome.dto.response.CartItemDetailResponse;
import com.rehome.rehome.dto.response.CartItemResponse;
import com.rehome.rehome.dto.response.CartProductResponse;
import com.rehome.rehome.dto.response.CartResponse;
import com.rehome.rehome.dto.response.CartWarehouseGroupResponse;
import com.rehome.rehome.dto.response.CheckoutPreviewOrderResponse;
import com.rehome.rehome.dto.response.CheckoutPreviewResponse;
import com.rehome.rehome.dto.response.CheckoutPreviewUnavailableItemResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.marketplace.Cart;
import com.rehome.rehome.entity.marketplace.CartItem;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.ProductPhoto;
import com.rehome.rehome.entity.marketplace.ShippingAddress;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ConflictException;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.CartItemRepository;
import com.rehome.rehome.repository.CartRepository;
import com.rehome.rehome.repository.ProductPhotoRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.repository.ShippingAddressRepository;
import com.rehome.rehome.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartServiceImpl implements CartService {

    private final AccountRepository accountRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ProductPhotoRepository productPhotoRepository;
    private final ShippingAddressRepository shippingAddressRepository;
    private final GhnShippingFeeCalculator ghnShippingFeeCalculator;

    @Override
    @Transactional
    public CartItemResponse addItem(String email, AddCartItemRequest request) {

        if (!StringUtils.hasText(request.getProductId())) {
            throw new BadRequestException("productId không được để trống.");
        }

        Account member = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm."));

        if (product.getStatus() != ProductStatus.ON_SALE) {
            throw new ConflictException("Sản phẩm không còn để bán.", "PRODUCT_NOT_AVAILABLE");
        }

        // Chặn ngay ở cửa vào giỏ, nên getCart/checkout-preview không cần thêm lý do mới:
        // món của chính mình không bao giờ vào được giỏ (quyền sở hữu không chuyển tay).
        if (product.getMember().getId().equals(member.getId())) {
            throw new ConflictException(
                    "Bạn không thể mua món do chính mình ký gửi.", "CANNOT_BUY_OWN_PRODUCT");
        }

        Cart cart = cartRepository.findByMember_Id(member.getId())
                .orElseGet(() -> cartRepository.save(Cart.builder().member(member).build()));

        if (cartItemRepository.existsByCart_IdAndProduct_Id(cart.getId(), product.getId())) {
            throw new ConflictException("Sản phẩm đã có trong giỏ hàng.", "ALREADY_IN_CART");
        }

        CartItem item = CartItem.builder()
                .cart(cart)
                .product(product)
                .build();
        cartItemRepository.save(item);

        return CartItemResponse.builder()
                .id(item.getId().toString())
                .productId(product.getId())
                .addedAt(item.getAddedAt())
                .build();
    }

    @Override
    public CartResponse getCart(String email) {

        Account member = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        Cart cart = cartRepository.findByMember_Id(member.getId()).orElse(null);
        if (cart == null) {
            return CartResponse.builder()
                    .groupedByWarehouse(List.of())
                    .totalAmount(BigDecimal.ZERO)
                    .build();
        }

        List<CartItem> items = cartItemRepository.findAllByCartIdWithProduct(cart.getId());

        List<String> productIds = items.stream().map(ci -> ci.getProduct().getId()).toList();
        Map<String, String> thumbnailByProductId = productPhotoRepository
                .findByProduct_IdInOrderByIdAsc(productIds).stream()
                .collect(Collectors.toMap(
                        photo -> photo.getProduct().getId(),
                        ProductPhoto::getImageUrl,
                        (first, second) -> first));

        Map<Warehouse, List<CartItem>> itemsByWarehouse = items.stream()
                .collect(Collectors.groupingBy(ci -> ci.getProduct().getWarehouse(),
                        LinkedHashMap::new, Collectors.toList()));

        List<CartWarehouseGroupResponse> groups = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (Map.Entry<Warehouse, List<CartItem>> entry : itemsByWarehouse.entrySet()) {
            Warehouse warehouse = entry.getKey();
            BigDecimal subtotal = BigDecimal.ZERO;
            List<CartItemDetailResponse> itemResponses = new ArrayList<>();

            for (CartItem ci : entry.getValue()) {
                Product product = ci.getProduct();
                String unavailableReason = product.getStatus() == ProductStatus.ON_SALE
                        ? null
                        : product.getStatus().name();

                if (unavailableReason == null) {
                    subtotal = subtotal.add(product.getCurrentPrice());
                }

                itemResponses.add(CartItemDetailResponse.builder()
                        .id(ci.getId().toString())
                        .product(CartProductResponse.builder()
                                .id(product.getId())
                                .name(product.getName())
                                .thumbnailUrl(thumbnailByProductId.get(product.getId()))
                                .brandName(product.getBrand().getName())
                                .size(product.getSize())
                                .condition(product.getConditionNote())
                                .originalPrice(product.getConsignmentReceipt().getAgreedPrice())
                                .currentPrice(product.getCurrentPrice())
                                .build())
                        .addedAt(ci.getAddedAt())
                        .unavailableReason(unavailableReason)
                        .build());
            }

            totalAmount = totalAmount.add(subtotal);

            groups.add(CartWarehouseGroupResponse.builder()
                    .warehouseId(warehouse.getId().toString())
                    .warehouseName(warehouse.getName())
                    .items(itemResponses)
                    .subtotal(subtotal)
                    .build());
        }

        return CartResponse.builder()
                .groupedByWarehouse(groups)
                .totalAmount(totalAmount)
                .build();
    }

    @Override
    @Transactional
    public void deleteItem(String email, UUID id) {

        Account member = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        CartItem item = cartItemRepository.findByIdAndCart_Member_Id(id, member.getId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy mục giỏ hàng."));

        cartItemRepository.delete(item);
    }

    @Override
    public CheckoutPreviewResponse checkoutPreview(String email, String shippingAddressId) {

        Account member = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        if (!StringUtils.hasText(shippingAddressId)) {
            throw new IllegalArgumentException("Không tìm thấy địa chỉ giao hàng.");
        }

        UUID addressId;
        try {
            addressId = UUID.fromString(shippingAddressId);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Không tìm thấy địa chỉ giao hàng.");
        }

        ShippingAddress shippingAddress = shippingAddressRepository.findById(addressId)
                .filter(a -> a.getMember().getId().equals(member.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy địa chỉ giao hàng."));

        Cart cart = cartRepository.findByMember_Id(member.getId()).orElse(null);
        List<CartItem> items = cart == null
                ? List.of()
                : cartItemRepository.findAllByCartIdWithProduct(cart.getId());

        if (items.isEmpty()) {
            throw new ConflictException("Giỏ hàng đang trống.");
        }

        List<CheckoutPreviewUnavailableItemResponse> unavailableItems = new ArrayList<>();
        Map<Warehouse, List<Product>> availableByWarehouse = new LinkedHashMap<>();

        for (CartItem ci : items) {
            Product product = ci.getProduct();
            if (product.getStatus() != ProductStatus.ON_SALE) {
                unavailableItems.add(CheckoutPreviewUnavailableItemResponse.builder()
                        .productId(product.getId())
                        .reason(product.getStatus().name())
                        .build());
                continue;
            }
            availableByWarehouse.computeIfAbsent(product.getWarehouse(), w -> new ArrayList<>()).add(product);
        }

        List<CheckoutPreviewOrderResponse> orders = new ArrayList<>();
        for (Map.Entry<Warehouse, List<Product>> entry : availableByWarehouse.entrySet()) {
            Warehouse warehouse = entry.getKey();
            List<Product> products = entry.getValue();

            BigDecimal subtotal = products.stream()
                    .map(Product::getCurrentPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal shippingFee = ghnShippingFeeCalculator.calculate(
                    warehouse, shippingAddress.getWardName(), shippingAddress.getProvinceName());

            orders.add(CheckoutPreviewOrderResponse.builder()
                    .warehouseId(warehouse.getId().toString())
                    .items(products.stream().map(Product::getId).toList())
                    .subtotal(subtotal)
                    .shippingFee(shippingFee)
                    .total(subtotal.add(shippingFee))
                    .build());
        }

        return CheckoutPreviewResponse.builder()
                .orders(orders)
                .unavailableItems(unavailableItems)
                .build();
    }
}
