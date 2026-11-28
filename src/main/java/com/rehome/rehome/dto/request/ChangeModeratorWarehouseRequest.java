package com.rehome.rehome.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ChangeModeratorWarehouseRequest {

    @NotNull(message = "warehouseId không được để trống")
    private UUID warehouseId;
}
