package com.rehome.rehome.initializer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ConsignmentFormStatus;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.marketplace.*;
import com.rehome.rehome.repository.*;
import com.rehome.rehome.service.EndOfTermDecisionService;
import com.rehome.rehome.service.FileStorageService;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.initializer.seed-enabled", havingValue = "true")
public class ProductInitializer implements CommandLineRunner {

    private final ProductRepository productRepository;
    private final AccountRepository accountRepository;
    private final WarehouseRepository warehouseRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ConsignmentFormRepository consignmentFormRepository;
    private final ConsignmentReceiptRepository consignmentReceiptRepository;
    private final EndOfTermDecisionService endOfTermDecisionService;
    private final ProductPhotoRepository productPhotoRepository;
    private final FileStorageService fileStorageService;

    // Mặc định TẮT để môi trường thật (Azure) không tự mọc 2 sản phẩm giả trên sàn.
    // Bật khi chạy local: biến môi trường APP_SEED_TRYON_PRODUCTS=true
//    @Value("${app.seed.tryon-products:false}")
//    private boolean tryOnSeedEnabled;

    // Tiền tố câu chung chung cũ, dữ liệu nào còn dùng câu này sẽ được thay bằng tình trạng thật
    private static final String GENERIC_CONDITION_PREFIX = "Hàng đã qua kiểm định";

    // id, tên, size, giá thỏa thuận, giá hiện tại, status, kỳ hiện tại, số ngày đã chạy, tình trạng
    private record Seed(String id, String name, String size, String agreedPrice, String price,
                        ProductStatus status, short term, int daysIntoTerm, String conditionNote) {}

    private static final List<Seed> SEEDS = List.of(
            new Seed("Q7-AK-000845", "Áo sơ mi Zara trắng", "M", "250000", "220000", ProductStatus.ON_SALE, (short) 1, 5, "Như mới, không lỗi"),
            new Seed("Q7-AK-000846", "Áo thun Nike đen", "L", "180000", "150000", ProductStatus.RESERVED, (short) 1, 10, "Đã mặc vài lần, màu còn đẹp, không xù lông"),
            new Seed("Q7-AK-000847", "Quần jean Levi's", "32", "350000", "300000", ProductStatus.SOLD, (short) 2, 40, "Tốt, form đứng, đường chỉ nguyên vẹn, không rách"),
            new Seed("Q7-QU-000848", "Quần kaki Uniqlo", "30", "200000", "180000", ProductStatus.WITHDRAWN, (short) 1, 3, "Còn 90%, có một vết ố nhỏ ở gấu quần"),
            new Seed("Q7-AK-000849", "Áo khoác Adidas", "XL", "450000", "400000", ProductStatus.ON_SALE, (short) 3, 89, "Còn 85%, khóa kéo mượt, bo tay hơi giãn nhẹ"),
            new Seed("Q7-AK-000850", "Áo len H&M", "M", "150000", "120000", ProductStatus.UNCLAIMED, (short) 3, 90, "Còn 80%, xù lông nhẹ ở vùng nách"),
            new Seed("Q7-QU-000851", "Quần short Puma", "L", "100000", "90000", ProductStatus.DONATED, (short) 3, 90, "Như mới, còn nguyên mác")
    );

    // Sản phẩm mẫu để test thử đồ AI (API 97): đang bán, đúng slot (Áo sơ mi = TOP, Quần tây = BOTTOM), có ảnh thật.
    // Ảnh nằm trong src/main/resources/seed/tryon/ và được đẩy qua FileStorageService nên chạy được cả provider
    // local lẫn cloudinary (URL do chính storage sinh ra, nên loadByUrl đọc lại được để gửi sang AI).
    private record TryOnSeed(String id, String name, String size, String agreedPrice, String price,
                             String categoryName, String conditionNote, String photoResource, String photoPath,
                             String receiptNo) {}

    private static final List<TryOnSeed> TRYON_SEEDS = List.of(
            new TryOnSeed("Q7-AK-000852", "Áo sơ mi Oxford xanh nhạt tay ngắn", "L", "200000", "180000",
                    "Áo sơ mi", "Như mới, không lỗi", "seed/tryon/top.png", "products/seed-tryon-top.png",
                    "KG-SEED-TRYON-01"),
            new TryOnSeed("Q7-QU-000853", "Quần tây đen ống đứng", "32", "250000", "220000",
                    "Quần tây", "Như mới, còn nguyên form", "seed/tryon/bottom.png", "products/seed-tryon-bottom.png",
                    "KG-SEED-TRYON-02")
    );

    @Override
    @Transactional
    public void run(String... args) {
        log.info("ProductInitializer: bắt đầu chạy");
        repairGenericConditionNotes();
        if (productRepository.count() > 0) {
            log.info("ProductInitializer: đã có sản phẩm, bỏ qua seed chung.");
            seedTryOnProducts();
            return;
        }

        Account member = accountRepository.findFirstByRole(Role.MODERATOR).orElse(null);
        Warehouse warehouse = warehouseRepository.findAll().stream().findFirst().orElse(null);
        Category category = categoryRepository.findAll().stream().findFirst().orElse(null);
        Brand brand = brandRepository.findAll().stream().findFirst().orElse(null);
        if (member == null || warehouse == null || category == null || brand == null) {
            log.warn("ProductInitializer: thiếu Moderator/Warehouse/Category/Brand, bỏ qua.");
            return;
        }

        Instant now = Instant.now();

        for (int i = 0; i < SEEDS.size(); i++) {
            Seed s = SEEDS.get(i);
            Instant termStartedAt = now.minus(s.daysIntoTerm(), ChronoUnit.DAYS);
            BigDecimal agreed = new BigDecimal(s.agreedPrice());
            int no = 845 + i;

            ConsignmentForm form = consignmentFormRepository.save(ConsignmentForm.builder()
                    .member(member)
                    .warehouse(warehouse)
                    .category(category)
                    .brand(brand)
                    .productName(s.name())
                    .description(s.conditionNote())
                    .desiredPrice(agreed)
                    .status(ConsignmentFormStatus.APPROVED)
                    .bringInDeadline(LocalDate.ofInstant(termStartedAt, ZoneOffset.UTC))
                    .build());

            ConsignmentReceipt receipt = consignmentReceiptRepository.save(ConsignmentReceipt.builder()
                    .receiptNo("KG-2026-" + String.format("%06d", no))
                    .consignmentForm(form)
                    .member(member)
                    .moderator(member)
                    .warehouse(warehouse)
                    .agreedPrice(agreed)
                    .floorPrice(agreed.multiply(new BigDecimal("0.5")))
                    .withdrawalFee(new BigDecimal("20000"))
                    .confirmedAt(termStartedAt)
                    .build());

            Product product = productRepository.save(Product.builder()
                    .id(s.id())
                    .consignmentReceipt(receipt)
                    .member(member)
                    .warehouse(warehouse)
                    .category(category)
                    .brand(brand)
                    .name(s.name())
                    .size(s.size())
                    .conditionNote(s.conditionNote())
                    .currentPrice(new BigDecimal(s.price()))
                    .status(s.status())
                    .currentTerm(s.term())
                    .termStartedAt(termStartedAt)
                    .endOfTermDeadline(termStartedAt.plus(30, ChronoUnit.DAYS))
                    .build());

            // Sản phẩm đang chờ Member chọn cách xử lý cuối kỳ (decision = null)
            if ("Q7-AK-000849".equals(s.id())) {
                endOfTermDecisionService.createDecision(product, now);
            }
        }
        log.info("ProductInitializer: đã tạo {} sản phẩm mẫu.", SEEDS.size());
        seedTryOnProducts();
    }

    // Idempotent: chạy lại bao nhiêu lần cũng được. Thiếu sản phẩm thì tạo, thiếu ảnh thì bổ sung ảnh.
    // Không đụng tới sản phẩm đã có (kể cả khi bạn đã đổi trạng thái/giá bằng tay).
    private void seedTryOnProducts() {
        Account member = accountRepository.findFirstByRole(Role.MODERATOR).orElse(null);
        Warehouse warehouse = warehouseRepository.findAll().stream().findFirst().orElse(null);
        Brand brand = brandRepository.findAll().stream().findFirst().orElse(null);
        if (member == null || warehouse == null || brand == null) {
            log.warn("ProductInitializer: thiếu Moderator/Warehouse/Brand, bỏ qua sản phẩm thử đồ.");
            return;
        }

        Instant now = Instant.now();
        for (int i = 0; i < TRYON_SEEDS.size(); i++) {
            TryOnSeed s = TRYON_SEEDS.get(i);

            Product product = productRepository.findById(s.id()).orElse(null);
            if (product == null) {
                // Không có danh mục đúng tên thì tự tạo, để sản phẩm chắc chắn được thêm
                Category category = categoryRepository.findAll().stream()
                        .filter(c -> s.categoryName().equals(c.getName()))
                        .findFirst()
                        .orElseGet(() -> categoryRepository.save(
                                Category.builder().name(s.categoryName()).build()));
                product = createTryOnProduct(s, i, now, member, warehouse, category, brand);
            }

            if (productPhotoRepository.findByProduct_IdOrderByDisplayOrderAscIdAsc(s.id()).isEmpty()) {
                addTryOnPhoto(product, s);
            }
        }
    }

    private Product createTryOnProduct(TryOnSeed s, int index, Instant now, Account member, Warehouse warehouse,
                                       Category category, Brand brand) {
        Instant termStartedAt = now.minus(2, ChronoUnit.DAYS);
        BigDecimal agreed = new BigDecimal(s.agreedPrice());

        ConsignmentForm form = consignmentFormRepository.save(ConsignmentForm.builder()
                .member(member)
                .warehouse(warehouse)
                .category(category)
                .brand(brand)
                .productName(s.name())
                .description(s.conditionNote())
                .desiredPrice(agreed)
                .status(ConsignmentFormStatus.APPROVED)
                .bringInDeadline(LocalDate.ofInstant(termStartedAt, ZoneOffset.UTC))
                .build());

        // receiptNo cố ý dùng tiền tố KG-SEED- để không đụng dãy KG-<năm>-<seq> của phiếu thật (cột unique)
        ConsignmentReceipt receipt = consignmentReceiptRepository.save(ConsignmentReceipt.builder()
                .receiptNo(s.receiptNo())
                .consignmentForm(form)
                .member(member)
                .moderator(member)
                .warehouse(warehouse)
                .agreedPrice(agreed)
                .floorPrice(agreed.multiply(new BigDecimal("0.5")))
                .withdrawalFee(new BigDecimal("20000"))
                .confirmedAt(termStartedAt)
                .build());

        Product product = productRepository.save(Product.builder()
                .id(s.id())
                .consignmentReceipt(receipt)
                .member(member)
                .warehouse(warehouse)
                .category(category)
                .brand(brand)
                .name(s.name())
                .size(s.size())
                .conditionNote(s.conditionNote())
                .currentPrice(new BigDecimal(s.price()))
                .status(ProductStatus.ON_SALE)
                .currentTerm((short) 1)
                .termStartedAt(termStartedAt)
                .endOfTermDeadline(termStartedAt.plus(30, ChronoUnit.DAYS))
                .build());
        log.info("ProductInitializer: đã tạo sản phẩm thử đồ {}.", s.id());
        return product;
    }

    // Lỗi đọc ảnh/storage chỉ ghi log, không làm app không khởi động được
    private void addTryOnPhoto(Product product, TryOnSeed s) {
        try {
            byte[] bytes = new ClassPathResource(s.photoResource()).getContentAsByteArray();
            String url = fileStorageService.store(s.photoPath(), bytes, "image/png");
            productPhotoRepository.save(ProductPhoto.builder()
                    .product(product)
                    .imageUrl(url)
                    .isBackgroundRemoved(false)
                    .displayOrder(0)
                    .build());
            log.info("ProductInitializer: đã thêm ảnh cho {}: {}", s.id(), url);
        } catch (IOException | RuntimeException e) {
            log.warn("ProductInitializer: không thêm được ảnh cho {}: {}", s.id(), e.toString());
        }
    }

    // DB đã có sẵn sản phẩm mẫu từ lần chạy trước nên khối seed bị bỏ qua; sửa riêng các sản phẩm mẫu
    // còn mang câu chung chung "Hàng đã qua kiểm định…". Sản phẩm thật do Moderator nhập không bị đụng tới.
    private void repairGenericConditionNotes() {
        for (Seed s : SEEDS) {
            productRepository.findById(s.id())
                    .filter(p -> p.getConditionNote() != null
                            && p.getConditionNote().startsWith(GENERIC_CONDITION_PREFIX))
                    .ifPresent(p -> {
                        p.setConditionNote(s.conditionNote());
                        productRepository.save(p);
                        log.info("ProductInitializer: đã sửa tình trạng của {}.", p.getId());
                    });
        }
    }
}