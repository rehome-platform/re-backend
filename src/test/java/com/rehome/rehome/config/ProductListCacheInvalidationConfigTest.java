package com.rehome.rehome.config;

import com.rehome.rehome.component.ProductListCache;
import com.rehome.rehome.entity.marketplace.Brand;
import com.rehome.rehome.repository.BrandRepository;
import com.rehome.rehome.repository.CategoryRepository;
import com.rehome.rehome.repository.ConsignmentReceiptRepository;
import com.rehome.rehome.repository.ProductPhotoRepository;
import com.rehome.rehome.repository.ProductRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import static org.mockito.Mockito.*;

/** Exercise the advisor against actual Spring Data repository proxies and transactions. */
@DataJpaTest
@Import(ProductListCacheInvalidationConfig.class)
class ProductListCacheInvalidationConfigTest {
    @MockBean ProductListCache cache;
    @Autowired ProductRepository products;
    @Autowired ProductPhotoRepository photos;
    @Autowired BrandRepository brands;
    @Autowired CategoryRepository categories;
    @Autowired ConsignmentReceiptRepository receipts;

    @Test void repositoryReadsDoNotInvalidatePages() {
        products.findById("NOT-FOUND");
        photos.findByProduct_IdOrderByIdAsc("NOT-FOUND");
        brands.findAll();
        verifyNoInteractions(cache);
    }

    @Test void writesAcrossAllCatalogRepositoriesScheduleInvalidation() {
        products.saveAll(List.of());
        photos.saveAll(List.of());
        brands.save(Brand.builder().name("Cache brand").build());
        categories.deleteAllByIdInBatch(List.of());
        receipts.saveAll(List.of());
        verify(cache, times(5)).invalidateAfterCommit();
    }

    @Test void bulkFeaturedUpdateAlsoSchedulesInvalidation() {
        products.clearFeaturedByMemberId(UUID.randomUUID());
        verify(cache).invalidateAfterCommit();
    }
}
