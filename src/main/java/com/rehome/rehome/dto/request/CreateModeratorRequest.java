package com.rehome.rehome.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateModeratorRequest {

    @NotBlank(message = "email không được để trống")
    @Email(message = "email không đúng định dạng")
    @Size(max = 255, message = "email tối đa 255 ký tự")
    private String email;

    @NotBlank(message = "fullName không được để trống")
    @Size(max = 255, message = "fullName tối đa 255 ký tự")
    private String fullName;

    @Size(max = 20, message = "phone tối đa 20 ký tự")
    private String phone;

    @NotNull(message = "warehouseId không được để trống")
    private UUID warehouseId;
}
