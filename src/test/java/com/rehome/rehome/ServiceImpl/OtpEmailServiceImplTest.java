package com.rehome.rehome.ServiceImpl;

import com.rehome.rehome.entity.enums.OtpPurpose;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Lưu ý: constructor đọc file classpath "templates/mail/otp.html",
 * nên file này phải tồn tại (src/main/resources hoặc src/test/resources).
 */
@ExtendWith(MockitoExtension.class)
class OtpEmailServiceImplTest {

    private static final String FROM = "noreply@rehome.com";

    @Mock private JavaMailSender mailSender;

    private OtpEmailServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        service = new OtpEmailServiceImpl(mailSender, FROM);
    }

    /** Gom toàn bộ phần text của một MimeMessage (kể cả multipart lồng nhau). */
    private static String extractText(Part part) throws Exception {
        Object content = part.getContent();
        if (content instanceof String s) {
            return s;
        }
        if (content instanceof Multipart mp) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mp.getCount(); i++) {
                sb.append(extractText(mp.getBodyPart(i))).append('\n');
            }
            return sb.toString();
        }
        return "";
    }

    @Test
    @DisplayName("send: đăng ký -> subject, người nhận và nội dung đúng")
    void send_registration() throws Exception {
        MimeMessage message = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(message);

        service.send("user@test.com", "123456", OtpPurpose.REGISTER, 300);

        verify(mailSender).send(message);
        assertThat(message.getSubject()).isEqualTo("ReHome - Mã OTP đăng ký tài khoản");
        assertThat(message.getFrom()[0].toString()).isEqualTo(FROM);
        assertThat(message.getRecipients(Message.RecipientType.TO)[0].toString())
                .isEqualTo("user@test.com");

        String text = extractText(message);
        assertThat(text).contains("123456");
        assertThat(text).contains("5 phút");
        assertThat(text).contains("Account Registration Verification");
        assertThat(text).doesNotContain("{{otp}}").doesNotContain("{{expiry}}");
    }

    @Test
    @DisplayName("send: quên mật khẩu -> subject và title khác với đăng ký")
    void send_forgotPassword() throws Exception {
        MimeMessage message = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(message);

        service.send("user@test.com", "654321", OtpPurpose.FORGOT_PASSWORD, 600);

        verify(mailSender).send(message);
        assertThat(message.getSubject()).isEqualTo("ReHome - Mã OTP đặt lại mật khẩu");

        String text = extractText(message);
        assertThat(text).contains("654321");
        assertThat(text).contains("10 phút");
        assertThat(text).contains("Password Reset Verification");
    }

    @Test
    @DisplayName("send: TTL không chia hết cho 60 -> hiển thị theo giây")
    void send_expiryInSeconds() throws Exception {
        MimeMessage message = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(message);

        service.send("user@test.com", "111111", OtpPurpose.REGISTER, 90);

        assertThat(extractText(message)).contains("90 giây");
    }

    @Test
    @DisplayName("send: MailException khi gửi -> ném lại nguyên exception")
    void send_mailException_rethrown() {
        MimeMessage message = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(message);
        MailSendException ex = new MailSendException("smtp down");
        doThrow(ex).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> service.send("user@test.com", "123456", OtpPurpose.REGISTER, 300))
                .isSameAs(ex);
    }

    @Test
    @DisplayName("send: địa chỉ người nhận sai -> MailPreparationException, không gửi mail")
    void send_invalidRecipient_messagingException() {
        MimeMessage message = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(message);

        assertThatThrownBy(() -> service.send("khong hop le <<", "123456", OtpPurpose.REGISTER, 300))
                .isInstanceOf(MailPreparationException.class);
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendResetLink: gửi SimpleMailMessage đúng nội dung")
    void sendResetLink_success() {
        service.sendResetLink("user@test.com", "http://fe/reset?token=abc", 30);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage msg = captor.getValue();
        assertThat(msg.getFrom()).isEqualTo(FROM);
        assertThat(msg.getTo()).containsExactly("user@test.com");
        assertThat(msg.getSubject()).isEqualTo("Đặt lại mật khẩu Rehome");
        assertThat(msg.getText())
                .contains("http://fe/reset?token=abc")
                .contains("30 phút");
    }

    @Test
    @DisplayName("sendResetLink: mailSender lỗi -> exception được ném ra")
    void sendResetLink_mailException() {
        doThrow(new MailSendException("fail")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> service.sendResetLink("user@test.com", "link", 30))
                .isInstanceOf(MailSendException.class);
    }
}
