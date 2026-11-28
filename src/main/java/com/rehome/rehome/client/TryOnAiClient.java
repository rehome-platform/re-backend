package com.rehome.rehome.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rehome.rehome.entity.enums.TryOnModelType;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.service.TryOnImageGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.regex.Pattern;

@Slf4j
@Component
public class TryOnAiClient implements TryOnImageGenerator {

    private static final Pattern JOB_ID = Pattern.compile("^[0-9a-f]{32}$");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient client;

    @Autowired
    public TryOnAiClient(
            RestClient.Builder restClientBuilder,
            @Value("${tryon.ai.base-url:}") String baseUrl,
            @Value("${tryon.ai.api-key:}") String apiKey,
            @Value("${tryon.ai.connect-timeout-seconds:5}") int connectTimeoutSeconds,
            @Value("${tryon.ai.read-timeout-seconds:120}") int readTimeoutSeconds) {
        if (baseUrl == null || baseUrl.isBlank() || apiKey == null || apiKey.isBlank()) {
            log.warn(">>>> Chua cau hinh tryon.ai.base-url / tryon.ai.api-key: POST /tryon/images se tra 502");
            this.client = null;
            return;
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        this.client = restClientBuilder
                .requestFactory(factory)
                .baseUrl(baseUrl.replaceAll("/+$", ""))
                .defaultHeader("X-API-Key", apiKey)
                .build();
    }

    TryOnAiClient(RestClient client) {
        this.client = client;
    }

    @Override
    public byte[] generateOutfit(TryOnModelType modelType, byte[] topImage, byte[] bottomImage) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("model_id", modelId(modelType));
        body.add("top", filePart("top.png", topImage));
        body.add("bottom", filePart("bottom.png", bottomImage));
        return generate("/try-on/preset", body);
    }

    @Override
    public byte[] generateDress(TryOnModelType modelType, byte[] dressImage) {
        if (modelType != TryOnModelType.FEMALE) {
            throw new AppException(ErrorCode.DRESS_NOT_SUPPORTED_FOR_MALE);
        }
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("model_id", "female");
        body.add("dress", filePart("dress.png", dressImage));
        return generate("/try-on/preset/dress", body);
    }

    private byte[] generate(String path, MultiValueMap<String, Object> body) {
        if (client == null) {
            log.error(">>>> Sinh anh thu do that bai: chua cau hinh AI server");
            throw failed();
        }
        try {
            ResponseEntity<String> res = client.post()
                    .uri(path)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .toEntity(String.class);

            String raw = res.getBody();
            log.info(">>>> AI {} -> status={}, contentType={}, body={}",
                    path, res.getStatusCode(), res.getHeaders().getContentType(),
                    raw == null ? null : raw.substring(0, Math.min(raw.length(), 500)));

            JsonNode json = raw == null ? null : MAPPER.readTree(raw);

            String jobId = json == null ? null : json.path("id").asText(null);
            if (jobId == null || !JOB_ID.matcher(jobId).matches()) {
                throw new IllegalStateException("AI tra response khong co id hop le");
            }
            boolean savedOnAiSide = json.hasNonNull("result_url");

            byte[] png = client.get()
                    .uri("/results/{id}/result.png", jobId)
                    .retrieve()
                    .body(byte[].class);
            if (png == null || png.length == 0) {
                throw new IllegalStateException("AI tra anh ket qua rong: id=" + jobId);
            }

            if (savedOnAiSide) {
                removeAiHistory(jobId);
            }
            return png;
        } catch (AppException e) {
            throw e;
        } catch (RuntimeException | JsonProcessingException e) {
            log.error(">>>> Sinh anh thu do that bai ({})", path, e);
            throw failed();
        }
    }

    private void removeAiHistory(String jobId) {
        try {
            client.delete().uri("/history/{id}", jobId).retrieve().toBodilessEntity();
        } catch (RuntimeException e) {
            log.warn(">>>> Khong xoa duoc ban sao tren AI server: id={}: {}", jobId, e.toString());
        }
    }

    private static String modelId(TryOnModelType modelType) {
        return modelType == TryOnModelType.MALE ? "male" : "female";
    }

    private static HttpEntity<ByteArrayResource> filePart(String filename, byte[] content) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        ByteArrayResource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        return new HttpEntity<>(resource, headers);
    }

    private static AppException failed() {
        return new AppException(ErrorCode.TRYON_GENERATION_FAILED);
    }
}