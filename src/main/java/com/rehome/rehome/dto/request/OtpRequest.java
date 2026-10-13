package com.rehome.rehome.dto.request;

import com.rehome.rehome.entity.OtpPurpose;
import jakarta.validation.constraints.*;

public record OtpRequest(@NotBlank @Email String email, @NotNull OtpPurpose purpose) { }
