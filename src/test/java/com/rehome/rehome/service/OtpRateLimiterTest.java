package com.rehome.rehome.service;

import com.rehome.rehome.exception.OtpException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class OtpRateLimiterTest {
    @Mock StringRedisTemplate redis;
    OtpRateLimiter limiter;

    @BeforeEach void setup() { limiter = new OtpRateLimiter(redis, 3, 30, 900); }

    @Test void blockedIpReturnsActualWaitingTimeAndDoesNotCountEmail() {
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString())).thenReturn(720L);
        assertThatThrownBy(() -> limiter.check("a@example.com", "198.51.100.1"))
                .isInstanceOf(OtpException.class)
                .hasMessageEndingWith("Vui lòng thử lại sau 12 phút.")
                .hasFieldOrPropertyWithValue("status", HttpStatus.TOO_MANY_REQUESTS)
                .hasFieldOrPropertyWithValue("retryAfterSeconds", 720L);
        verify(redis).execute(any(RedisScript.class), anyList(), eq("30"), eq("900"));
    }

    @Test void blockedEmailReturnsMinutesAndRemainingSeconds() {
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString())).thenReturn(0L, 65L);
        assertThatThrownBy(() -> limiter.check("a@example.com", "198.51.100.1"))
                .isInstanceOf(OtpException.class).hasMessageEndingWith("Vui lòng thử lại sau 1 phút 5 giây.");
        verify(redis).execute(any(RedisScript.class), anyList(), eq("3"), eq("900"));
    }

    @Test void differentClientsHaveSeparateIpBucketsWhileEmailNormalizationIsPreserved() {
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString())).thenReturn(0L);
        limiter.check(" Member@Example.com ", "198.51.100.1");
        limiter.check("member@example.com", "198.51.100.2");
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(redis, times(4)).execute(any(RedisScript.class), keys.capture(), anyString(), anyString());
        assertThat(keys.getAllValues().get(0)).isNotEqualTo(keys.getAllValues().get(2));
        assertThat(keys.getAllValues().get(1)).isEqualTo(keys.getAllValues().get(3));
        assertThat(keys.getAllValues().get(0).get(0)).startsWith("rehome:otp:rate:ip:");
        assertThat(keys.getAllValues().get(1).get(0)).startsWith("rehome:otp:rate:email:");
    }

    @Test void missingIpStillChecksEmailQuota() {
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString())).thenReturn(0L);
        limiter.check("a@example.com", null);
        verify(redis).execute(any(RedisScript.class), anyList(), eq("3"), eq("900"));
        verifyNoMoreInteractions(redis);
    }

    @Test void redisFailureDoesNotBypassTheRateLimit() {
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString()))
                .thenThrow(new RedisConnectionFailureException("offline"));
        assertThatThrownBy(() -> limiter.check("a@example.com", "198.51.100.1"))
                .isInstanceOf(OtpException.class).hasFieldOrPropertyWithValue("status", HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test void missingRedisResultIsUnavailable() {
        assertThatThrownBy(() -> limiter.check("a@example.com", "198.51.100.1"))
                .isInstanceOf(OtpException.class).hasFieldOrPropertyWithValue("status", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
