package com.rehome.rehome.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class OtpException extends RuntimeException {
    private final HttpStatus status;
    public OtpException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
