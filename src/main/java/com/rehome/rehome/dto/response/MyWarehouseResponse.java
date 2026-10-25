package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.marketplace.Warehouse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MyWarehouseResponse {
    private String name;

    public static MyWarehouseResponse from(Warehouse w) {
        return new MyWarehouseResponse(
                w.getName());
    }
}
