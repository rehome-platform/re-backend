package com.rehome.rehome.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RejectOrganizationRequest {
    private String reason;

    private List<DocumentDecision> documents;

    public RejectOrganizationRequest(String reason) {
        this.reason = reason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentDecision {
        private UUID documentId;
        private Boolean accepted;
    }
}
