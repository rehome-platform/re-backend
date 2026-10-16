package com.rehome.rehome.exception;

public class AccountNotVerifiedException extends RuntimeException {
    private final ErrorCode errorCode;
    public AccountNotVerifiedException(String message, ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }
}
