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

/**
 * Client gọi AI server (CatVTON + AutoMasker, FastAPI) để sinh ảnh thử đồ - F-078.
 *
 * <p>Luồng một lần sinh ảnh:
 * <ol>
 *   <li>{@code POST /try-on/preset} (áo + quần) hoặc {@code POST /try-on/preset/dress} (đầm), multipart,
 *       người mẫu male/female nằm sẵn trên AI server. Chạy ĐỒNG BỘ, vài chục giây.</li>
 *   <li>Tải bytes ảnh kết quả qua {@code GET /results/{id}/result.png} (kèm API key). Backend tự lưu ảnh
 *       vào storage của mình, KHÔNG dùng {@code result_url} (link Cloudinary của AI) vì F-081 xóa ảnh bằng
 *       {@code deleteByUrl}, mà storage chỉ nhận URL do chính nó sinh ra.</li>
 *   <li>Nếu AI đã tự upload bản của nó ({@code result_url != null}) thì {@code DELETE /history/{id}} để
 *       không giữ ảnh của Member ở hai nơi. Bước này best-effort, lỗi chỉ ghi log.</li>
 * </ol>
 *
 * <p>Mọi lỗi (chưa cấu hình, timeout, 409 GPU bận, 422 mask lỗi, 500 model lỗi, response sai...) đều thành
 * {@link ErrorCode#TRYON_GENERATION_FAILED} (502).
 */
@Slf4j
@Component
public class TryOnAiClient implements TryOnImageGenerator {

    // Cùng regex với AI server (JOB_RE trong api.py): chỉ nhận id do AI sinh, không ghép chuỗi lạ vào URL
    private static final Pattern JOB_ID = Pattern.compile("^[0-9a-f]{32}$");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // null khi chưa cấu hình tryon.ai.base-url / tryon.ai.api-key -> mọi lần gọi trả 502
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
        // Read timeout phải đủ dài: AI chạy đồng bộ nhiều bước diffusion
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        this.client = restClientBuilder
                .requestFactory(factory)
                .baseUrl(baseUrl.replaceAll("/+$", ""))
                .defaultHeader("X-API-Key", apiKey)
                .build();
    }

    // Dùng cho test: truyền RestClient đã gắn MockRestServiceServer
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
        // AI chỉ có mẫu nữ cho đầm; service đã chặn trước, đây là lớp phòng thủ
        if (modelType != TryOnModelType.FEMALE) {
            throw new AppException(ErrorCode.DRESS_NOT_SUPPORTED_FOR_MALE);
        }
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("model_id", "female");
        body.add("dress", filePart("dress.png", dressImage));
        return generate("/try-on/preset/dress", body);
    }

    // ---------------------------------------------------------------- nội bộ

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
            log.error(">>>> Sinh anh thu do that bai ({})", path, e);   // e là tham số cuối để in stack trace
            throw failed();
        }
    }

    // Best-effort: ảnh đã nằm trong storage của backend, bản trên AI chỉ là dư thừa
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

    // Part có filename để FastAPI nhận là UploadFile; MIME để octet-stream vì không biết chắc định dạng gốc
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