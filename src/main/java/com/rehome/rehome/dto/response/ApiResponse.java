package com.rehome.rehome.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.rehome.rehome.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiResponse<T> {
    private T result;

    @JsonProperty("isSuccess")
    @Builder.Default
    private boolean isSuccess = true;

    @Builder.Default
    private int statusCode = 200;

    @JsonInclude(JsonInclude.Include.NON_NULL) // thành công thì không có field code
    private String code;

    @Builder.Default
    private String message = "";

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .result(data)
                .isSuccess(true)
                .statusCode(200)
                .message("")
                .build();
    }

    public static <T> ApiResponse<T> success(T data, int statusCode) {
        return ApiResponse.<T>builder()
                .result(data)
                .isSuccess(true)
                .statusCode(statusCode)
                .message("")
                .build();
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .result(data)
                .isSuccess(true)
                .statusCode(200)
                .message(message)
                .build();
    }

    public static <T> ApiResponse<T> error(int statusCode, String message) {
        return ApiResponse.<T>builder()
                .result(null)
                .isSuccess(false)
                .statusCode(statusCode)
                .message(message)
                .build();
    }

    // MỚI: lỗi có code để FE điều hướng màn hình
    public static <T> ApiResponse<T> error(ErrorCode ec) {
        return ApiResponse.<T>builder()
                .result(null)
                .isSuccess(false)
                .statusCode(ec.getStatus().value())
                .code(ec.name())
                .message(ec.getDefaultMessage())
                .build();
    }

    public static <T> ApiResponse<T> error(ErrorCode ec, String message) {
        return ApiResponse.<T>builder()
                .result(null)
                .isSuccess(false)
                .statusCode(ec.getStatus().value())
                .code(ec.name())
                .message(message)
                .build();
    }

    public static <T> ApiResponse<T> success(T data, int statusCode, String message) {
        return ApiResponse.<T>builder()
                .result(data)
                .isSuccess(true)
                .statusCode(statusCode)
                .message(message)
                .build();
    }
}