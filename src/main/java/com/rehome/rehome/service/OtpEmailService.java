package com.rehome.rehome.service;


import com.rehome.rehome.entity.enums.OtpPurpose;

public interface OtpEmailService {

    void send(String email, String code, OtpPurpose purpose, long ttlSeconds);

    void sendResetLink(String to, String link, long minutes);

    /** API 138: gửi thông tin đăng nhập (mật khẩu tạm) cho Moderator mới do Admin tạo. */
    void sendModeratorAccount(String to, String fullName, String moderatorCode, String tempPassword);
}
