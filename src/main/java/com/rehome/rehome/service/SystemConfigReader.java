package com.rehome.rehome.service;

import java.math.BigDecimal;

public interface SystemConfigReader {

    // Đọc số nguyên từ system_configs, không có key thì trả về defaultValue
    int getInt(String key, int defaultValue);

    // Đọc số thập phân từ system_configs, không có key thì trả về defaultValue
    BigDecimal getDecimal(String key, BigDecimal defaultValue);
}