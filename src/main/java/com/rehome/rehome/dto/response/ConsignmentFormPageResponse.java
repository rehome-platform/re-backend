package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsignmentFormPageResponse {
    private List<ConsignmentFormListItemResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private long pendingCount;
    private int maxPending;
}
