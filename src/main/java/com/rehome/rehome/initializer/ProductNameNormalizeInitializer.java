package com.rehome.rehome.initializer;

import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.util.VietnameseText;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Điền name_normalized cho sản phẩm đã có trước khi cột này ra đời: ddl-auto chỉ thêm cột
// với giá trị null, mà null thì không khớp LIKE nào nên hàng cũ sẽ biến mất khỏi kết quả tìm kiếm.
@Slf4j
@Component
@Order(101)
@RequiredArgsConstructor
public class ProductNameNormalizeInitializer implements ApplicationRunner {

    private final ProductRepository productRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Product> pending = productRepository.findAllByNameNormalizedIsNull();

        if (pending.isEmpty()) {
            log.info("[ProductNameNormalizeInitializer] Mọi sản phẩm đã có tên không dấu, bỏ qua.");
            return;
        }

        // Gán tay thay vì dựa vào @PreUpdate: entity không đổi gì thì Hibernate không phát UPDATE
        // và callback sẽ không chạy.
        for (Product product : pending) {
            product.setNameNormalized(VietnameseText.normalize(product.getName()));
        }
        productRepository.saveAll(pending);

        log.info("[ProductNameNormalizeInitializer] Đã chuẩn hóa tên cho {} sản phẩm.", pending.size());
    }
}
