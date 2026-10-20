package com.rehome.rehome.mapper;

import com.rehome.rehome.dto.response.MyProductResponse;
import com.rehome.rehome.entity.marketplace.Product;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {
    public MyProductResponse toMyProductResponse(Product product) {
        if (product == null) {
            return null;
        }
        return MyProductResponse.builder()
                .id(product.getId())
                .productName(product.getName())
                .status(product.getStatus())
                .currentTerm(product.getCurrentTerm())
                .termStartedAt(product.getTermStartedAt())
                .endOfTermDeadline(product.getEndOfTermDeadline())
                .build();
    }
}
