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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock AccountRepository accountRepository;
    @Mock CartRepository cartRepository;
    @Mock CartItemRepository cartItemRepository;
    @Mock ProductRepository productRepository;

    @InjectMocks
    CartServiceImpl cartService;

    Account member;
    Product product;
    String email;

    @BeforeEach
    void setup() {
        email = "member@example.com";
        member = Account.builder().id(UUID.randomUUID()).email(email).build();
        product = Product.builder().id("P-001").status(ProductStatus.ON_SALE).build();
    }

    @Test
    void addItem_throwsBadRequest_whenProductIdIsBlank() {
        assertThatThrownBy(() -> cartService.addItem(email, new AddCartItemRequest("  ")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("productId không được để trống.");

        verify(accountRepository, never()).findByEmail(any());
    }

    @Test
    void addItem_throwsNotFound_whenProductDoesNotExist() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(productRepository.findById("P-404")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.addItem(email, new AddCartItemRequest("P-404")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy sản phẩm.");
    }

    @Test
    void addItem_throwsConflict_whenProductIsNotOnSale() {
        product.setStatus(ProductStatus.RESERVED);
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(productRepository.findById("P-001")).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> cartService.addItem(email, new AddCartItemRequest("P-001")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Sản phẩm không còn để bán.");

        verify(cartRepository, never()).findByMember_Id(any());
    }

    @Test
    void addItem_throwsConflict_whenItemAlreadyInCart() {
        Cart cart = Cart.builder().id(UUID.randomUUID()).member(member).build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(productRepository.findById("P-001")).thenReturn(Optional.of(product));
        when(cartRepository.findByMember_Id(member.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepository.existsByCart_IdAndProduct_Id(cart.getId(), "P-001")).thenReturn(true);

        assertThatThrownBy(() -> cartService.addItem(email, new AddCartItemRequest("P-001")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Sản phẩm đã có trong giỏ hàng.");

        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void addItem_createsCart_whenMemberHasNoCartYet() {
        Cart newCart = Cart.builder().id(UUID.randomUUID()).member(member).build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(productRepository.findById("P-001")).thenReturn(Optional.of(product));
        when(cartRepository.findByMember_Id(member.getId())).thenReturn(Optional.empty());
        when(cartRepository.save(any())).thenReturn(newCart);
        when(cartItemRepository.existsByCart_IdAndProduct_Id(newCart.getId(), "P-001")).thenReturn(false);
        when(cartItemRepository.save(any())).thenAnswer(invocation -> {
            CartItem saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        CartItemResponse response = cartService.addItem(email, new AddCartItemRequest("P-001"));

        assertThat(response.getProductId()).isEqualTo("P-001");
        verify(cartRepository).save(any());
    }

    @Test
    void addItem_returnsAddedItem_onSuccess() {
        Cart cart = Cart.builder().id(UUID.randomUUID()).member(member).build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(productRepository.findById("P-001")).thenReturn(Optional.of(product));
        when(cartRepository.findByMember_Id(member.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepository.existsByCart_IdAndProduct_Id(cart.getId(), "P-001")).thenReturn(false);
        when(cartItemRepository.save(any())).thenAnswer(invocation -> {
            CartItem saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setAddedAt(java.time.Instant.now());
            return saved;
        });

        CartItemResponse response = cartService.addItem(email, new AddCartItemRequest("P-001"));

        assertThat(response.getProductId()).isEqualTo("P-001");
        assertThat(response.getId()).isNotNull();
        assertThat(response.getAddedAt()).isNotNull();

        ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository).save(captor.capture());
        assertThat(captor.getValue().getCart()).isEqualTo(cart);
        assertThat(captor.getValue().getProduct()).isEqualTo(product);
    }
}
