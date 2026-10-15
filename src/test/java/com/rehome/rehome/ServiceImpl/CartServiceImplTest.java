package com.rehome.rehome.ServiceImpl;

import com.rehome.rehome.dto.request.AddCartItemRequest;
import com.rehome.rehome.dto.response.CartItemResponse;
import com.rehome.rehome.dto.response.CartResponse;
import com.rehome.rehome.dto.response.CartWarehouseGroupResponse;
import com.rehome.rehome.entity.Account;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.marketplace.Cart;
import com.rehome.rehome.entity.marketplace.CartItem;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.ProductPhoto;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ConflictException;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.CartItemRepository;
import com.rehome.rehome.repository.CartRepository;
import com.rehome.rehome.repository.ProductPhotoRepository;
import com.rehome.rehome.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
    @Mock ProductPhotoRepository productPhotoRepository;

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

    @Test
    void getCart_returnsEmptyResult_whenMemberHasNoCart() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(cartRepository.findByMember_Id(member.getId())).thenReturn(Optional.empty());

        CartResponse response = cartService.getCart(email);

        assertThat(response.getGroupedByWarehouse()).isEmpty();
        assertThat(response.getTotalAmount()).isEqualTo(BigDecimal.ZERO);
        verify(cartItemRepository, never()).findAllByCartIdWithProduct(any());
    }

    @Test
    void getCart_throwsNotFound_whenAccountDoesNotExist() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.getCart(email))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account không tồn tại");
    }

    @Test
    void getCart_groupsItemsByWarehouse_andComputesSubtotalsExcludingUnavailableItems() {
        Cart cart = Cart.builder().id(UUID.randomUUID()).member(member).build();

        Warehouse warehouse1 = Warehouse.builder().id(UUID.randomUUID()).name("Kho Quận 7").build();
        Warehouse warehouse2 = Warehouse.builder().id(UUID.randomUUID()).name("Kho Bình Thạnh").build();

        ConsignmentReceipt receipt1 = ConsignmentReceipt.builder().agreedPrice(new BigDecimal("350000")).build();
        ConsignmentReceipt receipt2 = ConsignmentReceipt.builder().agreedPrice(new BigDecimal("450000")).build();

        Product availableProduct = Product.builder()
                .id("Q7-AK-000845")
                .name("Áo sơ mi Zara trắng")
                .size("M")
                .currentPrice(new BigDecimal("220000"))
                .status(ProductStatus.ON_SALE)
                .warehouse(warehouse1)
                .consignmentReceipt(receipt1)
                .build();

        Product soldProduct = Product.builder()
                .id("Q7-AK-000912")
                .name("Quần jean Levi's xanh")
                .size("30")
                .currentPrice(new BigDecimal("380000"))
                .status(ProductStatus.SOLD)
                .warehouse(warehouse2)
                .consignmentReceipt(receipt2)
                .build();

        CartItem item1 = CartItem.builder()
                .id(UUID.randomUUID())
                .cart(cart)
                .product(availableProduct)
                .addedAt(Instant.parse("2026-10-13T09:00:00Z"))
                .build();

        CartItem item2 = CartItem.builder()
                .id(UUID.randomUUID())
                .cart(cart)
                .product(soldProduct)
                .addedAt(Instant.parse("2026-10-14T14:20:00Z"))
                .build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(cartRepository.findByMember_Id(member.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCartIdWithProduct(cart.getId())).thenReturn(List.of(item1, item2));

        ProductPhoto photo = ProductPhoto.builder()
                .product(availableProduct)
                .imageUrl("https://cdn.rehome.vn/products/Q7-AK-000845/thumb.jpg")
                .build();
        when(productPhotoRepository.findByProduct_IdInOrderByIdAsc(List.of("Q7-AK-000845", "Q7-AK-000912")))
                .thenReturn(List.of(photo));

        CartResponse response = cartService.getCart(email);

        assertThat(response.getGroupedByWarehouse()).hasSize(2);
        assertThat(response.getTotalAmount()).isEqualTo(new BigDecimal("220000"));

        CartWarehouseGroupResponse group1 = response.getGroupedByWarehouse().get(0);
        assertThat(group1.getWarehouseId()).isEqualTo(warehouse1.getId().toString());
        assertThat(group1.getWarehouseName()).isEqualTo("Kho Quận 7");
        assertThat(group1.getSubtotal()).isEqualTo(new BigDecimal("220000"));
        assertThat(group1.getItems()).hasSize(1);
        assertThat(group1.getItems().get(0).getUnavailableReason()).isNull();
        assertThat(group1.getItems().get(0).getProduct().getThumbnailUrl())
                .isEqualTo("https://cdn.rehome.vn/products/Q7-AK-000845/thumb.jpg");
        assertThat(group1.getItems().get(0).getProduct().getOriginalPrice()).isEqualTo(new BigDecimal("350000"));
        assertThat(group1.getItems().get(0).getProduct().getCurrentPrice()).isEqualTo(new BigDecimal("220000"));

        CartWarehouseGroupResponse group2 = response.getGroupedByWarehouse().get(1);
        assertThat(group2.getWarehouseId()).isEqualTo(warehouse2.getId().toString());
        assertThat(group2.getWarehouseName()).isEqualTo("Kho Bình Thạnh");
        assertThat(group2.getSubtotal()).isEqualTo(BigDecimal.ZERO);
        assertThat(group2.getItems()).hasSize(1);
        assertThat(group2.getItems().get(0).getUnavailableReason()).isEqualTo("SOLD");
        assertThat(group2.getItems().get(0).getProduct().getThumbnailUrl()).isNull();
    }
}
