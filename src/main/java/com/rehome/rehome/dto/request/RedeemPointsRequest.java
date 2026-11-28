package com.rehome.rehome.dto.request;

import com.rehome.rehome.entity.enums.PointRedeemType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RedeemPointsRequest {

    @NotNull(message = "type là bắt buộc.")
    private PointRedeemType type;

    private int quantity;
}
