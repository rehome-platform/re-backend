package com.rehome.rehome.dto.request;

import jakarta.validation.constraints.*;

public record ResetPasswordRequest(@NotBlank @Email String email, @NotBlank String resetToken,
                                   @NotBlank @Size(min = 6, max = 72) String newPassword,
                                   @NotBlank String confirmPassword) { }
