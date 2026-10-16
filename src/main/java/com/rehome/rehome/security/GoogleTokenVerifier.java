package com.rehome.rehome.security;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

@Component
public class GoogleTokenVerifier {

    public record GoogleUserInfo(String googleId, String email, String name) {}

    private final GoogleIdTokenVerifier verifier;

    public GoogleTokenVerifier(@Value("${google.client-id}") String clientId) {
        this.verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(List.of(clientId))
                .build();
    }

    public GoogleUserInfo verify(String idToken) {
        try {
            GoogleIdToken token = verifier.verify(idToken);
            if (token == null) {
                throw new InsufficientAuthenticationException("Google token không hợp lệ");
            }
            GoogleIdToken.Payload p = token.getPayload();
            if (!Boolean.TRUE.equals(p.getEmailVerified())) {
                throw new InsufficientAuthenticationException("Email Google chưa được xác minh");
            }
            return new GoogleUserInfo(
                    p.getSubject(),
                    p.getEmail().toLowerCase(),
                    (String) p.get("name"));
        } catch (GeneralSecurityException | IOException e) {
            throw new InsufficientAuthenticationException("Không thể xác minh Google token");
        }
    }
}