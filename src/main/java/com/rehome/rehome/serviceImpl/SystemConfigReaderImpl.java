package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.repository.SystemConfigRepository;
import com.rehome.rehome.service.SystemConfigReader;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SystemConfigReaderImpl implements SystemConfigReader {

    private final SystemConfigRepository systemConfigRepository;

    @Override
    @Transactional(readOnly = true)
    public int getInt(String key, int defaultValue) {
        return systemConfigRepository.findByKey(key)
                .map(c -> {
                    try {
                        return Integer.parseInt(c.getValue().trim());
                    } catch (NumberFormatException e) {
                        throw new IllegalStateException("system_configs[" + key + "] không phải số nguyên hợp lệ: " + c.getValue(), e);
                    }
                })
                .orElse(defaultValue);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getDecimal(String key, BigDecimal defaultValue) {
        return systemConfigRepository.findByKey(key)
                .map(c -> {
                    try {
                        return new BigDecimal(c.getValue().trim());
                    } catch (NumberFormatException e) {
                        throw new IllegalStateException("system_configs[" + key + "] không phải số hợp lệ: " + c.getValue(), e);
                    }
                })
                .orElse(defaultValue);
    }
}