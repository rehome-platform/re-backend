package com.rehome.rehome.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OtpResponse(String message, String resetToken) {
    public OtpResponse(String message) { this(message, null); }
}
