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
    NEW_PASSWORD_SAME_AS_OLD(HttpStatus.BAD_REQUEST, "Mật khẩu mới phải khác mật khẩu cũ."),
    INVALID_COORDINATES(HttpStatus.BAD_REQUEST, "lat/lng không hợp lệ."),
    // API 3, 4 - hồ sơ tổ chức
    ORGANIZATION_PROFILE_ALREADY_EXISTS(HttpStatus.CONFLICT, "Tài khoản này đã có hồ sơ tổ chức."),
    ORGANIZATION_EMAIL_TAKEN(HttpStatus.CONFLICT, "Email liên hệ đã được sử dụng."),
    ORGANIZATION_TAX_CODE_TAKEN(HttpStatus.CONFLICT, "Mã số thuế đã được sử dụng."),
    ORGANIZATION_PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ tổ chức."),
    ORGANIZATION_NOT_REJECTED(HttpStatus.FORBIDDEN, "Chỉ nộp lại hồ sơ khi trạng thái là REJECTED."),
    NOT_ORGANIZATION_ACCOUNT(HttpStatus.FORBIDDEN, "Chỉ tài khoản tổ chức mới được nộp hồ sơ."); // <-- dấu ; ở đây
    private final HttpStatus status;
    private final String defaultMessage;
}