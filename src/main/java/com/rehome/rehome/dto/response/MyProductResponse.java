package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.ProductStatus;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MyProductResponse {
    private String id;
    private String productName;
    private ProductStatus status;
    private Short currentTerm;
    private Instant termStartedAt;
    private Instant endOfTermDeadline;
}
