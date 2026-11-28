package com.rehome.rehome.exception;

import lombok.Getter;

@Getter
public class AppException extends RuntimeException {
    private final ErrorCode errorCode;

    private final Object result;

    public AppException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public AppException(ErrorCode errorCode, Object result) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.result = result;
    }
}
