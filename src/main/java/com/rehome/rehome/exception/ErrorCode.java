package com.rehome.rehome.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    ACCOUNT_BANNED(HttpStatus.FORBIDDEN, "Tài khoản đã bị khoá!"),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "Tài khoản chưa xác thực email. Vui lòng kiểm tra hộp thư."),
    WRONG_PASSWORD(HttpStatus.BAD_REQUEST, "Mật khẩu cũ không đúng."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Token không hợp lệ hoặc đã hết hạn."),
    GOOGLE_ACCOUNT_CONFLICT(HttpStatus.CONFLICT, "Tài khoản gmail đã được sử dụng."),
    PASSWORD_CONFIRM_MISMATCH(HttpStatus.BAD_REQUEST, "Mật khẩu xác nhận không khớp."),
    PASSWORD_NOT_SET(HttpStatus.BAD_REQUEST, "Tài khoản chưa có mật khẩu."),
    NEW_PASSWORD_SAME_AS_OLD(HttpStatus.BAD_REQUEST, "Mật khẩu mới phải khác mật khẩu cũ.");

    private final HttpStatus status;
    private final String defaultMessage;
}