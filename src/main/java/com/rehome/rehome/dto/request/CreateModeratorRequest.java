package com.rehome.rehome.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/** API 138 - Admin tạo tài khoản Moderator. Mã nhân viên do hệ thống tự sinh nên không có trong body. */
@Data
public class CreateModeratorRequest {

    @NotBlank(message = "email không được để trống")
    @Email(message = "email không đúng định dạng")
    @Size(max = 255, message = "email tối đa 255 ký tự")
    private String email;

    @NotBlank(message = "fullName không được để trống")
    @Size(max = 255, message = "fullName tối đa 255 ký tự")
    private String fullName;

    // Không bắt buộc để tương thích client cũ; có gửi thì Service chuẩn hóa, kiểm tra định dạng 0xxxxxxxxx và trùng
    @Size(max = 20, message = "phone tối đa 20 ký tự")
    private String phone;

    @NotNull(message = "warehouseId không được để trống")
    private UUID warehouseId;
}
