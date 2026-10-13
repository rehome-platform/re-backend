package com.rehome.rehome;

import com.rehome.rehome.entity.EmailOtp;
import com.rehome.rehome.repository.EmailOtpRepository;
import com.rehome.rehome.repository.AccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import jakarta.mail.Session;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:otp;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.show-sql=false", "app.otp.from=test@example.com"
})
@AutoConfigureMockMvc
class OtpIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired EmailOtpRepository otps;
    @Autowired AccountRepository accounts;
    @MockBean JavaMailSender mail;

    String email;
    AtomicReference<String> code;

    @BeforeEach void setup() {

        email = UUID.randomUUID().toString() + "@example.com";
        code = new AtomicReference<>();
        when(mail.createMimeMessage()).thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
        doAnswer(invocation -> {
            MimeMessage message = invocation.getArgument(0);
            message.saveChanges();
            String html = htmlContent(message);
            assertThat(html).contains("ECO", "5 phút").doesNotContain("{{");
            var matcher = java.util.regex.Pattern.compile("(?<![0-9])[0-9]{6}(?![0-9])").matcher(html);
            assertThat(matcher.find()).isTrue();
            code.set(matcher.group());
            return null;
        }).when(mail).send(any(MimeMessage.class));
    }

    private String htmlContent(Part part) throws Exception {
        if (part.isMimeType("text/html")) return (String) part.getContent();
        if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                String html = htmlContent(multipart.getBodyPart(i));
                if (!html.isEmpty()) return html;
            }
        }
        return "";
    }

    ResultActions postJson(String endpoint, Object body) throws Exception {
        return mvc.perform(post("/api/auth/" + endpoint).contentType("application/json")
                .content(json.writeValueAsBytes(body)));
    }

    void register() throws Exception {
        postJson("register", Map.of("email", email, "password", "secret123"))
                .andExpect(status().isCreated()).andExpect(jsonPath("accessToken").doesNotExist());
        assertThat(code.get()).matches("[0-9]{6}");
    }

    ResultActions verify(String purpose, String value) throws Exception {
        return postJson("verify-otp", Map.of("email", email, "purpose", purpose, "otp", value));
    }

    @Test void registrationRequiresVerificationAndOtpIsSingleUse() throws Exception {
        register();
        postJson("login", Map.of("email", email, "password", "secret123")).andExpect(status().isForbidden());
        verify("REGISTER", code.get()).andExpect(status().isOk());
        verify("REGISTER", code.get()).andExpect(status().isBadRequest());
        postJson("login", Map.of("email", email, "password", "secret123"))
                .andExpect(status().isOk()).andExpect(jsonPath("accessToken").isNotEmpty());
    }

    @Test void wrongAttemptsAreCommittedAndExhaustCode() throws Exception {
        register();
        String correct = code.get();
        String wrong = correct.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) verify("REGISTER", wrong).andExpect(status().isBadRequest());
        verify("REGISTER", correct).andExpect(status().isBadRequest());
        assertThat(otps.findById(accounts.findByEmail(email).orElseThrow().getId() + ":REGISTER")
                .orElseThrow().getAttempts()).isEqualTo(5);
    }

    @Test void cooldownExpiryAndPurposeIsolation() throws Exception {
        register();
        postJson("resend-otp", Map.of("email", email, "purpose", "REGISTER"))
                .andExpect(status().isTooManyRequests());
        verify("FORGOT_PASSWORD", code.get()).andExpect(status().isBadRequest());
        String key = accounts.findByEmail(email).orElseThrow().getId() + ":REGISTER";
        EmailOtp otp = otps.findById(key).orElseThrow();
        otp.setExpiresAt(Instant.now().minusSeconds(1));
        otp.setSentAt(Instant.now().minusSeconds(61));
        otps.save(otp);
        verify("REGISTER", code.get()).andExpect(status().isBadRequest());
        postJson("resend-otp", Map.of("email", email, "purpose", "REGISTER")).andExpect(status().isOk());
        verify("REGISTER", code.get()).andExpect(status().isOk());
    }

    @Test void passwordResetConsumesTokenAndRevokesRefreshTokens() throws Exception {
        register();
        verify("REGISTER", code.get()).andExpect(status().isOk());
        String login = postJson("login", Map.of("email", email, "password", "secret123"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String refreshToken = json.readTree(login).get("refreshToken").asText();
        postJson("resend-otp", Map.of("email", email, "purpose", "FORGOT_PASSWORD")).andExpect(status().isOk());
        String result = verify("FORGOT_PASSWORD", code.get()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String resetToken = json.readTree(result).get("resetToken").asText();
        var body = Map.of("email", email, "resetToken", resetToken, "newPassword", "changed123", "confirmPassword", "changed123");
        postJson("reset-password", body).andExpect(status().isOk());
        postJson("reset-password", body).andExpect(status().isBadRequest());
        postJson("refresh", Map.of("refreshToken", refreshToken)).andExpect(status().isForbidden());
        postJson("login", Map.of("email", email, "password", "secret123")).andExpect(status().isUnauthorized());
        postJson("login", Map.of("email", email, "password", "changed123")).andExpect(status().isOk());
    }

    @Test void mailFailureRollsBackRegistration() throws Exception {
        doThrow(new MailSendException("SMTP unavailable")).when(mail).send(any(MimeMessage.class));
        postJson("register", Map.of("email", email, "password", "secret123"))
                .andExpect(status().isServiceUnavailable());
        assertThat(accounts.existsByEmail(email)).isFalse();
    }

    @Test void invalidPayloadIsBadRequest() throws Exception {
        postJson("verify-otp", Map.of("email", "invalid", "purpose", "REGISTER", "otp", "123"))
                .andExpect(status().isBadRequest());
        postJson("resend-otp", Map.of("email", email, "purpose", "UNKNOWN"))
                .andExpect(status().isBadRequest());
    }

    @Test void emailIdentifiesAccountAcrossJwtRefreshAndLogout() throws Exception {
        register();
        verify("REGISTER", code.get()).andExpect(status().isOk());
        postJson("register", Map.of("email", email, "password", "secret123"))
                .andExpect(status().isConflict());
        postJson("login", Map.of("email", "invalid", "password", "secret123"))
                .andExpect(status().isBadRequest());
        String login = postJson("login", Map.of("email", email, "password", "secret123"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String refreshed = postJson("refresh", Map.of("refreshToken", json.readTree(login).get("refreshToken").asText()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String access = json.readTree(refreshed).get("accessToken").asText();
        mvc.perform(get("/api/account/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("email").value(email))
                .andExpect(jsonPath("username").doesNotExist());
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + access))
                .andExpect(status().isNoContent());
        postJson("refresh", Map.of("refreshToken", json.readTree(refreshed).get("refreshToken").asText()))
                .andExpect(status().isForbidden());
    }

    @Test void bannedAccountCannotLoginRefreshOrUseExistingAccessToken() throws Exception {
        register();
        verify("REGISTER", code.get()).andExpect(status().isOk());
        String login = postJson("login", Map.of("email", email, "password", "secret123"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var tokens = json.readTree(login);
        var account = accounts.findByEmail(email).orElseThrow();
        account.setStatus(com.rehome.rehome.entity.AccountStatus.BANNED);
        accounts.save(account);
        postJson("login", Map.of("email", email, "password", "secret123"))
                .andExpect(status().isForbidden());
        postJson("refresh", Map.of("refreshToken", tokens.get("refreshToken").asText()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/user/me").header("Authorization", "Bearer " + tokens.get("accessToken").asText()))
                .andExpect(status().isForbidden());
    }

    @Test void accountSchemaPersistsOptionalGoogleIdentityAndPhoneWithoutPassword() {
        var account = com.rehome.rehome.entity.Account.builder()
                .email(email)
                .googleId(UUID.randomUUID().toString())
                .phone(UUID.randomUUID().toString())
                .role(com.rehome.rehome.entity.Role.ORGANIZATION)
                .build();
        accounts.saveAndFlush(account);
        var persisted = accounts.findById(account.getId()).orElseThrow();
        assertThat(persisted.getPasswordHash()).isNull();
        assertThat(persisted.getGoogleId()).isEqualTo(account.getGoogleId());
        assertThat(persisted.getPhone()).isEqualTo(account.getPhone());
        assertThat(persisted.getStatus()).isEqualTo(com.rehome.rehome.entity.AccountStatus.ACTIVE);
        assertThat(persisted.isEmailVerified()).isFalse();
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getUpdatedAt()).isNotNull();
        assertThat(persisted.getAuthorities()).extracting("authority").containsExactly("ROLE_ORGANIZATION");
    }
}
