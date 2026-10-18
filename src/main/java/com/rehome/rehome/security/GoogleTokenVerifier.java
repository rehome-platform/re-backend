package com.rehome.rehome.security;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;

@Slf4j
@Component
public class GoogleTokenVerifier {

    public record GoogleUserInfo(String googleId, String email, String name) {}

    private final GoogleIdTokenVerifier verifier;

    // google.client-id có thể chứa nhiều client ID, cách nhau dấu phẩy (web, android, ios…)
    public GoogleTokenVerifier(@Value("${google.client-id}") String clientIds) {
        log.info("[Google] audience = '{}'", clientIds); // dấu nháy để thấy khoảng trắng thừa
        this.verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Arrays.stream(clientIds.split(",")).map(String::trim).toList())
                .build();
    }

    public GoogleUserInfo verify(String idToken) {
        try {
            GoogleIdToken token = verifier.verify(idToken);
            if (token == null) {
                log.warn("[Google] verify trả null — sai audience / issuer / token hết hạn");
                throw new InsufficientAuthenticationException("Google token không hợp lệ");
            }
            GoogleIdToken.Payload p = token.getPayload();
            if (!Boolean.TRUE.equals(p.getEmailVerified())) {
                log.warn("[Google] email chưa xác minh: {}", p.getEmail());
                throw new InsufficientAuthenticationException("Email Google chưa được xác minh");
            }
            return new GoogleUserInfo(
                    p.getSubject(),
                    p.getEmail().toLowerCase(),
                    (String) p.get("name"));
        } catch (GeneralSecurityException | IOException e) {
            log.error("[Google] không xác minh được token", e);
            throw new InsufficientAuthenticationException("Không thể xác minh Google token");
        }
    }
}