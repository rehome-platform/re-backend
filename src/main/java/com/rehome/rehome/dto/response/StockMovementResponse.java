package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.StockMovementType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StockMovementResponse {
    private UUID id;
    private StockMovementType type;
    private String fromLocation;
    private String toLocation;
    private String note;
    private Instant createdAt;
}