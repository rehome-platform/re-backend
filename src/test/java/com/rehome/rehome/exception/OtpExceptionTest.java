package com.rehome.rehome.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;

class OtpExceptionTest {
    @ParameterizedTest
    @CsvSource({"1,1 giây", "59,59 giây", "60,1 phút", "61,1 phút 1 giây", "720,12 phút", "721,12 phút 1 giây"})
    void waitingMessagePreservesExactRetryAfterSeconds(long seconds, String wait) {
        var error = new OtpException(HttpStatus.TOO_MANY_REQUESTS, "Quá nhiều yêu cầu.", seconds);
        var response = new GlobalExceptionHandler().handleOtp(error,
                new MockHttpServletRequest("POST", "/api/auth/register"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo(Long.toString(seconds));
        assertThat(response.getBody().getMessage()).isEqualTo("Quá nhiều yêu cầu. Vui lòng thử lại sau " + wait + ".");
    }

    @Test void nonRateLimitErrorsKeepTheirMessageWithoutRetryHeader() {
        var response = new GlobalExceptionHandler().handleOtp(
                new OtpException(HttpStatus.BAD_REQUEST, "Mã không hợp lệ."), new MockHttpServletRequest());
        assertThat(response.getBody().getMessage()).isEqualTo("Mã không hợp lệ.");
        assertThat(response.getHeaders()).doesNotContainKey("Retry-After");
    }
}
