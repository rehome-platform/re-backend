package com.rehome.rehome.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class DecartVtonClient {

    private final RestClient client;

    @Autowired
    public DecartVtonClient(
            RestClient.Builder restClientBuilder,
            @Value("${decart.api.base-url:https://api.decart.ai}") String baseUrl,
            @Value("${decart.api.key:}") String apiKey,
            @Value("${decart.api.connect-timeout-seconds:5}") int connectTimeoutSeconds,
            @Value("${decart.api.read-timeout-seconds:10}") int readTimeoutSeconds) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn(">>>> Chua cau hinh decart.api.key: POST /tryon/live/sessions se tra 502");
            this.client = null;
            return;
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        this.client = restClientBuilder
                .requestFactory(factory)
                .baseUrl(baseUrl.replaceAll("/+$", ""))
                .defaultHeader("x-api-key", apiKey)
                .build();
    }

    DecartVtonClient(RestClient client) {
        this.client = client;
    }

    public TokenResult mintClientToken(String modelId, int tokenTtlSeconds, int maxSessionSeconds) {
        if (client == null) {
            log.error(">>>> Mint Decart client token that bai: chua cau hinh decart.api.key");
            throw failed();
        }
        try {
            TokenRequestBody body = TokenRequestBody.builder()
                    .expiresIn(tokenTtlSeconds)
                    .allowedModels(new String[]{modelId})
                    .constraints(new Constraints(new RealtimeConstraints(maxSessionSeconds)))
                    .build();

            JsonNode json = client.post()
                    .uri("/v1/client/tokens")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            String token = json == null ? null : json.path("apiKey").asText(null);
            String expiresAtRaw = json == null ? null : json.path("expiresAt").asText(null);
            if (token == null || token.isBlank() || expiresAtRaw == null) {
                throw new IllegalStateException("Decart tra response khong co apiKey/expiresAt hop le");
            }
            return new TokenResult(token, Instant.parse(expiresAtRaw));
        } catch (AppException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error(">>>> Mint Decart client token that bai: {}", e.toString());
            throw failed();
        }
    }

    private static AppException failed() {
        return new AppException(ErrorCode.TRYON_LIVE_TOKEN_FAILED);
    }

    public record TokenResult(String apiKey, Instant expiresAt) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Builder
    @Getter
    private static class TokenRequestBody {
        private final Integer expiresIn;
        private final String[] allowedModels;
        private final Constraints constraints;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record Constraints(RealtimeConstraints realtime) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record RealtimeConstraints(Integer maxSessionDuration) {
    }
}
