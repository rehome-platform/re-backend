package com.rehome.rehome.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class OtpException extends RuntimeException {
    private final HttpStatus status;
    private final Long retryAfterSeconds;

    public OtpException(HttpStatus status, String message) {
        this(status, message, null);
    }

    public OtpException(HttpStatus status, String message, Long retryAfterSeconds) {
        super(message);
        this.status = status;
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
