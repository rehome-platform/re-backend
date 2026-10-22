package com.rehome.rehome.initializer;

import com.rehome.rehome.config.CodeGenerator;
import com.rehome.rehome.entity.enums.CatalogStatus;
import com.rehome.rehome.entity.enums.TryOnSlot;
import com.rehome.rehome.entity.marketplace.Category;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.repository.CategoryRepository;
import com.rehome.rehome.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class CategoryInitializer implements ApplicationRunner {

    private final CategoryRepository categoryRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Map<String, TryOnSlot> seeds = new LinkedHashMap<>();

        // TOP
        seeds.put("Áo thun", TryOnSlot.TOP);
        seeds.put("Áo sơ mi", TryOnSlot.TOP);
        seeds.put("Áo polo", TryOnSlot.TOP);
        seeds.put("Áo len", TryOnSlot.TOP);
        seeds.put("Áo hoodie / Áo nỉ", TryOnSlot.TOP);
        seeds.put("Áo khoác", TryOnSlot.TOP);
        seeds.put("Áo blazer", TryOnSlot.TOP);
        seeds.put("Áo kiểu", TryOnSlot.TOP);

        // BOTTOM
        seeds.put("Quần jeans", TryOnSlot.BOTTOM);
        seeds.put("Quần tây", TryOnSlot.BOTTOM);
        seeds.put("Quần kaki", TryOnSlot.BOTTOM);
        seeds.put("Quần short", TryOnSlot.BOTTOM);
        seeds.put("Quần jogger / Quần thể thao", TryOnSlot.BOTTOM);
        seeds.put("Chân váy", TryOnSlot.BOTTOM);

        // DRESS
        seeds.put("Đầm", TryOnSlot.DRESS);
        seeds.put("Váy liền", TryOnSlot.DRESS);
        seeds.put("Jumpsuit", TryOnSlot.DRESS);
        seeds.put("Áo dài", TryOnSlot.DRESS);

        Set<String> existing = categoryRepository.findAll().stream()
                .map(c -> c.getName().trim().toLowerCase())
                .collect(Collectors.toSet());

        List<Category> toInsert = seeds.entrySet().stream()
                .filter(e -> !existing.contains(e.getKey().toLowerCase()))
                .map(e -> Category.builder()
                        .name(e.getKey())
                        .tryonSlot(e.getValue())
                        .status(CatalogStatus.ACTIVE)
                        .build())
                .toList();

        if (toInsert.isEmpty()) {
            log.info("[CategoryInitializer] Danh mục đã đầy đủ, bỏ qua.");
            return;
        }

        categoryRepository.saveAll(toInsert);
        log.info("[CategoryInitializer] Đã thêm {} danh mục.", toInsert.size());
    }

    @Component
    @RequiredArgsConstructor
    public class CodeBackfillRunner implements CommandLineRunner {

        private final WarehouseRepository warehouseRepository;
        private final CategoryRepository categoryRepository;

        @Override
        @Transactional
        public void run(String... args) {
            // Dùng native query vì entity đã khai báo code NOT NULL;
            // nếu findAll() lỗi do code null thì chuyển sang JdbcTemplate.
            for (Warehouse w : warehouseRepository.findAll()) {
                if (w.getCode() == null) {
                    w.setCode(CodeGenerator.makeUnique(
                            CodeGenerator.fromName(w.getName(), "kho"),
                            warehouseRepository::existsByCode));
                    warehouseRepository.saveAndFlush(w);
                }
            }
            for (Category c : categoryRepository.findAll()) {
                if (c.getCode() == null) {
                    c.setCode(CodeGenerator.makeUnique(
                            CodeGenerator.fromName(c.getName()),
                            categoryRepository::existsByCode));
                    categoryRepository.saveAndFlush(c);
                }
            }
        }
    }
}