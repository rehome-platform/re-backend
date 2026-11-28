package com.rehome.rehome.config;

import com.rehome.rehome.component.ProductListCache;
import java.util.Set;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ProductListCacheInvalidationConfig {
    private static final Set<String> REPOSITORIES = Set.of("productRepository", "productPhotoRepository",
            "brandRepository", "categoryRepository", "consignmentReceiptRepository");

    @Bean
    static BeanPostProcessor productListCacheInvalidation(ObjectProvider<ProductListCache> cache) {
        return new BeanPostProcessor() {
            @Override public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (REPOSITORIES.contains(beanName) && bean instanceof Advised advised) {
                    advised.addAdvice(0, (MethodInterceptor) invocation -> {
                        Object result = invocation.proceed();
                        String method = invocation.getMethod().getName();
                        if (method.startsWith("save") || method.startsWith("delete")
                                || method.equals("clearFeaturedByMemberId")) {
                            cache.getObject().invalidateAfterCommit();
                        }
                        return result;
                    });
                }
                return bean;
            }
        };
    }
}
