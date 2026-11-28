package com.rehome.rehome.service;

import com.rehome.rehome.entity.marketplace.Product;

public interface SkuLabelPdfService {
    byte[] generate(Product product);
}