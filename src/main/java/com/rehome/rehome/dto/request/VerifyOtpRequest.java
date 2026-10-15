package com.rehome.rehome.dto.request;

import com.rehome.rehome.entity.enums.OtpPurpose;
import jakarta.validation.constraints.*;

public record VerifyOtpRequest(@NotBlank @Email String email, @NotNull OtpPurpose purpose,
                               @NotBlank @Pattern(regexp = "[0-9]{6}") String otp) { }
