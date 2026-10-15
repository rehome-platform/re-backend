package com.rehome.rehome;

import com.rehome.rehome.entity.Account;
import com.rehome.rehome.entity.AccountStatus;
import com.rehome.rehome.entity.Role;
import com.rehome.rehome.entity.enums.CatalogStatus;
import com.rehome.rehome.entity.enums.ConsignmentFormStatus;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.TryOnSlot;
import com.rehome.rehome.entity.marketplace.Brand;
import com.rehome.rehome.entity.marketplace.Category;
import com.rehome.rehome.entity.marketplace.ConsignmentForm;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.ProductPhoto;
import com.rehome.rehome.entity.marketplace.Warehouse;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired EntityManager em;

    Warehouse warehouse;
    Account member;
    Account moderator;

    @BeforeEach
    void setup() {
        warehouse = Warehouse.builder()
                .name("Kho Quận 7").address("123 Nguyễn Văn Linh")
                .latitude(new BigDecimal("10.7326")).longitude(new BigDecimal("106.7217"))
                .openingHours("08:00 - 20:00").status(CatalogStatus.ACTIVE)
                .build();
        em.persist(warehouse);

        member = Account.builder()
                .email("member-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hash").role(Role.MEMBER).status(AccountStatus.ACTIVE)
                .emailVerified(true)
                .build();
        em.persist(member);

        moderator = Account.builder()
                .email("moderator-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hash").role(Role.MODERATOR).status(AccountStatus.ACTIVE)
                .emailVerified(true)
                .build();
        em.persist(moderator);
    }

    private Product createProduct(String id, String name, BigDecimal currentPrice, ProductStatus status,
                                   Category category, Brand brand) {
        ConsignmentForm form = ConsignmentForm.builder()
                .member(member).warehouse(warehouse).category(category).brand(brand)
                .productName(name).description("Hàng còn mới").desiredPrice(currentPrice)
                .status(ConsignmentFormStatus.APPROVED)
                .build();
        em.persist(form);

        ConsignmentReceipt receipt = ConsignmentReceipt.builder()
                .receiptNo("RC-" + UUID.randomUUID())
                .consignmentForm(form).member(member).moderator(moderator).warehouse(warehouse)
                .agreedPrice(currentPrice.multiply(new BigDecimal("1.5")))
                .floorPrice(currentPrice.multiply(new BigDecimal("0.75")))
                .withdrawalFee(new BigDecimal("20000"))
                .confirmedAt(Instant.now())
                .pdfUrl("https://example.com/receipt.pdf")
                .build();
        em.persist(receipt);

        Product product = Product.builder()
                .id(id)
                .consignmentReceipt(receipt).member(member).warehouse(warehouse)
                .category(category).brand(brand)
                .name(name).size("M").conditionNote("LIKE_NEW")
                .currentPrice(currentPrice).status(status)
                .termStartedAt(Instant.now())
                .build();
        em.persist(product);

        ProductPhoto photo = ProductPhoto.builder()
                .product(product)
                .imageUrl("https://cdn.rehome.vn/products/" + id + "/thumb.jpg")
                .isBackgroundRemoved(true)
                .build();
        em.persist(photo);

        return product;
    }

    private Category createCategory(String name) {
        Category category = Category.builder()
                .name(name).tryonSlot(TryOnSlot.TOP).status(CatalogStatus.ACTIVE)
                .build();
        em.persist(category);
        return category;
    }

    private Brand createBrand(String name) {
        Brand brand = Brand.builder().name(name).status(CatalogStatus.ACTIVE).build();
        em.persist(brand);
        return brand;
    }

    @Test
    void listsOnlyOnSaleProductsWithFullFields() throws Exception {
        Category category = createCategory("Áo sơ mi");
        Brand brand = createBrand("Zara");

        createProduct("P-001", "Áo sơ mi Zara trắng", new BigDecimal("220000"), ProductStatus.ON_SALE, category, brand);
        createProduct("P-002", "Áo sơ mi Zara xanh", new BigDecimal("230000"), ProductStatus.RESERVED, category, brand);
        createProduct("P-003", "Áo sơ mi Zara đen", new BigDecimal("210000"), ProductStatus.SOLD, category, brand);
        em.flush();

        mvc.perform(get("/products").param("brandId", brand.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("isSuccess").value(true))
                .andExpect(jsonPath("result.totalElements").value(1))
                .andExpect(jsonPath("result.content[0].id").value("P-001"))
                .andExpect(jsonPath("result.content[0].name").value("Áo sơ mi Zara trắng"))
                .andExpect(jsonPath("result.content[0].brandName").value("Zara"))
                .andExpect(jsonPath("result.content[0].categoryName").value("Áo sơ mi"))
                .andExpect(jsonPath("result.content[0].size").value("M"))
                .andExpect(jsonPath("result.content[0].condition").value("LIKE_NEW"))
                .andExpect(jsonPath("result.content[0].currentPrice").value(220000))
                .andExpect(jsonPath("result.content[0].originalPrice").value(330000))
                .andExpect(jsonPath("result.content[0].thumbnailUrl").value("https://cdn.rehome.vn/products/P-001/thumb.jpg"));
    }

    @Test
    void filtersBySearchKeyword() throws Exception {
        Category category = createCategory("Quần");
        Brand brand = createBrand("Levi's");

        createProduct("P-010", "Quần jeans Levi's 501", new BigDecimal("350000"), ProductStatus.ON_SALE, category, brand);
        createProduct("P-011", "Áo thun Uniqlo", new BigDecimal("150000"), ProductStatus.ON_SALE, category, brand);
        em.flush();

        mvc.perform(get("/products").param("search", "jeans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.totalElements").value(1))
                .andExpect(jsonPath("result.content[0].id").value("P-010"));
    }

    @Test
    void filtersByCategoryAndPriceRange() throws Exception {
        Category category = createCategory("Đầm");
        Brand brand = createBrand("Mango");

        createProduct("P-020", "Đầm maxi hoa nhí", new BigDecimal("190000"), ProductStatus.ON_SALE, category, brand);
        createProduct("P-021", "Đầm dạ hội", new BigDecimal("500000"), ProductStatus.ON_SALE, category, brand);
        em.flush();

        mvc.perform(get("/products")
                        .param("categoryId", category.getId().toString())
                        .param("priceMin", "100000")
                        .param("priceMax", "300000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.totalElements").value(1))
                .andExpect(jsonPath("result.content[0].id").value("P-020"));
    }

    @Test
    void paginatesResultsAcrossPages() throws Exception {
        Category category = createCategory("Áo khoác");
        Brand brand = createBrand("H&M");

        createProduct("P-030", "Áo khoác denim A", new BigDecimal("280000"), ProductStatus.ON_SALE, category, brand);
        createProduct("P-031", "Áo khoác denim B", new BigDecimal("290000"), ProductStatus.ON_SALE, category, brand);
        createProduct("P-032", "Áo khoác denim C", new BigDecimal("300000"), ProductStatus.ON_SALE, category, brand);
        em.flush();

        mvc.perform(get("/products")
                        .param("brandId", brand.getId().toString())
                        .param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.totalElements").value(3))
                .andExpect(jsonPath("result.totalPages").value(2))
                .andExpect(jsonPath("result.content.length()").value(2));

        mvc.perform(get("/products")
                        .param("brandId", brand.getId().toString())
                        .param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.content.length()").value(1));
    }

    @Test
    void rejectsPriceMinGreaterThanPriceMax() throws Exception {
        mvc.perform(get("/products").param("priceMin", "500000").param("priceMax", "100000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("isSuccess").value(false))
                .andExpect(jsonPath("statusCode").value(400))
                .andExpect(jsonPath("message").value("priceMin không được lớn hơn priceMax."));
    }

    @Test
    void isAccessibleWithoutAuthentication() throws Exception {
        mvc.perform(get("/products"))
                .andExpect(status().isOk());
    }
}
