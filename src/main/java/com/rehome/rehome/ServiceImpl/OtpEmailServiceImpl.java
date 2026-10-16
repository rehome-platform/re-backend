
package com.rehome.rehome.ServiceImpl;

import com.rehome.rehome.entity.enums.OtpPurpose;
import com.rehome.rehome.service.OtpEmailService;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class OtpEmailServiceImpl implements OtpEmailService {

    private final RestClient brevoClient;
    private final String template;
    private final String from;

    public OtpEmailServiceImpl(
            RestClient.Builder restClientBuilder,
            @Value("${app.otp.from}") String from,
            @Value("${brevo.api-key}") String brevoApiKey
    ) throws IOException {
        this.from = from;
        this.template = new ClassPathResource(
                "templates/mail/otp.html"
        ).getContentAsString(StandardCharsets.UTF_8);
        this.brevoClient = restClientBuilder
                .baseUrl("https://api.brevo.com/v3")
                .defaultHeader("api-key", brevoApiKey)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .build();
    }

    private void sendViaBrevo(String to, String subject, String htmlContent, String textContent) {
        Map<String, Object> sender = new LinkedHashMap<>();
        sender.put("email", from);

        Map<String, Object> recipient = new LinkedHashMap<>();
        recipient.put("email", to);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sender", sender);
        body.put("to", java.util.List.of(recipient));
        body.put("subject", subject);
        body.put("htmlContent", htmlContent);
        body.put("textContent", textContent);

        try {
            brevoClient.post()
                    .uri("/smtp/email")
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Brevo API đã chấp nhận email để gửi, to={}", to);

        } catch (RestClientException ex) {
            log.error("Gửi email qua Brevo API thất bại: {}", ex.getMessage(), ex);
            throw new MailException("Gửi email OTP thất bại", ex) {};
        }
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

        String subject = registration
                ? "ReHome - Mã OTP đăng ký tài khoản"
                : "ReHome - Mã OTP đặt lại mật khẩu";

        String text = description
                + "\nMã OTP của bạn: " + code
                + "\nMã OTP sẽ hết hạn sau " + expiry + ".";

        log.info("Đang gửi email OTP qua Brevo API, from={}", from);

        sendViaBrevo(email, subject, html, text);
    }

    @Override
    public void sendResetLink(String to, String link, long minutes) {
        String text = "Bạn đã yêu cầu đặt lại mật khẩu.\n\n"
                + "Bấm vào link sau để đặt mật khẩu mới (hiệu lực " + minutes + " phút):\n"
                + link + "\n\n"
                + "Nếu bạn không yêu cầu, hãy bỏ qua email này.";

        sendViaBrevo(to, "Đặt lại mật khẩu Rehome", text, text);
    }
}
