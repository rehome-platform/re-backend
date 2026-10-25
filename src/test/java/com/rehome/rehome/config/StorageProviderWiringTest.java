package com.rehome.rehome.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.rehome.rehome.service.FileStorageService;
import com.rehome.rehome.serviceImpl.CloudinaryFileStorageServiceImpl;
import com.rehome.rehome.serviceImpl.LocalFileStorageServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

/**
 * Hai bean cùng implement {@link FileStorageService}, phân biệt bằng
 * {@code app.storage.provider}. Nếu điều kiện sai thì context chết vì trùng bean
 * (hoặc vì không có bean nào) - test này đăng ký đúng các class thật để kiểm tra điều đó.
 */
class StorageProviderWiringTest {

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                // Cần để resolve @Value trên constructor của các impl
                .withBean(PropertySourcesPlaceholderConfigurer.class)
                .withUserConfiguration(
                        CloudinaryConfig.class,
                        LocalFileStorageServiceImpl.class,
                        CloudinaryFileStorageServiceImpl.class);
    }

    @Test
    void noProviderConfigured_usesLocalStorage() {
        runner().run(context -> assertThat(context)
                .hasSingleBean(FileStorageService.class)
                .getBean(FileStorageService.class)
                .isInstanceOf(LocalFileStorageServiceImpl.class));
    }

    @Test
    void providerLocal_usesLocalStorage() {
        runner().withPropertyValues("app.storage.provider=local")
                .run(context -> assertThat(context)
                        .hasSingleBean(FileStorageService.class)
                        .getBean(FileStorageService.class)
                        .isInstanceOf(LocalFileStorageServiceImpl.class));
    }

    @Test
    void providerLocal_doesNotRequireCloudinaryCredentials() {
        runner().withPropertyValues("app.storage.provider=local")
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .doesNotHaveBean(com.cloudinary.Cloudinary.class));
    }

    @Test
    void providerCloudinary_usesCloudinaryStorage() {
        runner().withPropertyValues(
                        "app.storage.provider=cloudinary",
                        "cloudinary.cloud-name=demo",
                        "cloudinary.api-key=123456789",
                        "cloudinary.api-secret=test-secret")
                .run(context -> assertThat(context)
                        .hasSingleBean(FileStorageService.class)
                        .getBean(FileStorageService.class)
                        .isInstanceOf(CloudinaryFileStorageServiceImpl.class));
    }

    /** provider lạ -> không có bean nào, app sẽ chết lúc startup chứ không chạy với storage sai. */
    @Test
    void providerUnknown_registersNoStorageBean() {
        runner().withPropertyValues("app.storage.provider=s3")
                .run(context -> assertThat(context).doesNotHaveBean(FileStorageService.class));
    }
}
