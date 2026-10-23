package com.rehome.rehome.exception;

import lombok.Getter;

@Getter
public class GhnException extends RuntimeException {
    private final String code;

    public GhnException(String message) {
        this(message, "GHN_ERROR");
    }

    public GhnException(String message, String code) {
        super(message);
        this.code = code;
    }
}
