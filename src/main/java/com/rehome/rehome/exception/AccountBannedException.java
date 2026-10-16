package com.rehome.rehome.exception;

public class AccountBannedException extends RuntimeException {
    private final ErrorCode errorCode;
    public AccountBannedException(String message, ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }
}
