package com.rehome.rehome.dto.request.organization;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

@Data
public class RegisterOrganizationRequest {
    @NotBlank(message = "legalName là bắt buộc")
    private String legalName;

    @NotBlank(message = "displayName là bắt buộc")
    private String displayName;

    @NotBlank(message = "orgType là bắt buộc")
    private String orgType;

    // Không bắt buộc (CLB trường học thường không có)
    private String taxCode;

    @NotBlank(message = "operationScope là bắt buộc")
    private String operationScope;

    @NotBlank(message = "address là bắt buộc")
    private String address;

    @NotBlank(message = "contactPhone là bắt buộc")
    private String contactPhone;

    @NotBlank(message = "contactEmail là bắt buộc")
    @Email(message = "contactEmail không hợp lệ")
    private String contactEmail;

    private String website;

    @NotBlank(message = "description là bắt buộc")
    private String description;

    private String logoUrl;

    @NotNull(message = "representative là bắt buộc")
    @Valid
    private RepresentativeRequest representative;

    @NotEmpty(message = "documents phải có tối thiểu 1 tệp giấy tờ pháp lý.")
    @Valid
    private List<DocumentRequest> documents;

    @NotNull(message = "donationPoint là bắt buộc")
    @Valid
    private DonationPointRequest donationPoint;
}