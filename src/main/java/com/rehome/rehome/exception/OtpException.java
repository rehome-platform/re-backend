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
        super(withRetryAfter(message, retryAfterSeconds));
        this.status = status;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    private static String withRetryAfter(String message, Long seconds) {
        if (seconds == null || seconds <= 0) return message;
        long minutes = seconds / 60;
        long remainder = seconds % 60;
        String wait = minutes == 0 ? seconds + " giây"
                : minutes + " phút" + (remainder == 0 ? "" : " " + remainder + " giây");
        return message + " Vui lòng thử lại sau " + wait + ".";
    }
}
