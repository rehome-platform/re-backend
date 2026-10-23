package com.rehome.rehome.dto.request;

import com.rehome.rehome.entity.enums.InvalidReturnChoice;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvalidReturnChoiceRequest {
    // SHIP_BACK = gửi lại cho người mua, người mua tự trả phí; WAREHOUSE_PICKUP = đến kho nhận
    private InvalidReturnChoice choice;
}
