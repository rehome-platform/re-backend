package com.rehome.rehome.dto.response;

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
public class WarehouseHistoryResponse {
    private String target;
    private String targetType;
    private String targetId;
    private String action;
    private String actor;
    private Instant timestamp;
    private String result;
}
