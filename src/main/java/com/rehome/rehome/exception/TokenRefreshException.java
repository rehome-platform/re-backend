package com.rehome.rehome.exception;

public class TokenRefreshException extends RuntimeException {
    public TokenRefreshException(String token, String message) {
        super(String.format("Refresh token [%s] không hợp lệ: %s", token, message));
    }
}
