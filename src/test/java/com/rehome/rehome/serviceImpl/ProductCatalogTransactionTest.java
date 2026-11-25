package com.rehome.rehome.serviceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rehome.rehome.component.ProductListCache;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.repository.ProductPhotoRepository;
import com.rehome.rehome.service.ProductCatalogReader;
import com.rehome.rehome.service.ProductService;
import javax.sql.DataSource;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.*;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Actual Spring transactions: no JDBC resource is held across Redis calls, on miss or hit. */
@SpringJUnitConfig(ProductCatalogTransactionTest.Config.class)
class ProductCatalogTransactionTest {
    @Autowired ProductService service;
    @Autowired ProductRepository products;
    @Autowired ProductPhotoRepository photos;
    @Autowired DataSource dataSource;
    @Autowired ValueOperations<String, String> values;

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() { return new DriverManagerDataSource("jdbc:h2:mem:catalog_tx;DB_CLOSE_DELAY=-1", "sa", ""); }
        @Bean PlatformTransactionManager transactions(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean ProductRepository products() { return mock(ProductRepository.class); }
        @Bean ProductPhotoRepository photos() { return mock(ProductPhotoRepository.class); }
        @Bean ValueOperations<String, String> values() { return mock(ValueOperations.class); }
        @Bean ProductListCache cache(ValueOperations<String, String> values) {
            StringRedisTemplate redis = mock(StringRedisTemplate.class);
            when(redis.opsForValue()).thenReturn(values);
            return new ProductListCache(redis, new ObjectMapper(), true, "30s");
        }
        @Bean ProductCatalogReader reader(ProductRepository products, ProductPhotoRepository photos) {
            return new ProductCatalogReader(products, photos);
        }
        @Bean ProductServiceImpl productService(ProductListCache cache, ProductCatalogReader reader) {
            // Supply unrelated business collaborators as mocks; exercise the real public facade.
            var constructor = ProductServiceImpl.class.getConstructors()[0];
            Object[] args = Arrays.stream(constructor.getParameterTypes()).map(type ->
                    type == ProductListCache.class ? cache : type == ProductCatalogReader.class ? reader : mock(type)).toArray();
            return (ProductServiceImpl) org.springframework.beans.BeanUtils.instantiateClass(constructor, args);
        }
    }

    @Test void cacheIoRunsWithoutTransactionAndReaderMapsInsideTransaction() {
        Map<String, String> stored = new HashMap<>();
        when(values.get(anyString())).thenAnswer(inv -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(TransactionSynchronizationManager.hasResource(dataSource)).isFalse();
            return stored.get(inv.getArgument(0));
        });
        doAnswer(inv -> {
            assertThat(TransactionSynchronizationManager.hasResource(dataSource)).isFalse();
            stored.put(inv.getArgument(0), inv.getArgument(1)); return null;
        }).when(values).set(anyString(), anyString(), any(java.time.Duration.class));
        when(products.findAll(org.mockito.ArgumentMatchers.<Specification<com.rehome.rehome.entity.marketplace.Product>>any(), any(Pageable.class)))
                .thenAnswer(inv -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                    assertThat(TransactionSynchronizationManager.hasResource(dataSource)).isTrue();
                    return Page.empty();
                });
        var first = service.getProducts(null, null, null, null, null, null, null, 0, 20, null);
        var second = service.getProducts(null, null, null, null, null, null, null, 0, 20, null);
        assertThat(second).usingRecursiveComparison().isEqualTo(first);
        verify(products, times(1)).findAll(org.mockito.ArgumentMatchers.<Specification<com.rehome.rehome.entity.marketplace.Product>>any(), any(Pageable.class));
        verifyNoInteractions(photos);
    }
}
