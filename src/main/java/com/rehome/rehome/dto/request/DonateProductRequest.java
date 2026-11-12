package com.rehome.rehome.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record DonateProductRequest(@NotNull UUID campaignId,
                                   @JsonProperty("isAnonymous") boolean isAnonymous) {}
