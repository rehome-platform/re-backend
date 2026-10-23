package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.entity.enums.OtpPurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OtpEmailServiceImplTest {

    private static final String BREVO_URL = "https://api.brevo.com/v3/smtp/email";

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
    }

    private OtpEmailServiceImpl service(String from) throws IOException {
        return new OtpEmailServiceImpl(restClientBuilder, from, "test-api-key");
    }

    @Test
    void send_registration_ttlInWholeMinutes() throws Exception {
        mockServer.expect(requestTo(BREVO_URL))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("api-key", "test-api-key"))
                .andExpect(jsonPath("$.sender.email").value("noreply@rehome.vn"))
                .andExpect(jsonPath("$.to[0].email").value("user@x.com"))
                .andExpect(jsonPath("$.subject").value("ReHome - Mã OTP đăng ký tài khoản"))
                .andRespond(withSuccess());

        service("noreply@rehome.vn").send("user@x.com", "123456", OtpPurpose.REGISTER, 300);

        mockServer.verify();
    }

    @Test
    void send_forgotPassword_ttlInSeconds() throws Exception {
        mockServer.expect(requestTo(BREVO_URL))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(jsonPath("$.subject").value("ReHome - Mã OTP đặt lại mật khẩu"))
                .andRespond(withSuccess());

        service("noreply@rehome.vn").send("user@x.com", "654321", OtpPurpose.FORGOT_PASSWORD, 90);

        mockServer.verify();
    }

    @Test
    void sendResetLink_sendsMailWithLinkAndMinutes() throws Exception {
        mockServer.expect(requestTo(BREVO_URL))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(jsonPath("$.sender.email").value("noreply@rehome.vn"))
                .andExpect(jsonPath("$.to[0].email").value("user@x.com"))
                .andExpect(jsonPath("$.subject").value("Đặt lại mật khẩu Rehome"))
                .andExpect(jsonPath("$.textContent")
                        .value(org.hamcrest.Matchers.containsString("http://fe/reset?token=abc")))
                .andExpect(jsonPath("$.textContent")
                        .value(org.hamcrest.Matchers.containsString("hiệu lực 30 phút")))
                .andRespond(withSuccess());

        service("noreply@rehome.vn")
                .sendResetLink("user@x.com", "http://fe/reset?token=abc", 30);

        mockServer.verify();
    }

    @Test
    void send_brevoApiFails_throwsMailException() throws Exception {
        mockServer.expect(requestTo(BREVO_URL))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        OtpEmailServiceImpl svc = service("noreply@rehome.vn");

        assertThatThrownBy(() -> svc.send("user@x.com", "123456", OtpPurpose.REGISTER, 300))
                .isInstanceOf(org.springframework.mail.MailException.class);
    }
}
