package com.rehome.rehome.dto.request.organization;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.LocalDate;

@Data
public class DocumentRequest {
    @NotBlank(message = "documents[].docType là bắt buộc")
    private String docType;

    private String docNumber;
    private LocalDate issueDate;
    private String issuingAuthority;

    @NotBlank(message = "documents[].fileUrl là bắt buộc")
    private String fileUrl;
}
