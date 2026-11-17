package com.rehome.rehome.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record TransferDonationsRequest(
        @NotNull UUID organizationId,
        @NotNull UUID campaignId,
        @NotEmpty List<@NotBlank String> donationIds) {
}
