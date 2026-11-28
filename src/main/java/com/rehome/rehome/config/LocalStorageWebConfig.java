package com.rehome.rehome.config;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalStorageWebConfig implements WebMvcConfigurer {

    private final String localDir;

    public LocalStorageWebConfig(@Value("${app.storage.local-dir:uploads}") String localDir) {
        this.localDir = localDir;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(localDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/files/**").addResourceLocations(location);
    }
}
