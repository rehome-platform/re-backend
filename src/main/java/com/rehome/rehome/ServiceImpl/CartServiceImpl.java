package com.rehome.rehome.ServiceImpl;

import com.rehome.rehome.dto.request.AddCartItemRequest;
import com.rehome.rehome.dto.response.CartItemResponse;
import com.rehome.rehome.entity.Account;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.marketplace.Cart;
import com.rehome.rehome.entity.marketplace.CartItem;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ConflictException;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.CartItemRepository;
import com.rehome.rehome.repository.CartRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartServiceImpl implements CartService {

    private final AccountRepository accountRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

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
            throw new ConflictException("Sản phẩm không còn để bán.");
        }

        Cart cart = cartRepository.findByMember_Id(member.getId())
                .orElseGet(() -> cartRepository.save(Cart.builder().member(member).build()));

        if (cartItemRepository.existsByCart_IdAndProduct_Id(cart.getId(), product.getId())) {
            throw new ConflictException("Sản phẩm đã có trong giỏ hàng.");
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
}
