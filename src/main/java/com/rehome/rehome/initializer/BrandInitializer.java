package com.rehome.rehome.initializer;

import com.rehome.rehome.entity.enums.CatalogStatus;
import com.rehome.rehome.entity.marketplace.Brand;
import com.rehome.rehome.repository.BrandRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class BrandInitializer implements ApplicationRunner {

    private final BrandRepository brandRepository;

    private static final List<String> SEED_BRANDS = List.of(
            "Uniqlo", "Zara", "H&M", "Mango", "Levi's",
            "Nike", "Adidas", "Puma", "Converse", "Champion",
            "Routine", "Coolmate", "Canifa", "Owen", "Yame",
            "IVY moda", "Elise", "The Blue Tshirt", "Biluxury", "Khác"
    );

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Set<String> existing = brandRepository.findAll().stream()
                .map(b -> b.getName().trim().toLowerCase())
                .collect(Collectors.toSet());

        List<Brand> toInsert = SEED_BRANDS.stream()
                .filter(name -> !existing.contains(name.toLowerCase()))
                .map(name -> Brand.builder()
                        .name(name)
                        .status(CatalogStatus.ACTIVE)
                        .build())
                .toList();

        if (toInsert.isEmpty()) {
            log.info("[BrandInitializer] Thương hiệu đã đầy đủ, bỏ qua.");
            return;
        }

        brandRepository.saveAll(toInsert);
        log.info("[BrandInitializer] Đã thêm {} thương hiệu.", toInsert.size());
    }
}