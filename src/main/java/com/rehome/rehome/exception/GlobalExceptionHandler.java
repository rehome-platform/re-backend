package com.rehome.rehome.exception;

import com.rehome.rehome.dto.response.ApiError;
import com.rehome.rehome.dto.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OtpException.class)
    public ResponseEntity<ApiError> handleOtp(OtpException ex, HttpServletRequest req) {
        return build(ex.getStatus(), ex.getMessage(), req);
    }

    @ExceptionHandler({org.springframework.security.authentication.DisabledException.class,
            org.springframework.security.authentication.LockedException.class})
    public ResponseEntity<ApiError> handleDisabled(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "Tài khoản chưa xác minh email hoặc đã bị vô hiệu hóa.", req);
    }

    @ExceptionHandler(org.springframework.mail.MailException.class)
    public ResponseEntity<ApiError> handleMail(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, "Không thể gửi email OTP. Vui lòng thử lại sau.", req);
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleInvalidJson(
            org.springframework.http.converter.HttpMessageNotReadableException ex,
            HttpServletRequest req) {

        String message = "Dữ liệu gửi lên không hợp lệ.";
        Throwable cause = ex.getCause();

        if (cause instanceof com.fasterxml.jackson.databind.exc.InvalidFormatException ife) {
            String field = ife.getPath().isEmpty() ? "" :
                    ife.getPath().get(ife.getPath().size() - 1).getFieldName();

            if ("purpose".equals(field)) {
                message = "purpose phải là REGISTER hoặc FORGOT_PASSWORD.";
            } else if (ife.getTargetType() != null && ife.getTargetType().isEnum()) {
                message = "Giá trị của '" + field + "' không hợp lệ.";
            } else {
                message = "Trường '" + field + "' sai định dạng.";
            }
        } else if (cause instanceof com.fasterxml.jackson.core.JsonParseException) {
            message = "JSON không đúng cú pháp.";
        }

        return build(HttpStatus.BAD_REQUEST, message, req);
    }

    @ExceptionHandler(AccountAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleAccountExists(AccountAlreadyExistsException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), req);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<?>> handleConflict(ConflictException ex, HttpServletRequest req) {
        ApiResponse<?> body = ex.getCode() != null
                ? ApiResponse.error(409, ex.getCode(), ex.getMessage())
                : ApiResponse.error(409, ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(GhnException.class)
    public ResponseEntity<ApiResponse<?>> handleGhn(GhnException ex, HttpServletRequest req) {
        // Theo đúng shape response của API 70 trong docs/ghnapi.md: không có field "code" riêng,
        // mã lỗi đã nằm trong message (tiền tố "GHN_ERROR:")
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error(502, ex.getMessage()));
    }

    @ExceptionHandler(TokenRefreshException.class)
    public ResponseEntity<ApiError> handleTokenRefresh(TokenRefreshException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), req);
    }

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handle(AppException ex) {
        ErrorCode ec = ex.getErrorCode();
        return ResponseEntity.status(ec.getStatus())
                .body(new ApiResponse<>(null, false, ec.getStatus().value(), ec.name(), ec.getDefaultMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, "Sai email hoặc password", req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, message, req);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<?>> handleAuthenticationException(AuthenticationException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(401, "Token không hợp lệ hoặc đã hết hạn."));
    }

    @ExceptionHandler(TokenExpiredException.class)
    public ResponseEntity<ApiResponse<?>> handleTokenExpiredException(AuthenticationException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(401, "Token đã hết hạn."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<?>> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(403, "Bạn không có quyền truy cập tài nguyên này."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<?>> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(404, ex.getMessage()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<?>> handleBadRequest(BadRequestException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, ex.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<?>> handleForbidden(ForbiddenException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(403, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneral(Exception ex, HttpServletRequest req) {
        ex.printStackTrace(); // tạm thời để debug
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), req);
    }


    @ExceptionHandler(AccountNotVerifiedException.class)
    public ResponseEntity<ApiError> handleAccountNotVerified(
            AccountNotVerifiedException ex,
            HttpServletRequest req) {

        return build(HttpStatus.FORBIDDEN, ex.getMessage(), req);
    }

    @ExceptionHandler(AccountBannedException.class)
    public ResponseEntity<ApiError> handleAccountBanned(AccountBannedException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), req);
    }


    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest req) {
        ApiError error = ApiError.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(req.getRequestURI())
                .build();
        return ResponseEntity.status(status).body(error);
    }
}
