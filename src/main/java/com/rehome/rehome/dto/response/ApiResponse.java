package com.rehome.rehome.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    public static <T> ApiResponse<T> error(int statusCode, String message) {
        return ApiResponse.<T>builder()
                .result(null)
                .isSuccess(false)
                .statusCode(statusCode)
                .message(message)
                .build();
    }
}