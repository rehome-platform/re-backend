package com.rehome.rehome.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccountMeResponse(
        UUID id,
        String email,
        String authorities,
        String role,
        UUID warehouseId,
        String warehouseName,
        String fullName,
        String moderatorCode
) {}