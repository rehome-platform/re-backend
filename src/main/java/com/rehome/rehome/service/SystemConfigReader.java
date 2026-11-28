package com.rehome.rehome.service;

import java.math.BigDecimal;

public interface SystemConfigReader {

    int getInt(String key, int defaultValue);

    BigDecimal getDecimal(String key, BigDecimal defaultValue);

    default void evict(String key) {
    }
}