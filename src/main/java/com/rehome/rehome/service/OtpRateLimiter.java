package com.rehome.rehome.service;

import com.rehome.rehome.exception.OtpException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
public class OtpRateLimiter {
    private static final DefaultRedisScript<Long> CHECK_LIMIT = new DefaultRedisScript<>("""
            local count = tonumber(redis.call('GET', KEYS[1]) or '0')
            if count >= tonumber(ARGV[1]) then
                local ttl = redis.call('PTTL', KEYS[1])
                if ttl >= 0 then
                    return math.max(1, math.ceil(ttl / 1000))
                end
            end
            if count == 0 or redis.call('PTTL', KEYS[1]) < 0 then
                redis.call('SET', KEYS[1], '1', 'EX', ARGV[2])
            else
                redis.call('INCR', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final int emailLimit;
    private final int ipLimit;
    private final long windowSeconds;

    public OtpRateLimiter(StringRedisTemplate redis,
                          @Value("${app.otp.rate-limit.email-limit:3}") int emailLimit,
                          @Value("${app.otp.rate-limit.ip-limit:30}") int ipLimit,
                          @Value("${app.otp.rate-limit.window-seconds:900}") long windowSeconds) {
        if (emailLimit < 1 || ipLimit < 1 || windowSeconds < 1) {
            throw new IllegalArgumentException("OTP rate limits and window must be positive");
        }
        this.redis = redis;
        this.emailLimit = emailLimit;
        this.ipLimit = ipLimit;
        this.windowSeconds = windowSeconds;
    }

    public void check(String email, String ipAddress) {
        if (ipAddress != null && !ipAddress.isBlank()) {
            checkBucket("ip", ipAddress, ipLimit);
        }
        checkBucket("email", email.strip().toLowerCase(Locale.ROOT), emailLimit);
    }

    private void checkBucket(String scope, String identity, int limit) {
        String digest = DigestUtils.md5DigestAsHex(identity.getBytes(StandardCharsets.UTF_8));
        String key = "rehome:otp:rate:" + scope + ":" + digest;
        Long retryAfter;
        try {
            retryAfter = redis.execute(CHECK_LIMIT, List.of(key),
                    Integer.toString(limit), Long.toString(windowSeconds));
        } catch (DataAccessException ex) {
            log.warn("OTP rate limiter could not reach Redis ({})", ex.getClass().getSimpleName());
            throw unavailable();
        }
        if (retryAfter == null) {
            throw unavailable();
        }
        if (retryAfter > 0) {
            throw new OtpException(HttpStatus.TOO_MANY_REQUESTS,
                    "Bạn đã yêu cầu gửi email xác thực quá nhiều lần.", retryAfter);
        }
    }

    private OtpException unavailable() {
        return new OtpException(HttpStatus.SERVICE_UNAVAILABLE,
                "Tạm thời chưa thể gửi email xác thực. Vui lòng thử lại sau.");
    }
}
