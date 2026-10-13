package com.rehome.rehome.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank(message = "email không được để trống")
    @jakarta.validation.constraints.Email(message = "email không hợp lệ")
    private String email;

    @NotBlank(message = "password không được để trống")
    private String password;
}
