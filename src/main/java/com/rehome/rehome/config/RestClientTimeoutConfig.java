package com.rehome.rehome.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RestClientTimeoutConfig {

    @Bean
    RestClientCustomizer restClientTimeoutCustomizer(
            @Value("${app.rest-client.connect-timeout:5s}") String connectTimeout,
            @Value("${app.rest-client.read-timeout:15s}") String readTimeout) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(DurationStyle.detectAndParse(connectTimeout))
                .withReadTimeout(DurationStyle.detectAndParse(readTimeout));
        return builder -> builder.requestFactory(ClientHttpRequestFactories.get(settings));
    }
}
