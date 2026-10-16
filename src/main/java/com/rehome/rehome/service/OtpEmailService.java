package com.rehome.rehome.service;


import com.rehome.rehome.entity.enums.OtpPurpose;

public interface OtpEmailService {

    void send(String email, String code, OtpPurpose purpose, long ttlSeconds);

    void sendResetLink(String to, String link, long minutes);
}
