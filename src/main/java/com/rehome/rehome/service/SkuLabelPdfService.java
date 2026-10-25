package com.rehome.rehome.service;

import com.rehome.rehome.entity.marketplace.Product;

public interface SkuLabelPdfService {
    // Sinh PDF tem SKU và trả byte. Không lưu lại: tem chỉ để in dán thùng, suy ra
    // được từ Product bất cứ lúc nào, không phải chứng từ cần giữ như biên nhận.
    byte[] generate(Product product);
}