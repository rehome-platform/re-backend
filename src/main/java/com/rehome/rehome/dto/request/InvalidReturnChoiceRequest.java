package com.rehome.rehome.dto.request;

import com.rehome.rehome.entity.enums.InvalidReturnChoice;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvalidReturnChoiceRequest {
    // SHIP_BACK = ReHome gửi lại qua GHN, người mua trả phí trước; WAREHOUSE_PICKUP = đến kho nhận
    private InvalidReturnChoice choice;

    // Chỉ dùng khi SHIP_BACK: địa chỉ trong sổ địa chỉ của người mua. Bỏ trống thì dùng địa chỉ
    // của đơn gốc (Order.shippingAddress) nếu địa chỉ đó còn trong sổ.
    private UUID addressId;

    public InvalidReturnChoiceRequest(InvalidReturnChoice choice) {
        this.choice = choice;
    }
}
