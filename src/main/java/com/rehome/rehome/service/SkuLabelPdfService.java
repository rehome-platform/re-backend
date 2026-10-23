package com.rehome.rehome.service;

import com.rehome.rehome.entity.marketplace.Product;

public interface SkuLabelPdfService {
    // Sinh PDF tem SKU, upload và trả URL
    String generate(Product product);
}