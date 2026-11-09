package com.rehome.rehome.serviceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rehome.rehome.component.AuditLogger;
import com.rehome.rehome.constant.SystemConfigCatalog;
import com.rehome.rehome.constant.SystemConfigCatalog.Spec;
import com.rehome.rehome.dto.response.SystemConfigItemResponse;
import com.rehome.rehome.dto.response.SystemConfigResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.marketplace.SystemConfig;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.SystemConfigRepository;
import com.rehome.rehome.service.SystemConfigAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SystemConfigAdminServiceImpl implements SystemConfigAdminService {

    private final SystemConfigRepository systemConfigRepository;
    private final AuditLogger auditLogger;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<SystemConfigItemResponse> list() {
        Map<String, SystemConfig> stored = systemConfigRepository.findAll().stream()
                .collect(Collectors.toMap(SystemConfig::getKey, Function.identity(), (a, b) -> a));

        // Chỉ liệt kê key trong catalog (Admin được chỉnh), không lộ các key nội bộ như force_payout_failure
        return SystemConfigCatalog.defaults().entrySet().stream()
                .map(e -> SystemConfigItemResponse.of(
                        SystemConfigCatalog.find(e.getKey()).orElseThrow(),
                        e.getValue(),
                        stored.get(e.getKey())))
                .toList();
    }

    @Override
    @Transactional
    public SystemConfigResponse update(Account admin, String key, String rawValue) {
        Spec spec = SystemConfigCatalog.find(key)
                .orElseThrow(() -> new AppException(ErrorCode.CONFIG_KEY_NOT_FOUND));

        String newValue = normalize(spec, rawValue);

        // Key chưa có dòng trong DB thì đang chạy bằng giá trị mặc định trong code: tạo dòng mới
        Optional<SystemConfig> existing = systemConfigRepository.findByKey(key);
        String oldValue = existing.map(SystemConfig::getValue).orElse(null);

        SystemConfig config = existing.orElseGet(() -> SystemConfig.builder().key(key).build());
        config.setValue(newValue);
        config.setUpdatedBy(admin);
        SystemConfig saved = systemConfigRepository.save(config);

        ObjectNode detail = objectMapper.createObjectNode();
        detail.putObject("before").put("value", oldValue);
        detail.putObject("after").put("value", newValue);
        auditLogger.log(admin, "UPDATE_CONFIG", "SYSTEM_CONFIG", key, detail);

        return SystemConfigResponse.from(saved);
    }

    // Trả về chuỗi chuẩn hóa để lưu; sai kiểu hoặc ngoài khoảng cho phép thì 422
    private String normalize(Spec spec, String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AppException(ErrorCode.CONFIG_VALUE_INVALID);
        }
        String text = raw.trim();
        try {
            BigDecimal number = switch (spec.type()) {
                case INT -> BigDecimal.valueOf(Long.parseLong(text));
                case DECIMAL -> new BigDecimal(text);
            };
            if (number.compareTo(spec.min()) < 0 || number.compareTo(spec.max()) > 0) {
                throw new AppException(ErrorCode.CONFIG_VALUE_INVALID);
            }
            return spec.type() == SystemConfigCatalog.ValueType.INT
                    ? number.toPlainString()
                    : number.stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
            throw new AppException(ErrorCode.CONFIG_VALUE_INVALID);
        }
    }
}