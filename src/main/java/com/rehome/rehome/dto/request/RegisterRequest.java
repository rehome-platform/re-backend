package com.rehome.rehome.dto.request;

import com.rehome.rehome.entity.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor

public class RegisterRequest {

    @NotBlank(message = "email không được để trống")
    @Email(message = "email không hợp lệ")
    private String email;

    @NotBlank(message = "password không được để trống")
    @Size(min = 6, message = "password phải có ít nhất 6 ký tự")
    private String password;

    private String fullName;

    private String phone;
}
