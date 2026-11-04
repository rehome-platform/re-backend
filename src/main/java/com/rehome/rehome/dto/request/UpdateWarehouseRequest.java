package com.rehome.rehome.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** API 143 - Admin sửa kho. Gửi đủ 5 field (PUT), giống API tạo. */
@Data
public class UpdateWarehouseRequest {

    @NotBlank(message = "name không được để trống")
    @Size(max = 255, message = "name tối đa 255 ký tự")
    private String name;

    @NotBlank(message = "address không được để trống")
    @Size(max = 255, message = "address tối đa 255 ký tự")
    private String address;

    @NotNull(message = "latitude không được để trống")
    @DecimalMin(value = "-90", message = "latitude phải trong khoảng -90 đến 90")
    @DecimalMax(value = "90", message = "latitude phải trong khoảng -90 đến 90")
    private BigDecimal latitude;

    @NotNull(message = "longitude không được để trống")
    @DecimalMin(value = "-180", message = "longitude phải trong khoảng -180 đến 180")
    @DecimalMax(value = "180", message = "longitude phải trong khoảng -180 đến 180")
    private BigDecimal longitude;

    @NotBlank(message = "openingHours không được để trống")
    @Size(max = 255, message = "openingHours tối đa 255 ký tự")
    private String openingHours;
}
