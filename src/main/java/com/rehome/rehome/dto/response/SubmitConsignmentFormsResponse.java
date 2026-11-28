package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.ConsignmentFormStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitConsignmentFormsResponse {
    private UUID warehouseId;
    private int successCount;
    private int failureCount;
    private List<Result> results;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Result {
        private UUID formId;
        private boolean success;
        private ConsignmentFormStatus status;
        private String formNo;
        private String errorCode;
        private String message;
    }
}
