
package com.rehome.rehome.ServiceImpl;

import com.rehome.rehome.entity.enums.OtpPurpose;
import com.rehome.rehome.service.OtpEmailService;

import jakarta.mail.MessagingException;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class OtpEmailServiceImpl implements OtpEmailService {

    private final JavaMailSender mailSender;
    private final String template;
    private final String from;

    public OtpEmailServiceImpl(
            JavaMailSender mailSender,
            @Value("${app.otp.from}") String from
    ) throws IOException {
        this.mailSender = mailSender;
        this.from = from;
        this.template = new ClassPathResource(
                "templates/mail/otp.html"
        ).getContentAsString(StandardCharsets.UTF_8);
    }

    @Override
    public void send(
            String email,
            String code,
            OtpPurpose purpose,
            long ttlSeconds
    ) {
        boolean registration = purpose == OtpPurpose.REGISTER;

        String title = registration
                ? "Account Registration Verification"
                : "Password Reset Verification";

        String description = registration
                ? "Chúng tôi nhận được yêu cầu đăng ký tài khoản của bạn. Vui lòng sử dụng mã OTP bên dưới để hoàn tất đăng ký:"
                : "Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn. Vui lòng sử dụng mã OTP bên dưới để tiếp tục:";

        String expiry = ttlSeconds % 60 == 0
                ? (ttlSeconds / 60) + " phút"
                : ttlSeconds + " giây";

        String html = template
                .replace("{{title}}", title)
                .replace("{{description}}", description)
                .replace("{{otp}}", code)
                .replace("{{expiry}}", expiry);

        try {
            var message = mailSender.createMimeMessage();

            var helper = new MimeMessageHelper(
                    message,
                    true,
                    StandardCharsets.UTF_8.name()
            );

            helper.setFrom(from);
            helper.setTo(email);

            helper.setSubject(registration
                    ? "ReHome - Mã OTP đăng ký tài khoản"
                    : "ReHome - Mã OTP đặt lại mật khẩu");

            helper.setText(
                    description
                            + "\nMã OTP của bạn: " + code
                            + "\nMã OTP sẽ hết hạn sau " + expiry + ".",
                    html
            );

            log.info("Đang gửi email OTP qua SMTP, from={}", from);

            mailSender.send(message);

            log.info("SMTP đã chấp nhận email OTP để gửi");

        } catch (MessagingException ex) {
            log.error("Không thể tạo nội dung email OTP", ex);
            throw new MailPreparationException(
                    "Không thể tạo email OTP", ex
            );

        } catch (MailException ex) {
            log.error("Gửi email OTP thất bại: {}", ex.getMessage(), ex);
            throw ex;
        }
    }
}
