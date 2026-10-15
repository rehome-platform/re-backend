package com.rehome.rehome.ServiceImpl;

import com.rehome.rehome.entity.enums.OtpPurpose;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.IOException;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtpEmailServiceImplTest {

    @Mock JavaMailSender mailSender;

    private MimeMessage message;

    @BeforeEach
    void setUp() {
        message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
    }

    private OtpEmailServiceImpl service(String from) throws IOException {
        return new OtpEmailServiceImpl(mailSender, from);
    }

    @Test
    void send_registration_ttlInWholeMinutes() throws Exception {
        service("noreply@rehome.vn").send("user@x.com", "123456", OtpPurpose.REGISTER, 300);

        verify(mailSender).send(message);
        assertThat(message.getSubject()).isEqualTo("ReHome - Mã OTP đăng ký tài khoản");
        assertThat(message.getFrom()[0].toString()).isEqualTo("noreply@rehome.vn");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("user@x.com");
    }

    @Test
    void send_forgotPassword_ttlInSeconds() throws Exception {
        service("noreply@rehome.vn").send("user@x.com", "654321", OtpPurpose.FORGOT_PASSWORD, 90);

        verify(mailSender).send(message);
        assertThat(message.getSubject()).isEqualTo("ReHome - Mã OTP đặt lại mật khẩu");
    }

    @Test
    void send_invalidFromAddress_throwsMailPreparationException() throws Exception {
        OtpEmailServiceImpl svc = service("a@x.com,b@x.com"); // 2 địa chỉ -> AddressException

        assertThatThrownBy(() -> svc.send("user@x.com", "123456", OtpPurpose.REGISTER, 300))
                .isInstanceOf(MailPreparationException.class)
                .hasMessageContaining("Không thể tạo email OTP");

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void send_smtpFails_rethrowsMailException() throws Exception {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));
        OtpEmailServiceImpl svc = service("noreply@rehome.vn");

        assertThatThrownBy(() -> svc.send("user@x.com", "123456", OtpPurpose.REGISTER, 300))
                .isInstanceOf(MailSendException.class)
                .hasMessage("smtp down");
    }
}