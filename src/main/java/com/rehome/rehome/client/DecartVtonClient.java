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

/**
 * Client mint "client token" ngắn hạn của Decart (Lucy VTON realtime) - xem docs
 * {@code POST https://api.decart.ai/v1/client/tokens}, header {@code x-api-key} là permanent key.
 *
 * <p>Permanent key CHỈ nằm ở backend (decart.api.key). Token trả về cho mobile là
 * {@code apiKey} ngắn hạn (field {@link TokenResult#apiKey()}), mobile truyền thẳng cho Decart
 * Web SDK trong WebView - không bao giờ thấy permanent key.
 *
 * <p>Hai field chặn chi phí quan trọng nhất trong request:
 * <ul>
 *   <li>{@code allowedModels}: token chỉ mở khoá đúng model này, kể cả nếu request bị can thiệp.</li>
 *   <li>{@code constraints.realtime.maxSessionDuration}: Decart tự cắt phiên phía server họ sau
 *   đúng N giây, không phụ thuộc client có tôn trọng countdown trên app hay không.</li>
 * </ul>
 */
@Slf4j
@Component
public class DecartVtonClient {

    // null khi chưa cấu hình decart.api.key -> mọi lần gọi trả 502 TRYON_LIVE_TOKEN_FAILED
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
        // Mint token phải nhanh (chỉ là 1 call REST ngắn) - timeout thấp để mobile không đứng
        // ở màn "Đang kết nối AI..." quá lâu khi Decart chậm/không phản hồi.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        this.client = restClientBuilder
                .requestFactory(factory)
                .baseUrl(baseUrl.replaceAll("/+$", ""))
                .defaultHeader("x-api-key", apiKey)
                .build();
    }

    // Dùng cho test: truyền RestClient đã gắn MockRestServiceServer
    DecartVtonClient(RestClient client) {
        this.client = client;
    }

    /**
     * @param modelId           ví dụ "lucy-vton-3.5" - ghim version cụ thể, không dùng alias "latest"
     * @param tokenTtlSeconds   thời gian token còn hiệu lực để MỞ kết nối (1-3600s theo Decart)
     * @param maxSessionSeconds Decart tự cắt phiên sau N giây kể cả token chưa hết hạn (tối thiểu 10s)
     */
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
            // RestClientResponseException (401 sai key, 422 validation), ResourceAccessException (mạng/timeout)...
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
