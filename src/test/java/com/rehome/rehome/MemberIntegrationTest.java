package com.rehome.rehome;

import com.rehome.rehome.entity.Account;
import com.rehome.rehome.entity.auth.MemberProfile;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.MemberProfileRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:members;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.show-sql=false", "app.otp.from=test@example.com"
})
@AutoConfigureMockMvc
class MemberIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccountRepository accounts;
    @Autowired MemberProfileRepository memberProfiles;
    @Autowired JdbcTemplate jdbc;
    @MockBean JavaMailSender mail;

    String email;
    AtomicReference<String> code;
    String accessToken;
    MemberProfile profile;

    @BeforeEach void setup() throws Exception {
        email = UUID.randomUUID().toString() + "@example.com";
        code = new AtomicReference<>();
        when(mail.createMimeMessage()).thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
        doAnswer(invocation -> {
            MimeMessage message = invocation.getArgument(0);
            message.saveChanges();
            String html = htmlContent(message);
            var matcher = java.util.regex.Pattern.compile("(?<![0-9])[0-9]{6}(?![0-9])").matcher(html);
            assertThat(matcher.find()).isTrue();
            code.set(matcher.group());
            return null;
        }).when(mail).send(any(MimeMessage.class));

        registerAndVerify();
        accessToken = login();
        profile = createMemberProfile();
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

    private ResultActions postJson(String endpoint, Object body) throws Exception {
        return mvc.perform(post("/api/auth/" + endpoint).contentType("application/json")
                .content(json.writeValueAsBytes(body)));
    }

    private void registerAndVerify() throws Exception {
        postJson("register", Map.of("email", email, "password", "secret123"))
                .andExpect(status().isCreated());
        postJson("verify-otp", Map.of("email", email, "purpose", "REGISTER", "otp", code.get()))
                .andExpect(status().isOk());
    }

    private String login() throws Exception {
        String result = postJson("login", Map.of("email", email, "password", "secret123"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(result).get("accessToken").asText();
    }

    private MemberProfile createMemberProfile() {
        Account account = accounts.findByEmail(email).orElseThrow();
        String memberCode = "MEM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        jdbc.update("INSERT INTO member_profiles (account_id, full_name, avatar_url, member_code) VALUES (?, ?, ?, ?)",
                account.getId(), "Nguyen Van A", "https://example.com/avatar.png", memberCode);
        return memberProfiles.findByAccountId(account.getId()).orElseThrow();
    }

    private ResultActions getMe() throws Exception {
        return mvc.perform(get("/members/me").header("Authorization", "Bearer " + accessToken));
    }

    private ResultActions getCode() throws Exception {
        return mvc.perform(get("/members/me/code").header("Authorization", "Bearer " + accessToken));
    }

    private ResultActions patchMe(Object body) throws Exception {
        return mvc.perform(patch("/members/me").header("Authorization", "Bearer " + accessToken)
                .contentType("application/json").content(json.writeValueAsBytes(body)));
    }

    // ---------- GET /members/me ----------

    @Test void getMemberProfileReturnsFullProfile() throws Exception {
        getMe().andExpect(status().isOk())
                .andExpect(jsonPath("result.email").value(email))
                .andExpect(jsonPath("result.fullName").value("Nguyen Van A"))
                .andExpect(jsonPath("result.avatarUrl").value("https://example.com/avatar.png"))
                .andExpect(jsonPath("result.memberCode").value(profile.getMemberCode()))
                .andExpect(jsonPath("result.emailVerified").value(true))
                .andExpect(jsonPath("result.accountId").value(profile.getAccountId().toString()));
    }

    @Test void getMemberProfileWithoutTokenIsForbidden() throws Exception {
        mvc.perform(get("/members/me")).andExpect(status().isForbidden());
    }

    @Test void getMemberProfileWithInvalidTokenIsForbidden() throws Exception {
        mvc.perform(get("/members/me").header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isForbidden());
    }

    @Test void getMemberProfileWithoutMemberProfileRowIsNotFound() throws Exception {
        memberProfiles.deleteById(profile.getAccountId());
        getMe().andExpect(status().isNotFound());
    }

    // ---------- PATCH /members/me ----------

    @Test void updateMemberProfileUpdatesFullNamePhoneAndAvatar() throws Exception {
        patchMe(Map.of("fullName", "Tran Thi B", "phone", "0912345678", "avatarUrl", "https://example.com/new.png"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("message").value("Cập nhật hồ sơ thành công"))
                .andExpect(jsonPath("result.fullName").value("Tran Thi B"))
                .andExpect(jsonPath("result.phone").value("0912345678"))
                .andExpect(jsonPath("result.avatarUrl").value("https://example.com/new.png"));

        getMe().andExpect(jsonPath("result.fullName").value("Tran Thi B"))
                .andExpect(jsonPath("result.phone").value("0912345678"));
    }

    @Test void updateMemberProfilePartialUpdateKeepsOtherFields() throws Exception {
        patchMe(Map.of("fullName", "Chi Co Ten"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.fullName").value("Chi Co Ten"))
                .andExpect(jsonPath("result.avatarUrl").value("https://example.com/avatar.png"));
    }

    @Test void updateMemberProfileRejectsInvalidPhoneFormat() throws Exception {
        patchMe(Map.of("phone", "12345"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("message").value("Số điện thoại không đúng định dạng."));

        getMe().andExpect(jsonPath("result.phone").doesNotExist());
    }

    @Test void updateMemberProfileWithoutTokenIsForbidden() throws Exception {
        mvc.perform(patch("/members/me").contentType("application/json")
                        .content(json.writeValueAsBytes(Map.of("fullName", "X"))))
                .andExpect(status().isForbidden());
    }

    // ---------- GET /members/me/code ----------

    @Test void getMemberCodeReturnsCode() throws Exception {
        getCode().andExpect(status().isOk())
                .andExpect(jsonPath("result.memberCode").value(profile.getMemberCode()));
    }

    @Test void getMemberCodeWithoutTokenIsForbidden() throws Exception {
        mvc.perform(get("/members/me/code")).andExpect(status().isForbidden());
    }

    @Test void getMemberCodeWithoutMemberProfileRowIsNotFound() throws Exception {
        memberProfiles.deleteById(profile.getAccountId());
        getCode().andExpect(status().isNotFound());
    }

}
