package com.rehome.rehome.mapper;

import com.rehome.rehome.dto.response.MyProductResponse;
import com.rehome.rehome.dto.response.ProductEndOfTermDetailResponse;
import com.rehome.rehome.entity.marketplace.EndOfTermDecision;
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
    public ProductEndOfTermDetailResponse toEndOfTermDetailResponse(Product product,
                                                                    EndOfTermDecision pending) {
        if (product == null) {
            return null;
        }
        return ProductEndOfTermDetailResponse.builder()
                .id(product.getId())
                .productName(product.getName())
                .currentPrice(product.getCurrentPrice())
                .status(product.getStatus())
                .endOfTermDecision(toDecisionResponse(pending))
                .build();
    }

    private ProductEndOfTermDetailResponse.EndOfTermDecisionResponse toDecisionResponse(
            EndOfTermDecision d) {
        if (d == null) {
            return null;
        }
        return ProductEndOfTermDetailResponse.EndOfTermDecisionResponse.builder()
                .deadline(d.getDeadline())
                .decision(d.getDecision())
                .build();
    }
}
