package com.rehome.rehome.dto.request.organization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class DonationPointRequest {
    @NotBlank(message = "donationPoint.name là bắt buộc")
    private String name;

    @NotBlank(message = "donationPoint.address là bắt buộc")
    private String address;

    @NotNull(message = "donationPoint.latitude là bắt buộc")
    private BigDecimal latitude;

    @NotNull(message = "donationPoint.longitude là bắt buộc")
    private BigDecimal longitude;

    private String receivingHours;
    private String contactPhone;
}
