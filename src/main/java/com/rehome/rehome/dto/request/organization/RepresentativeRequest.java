package com.rehome.rehome.dto.request.organization;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RepresentativeRequest {
    @NotBlank(message = "representative.fullName là bắt buộc")
    private String fullName;

    private String position;

    @NotBlank(message = "representative.cccdNumber là bắt buộc")
    private String cccdNumber;

    @NotBlank(message = "representative.cccdFrontUrl là bắt buộc")
    private String cccdFrontUrl;

    @NotBlank(message = "representative.cccdBackUrl là bắt buộc")
    private String cccdBackUrl;
}
