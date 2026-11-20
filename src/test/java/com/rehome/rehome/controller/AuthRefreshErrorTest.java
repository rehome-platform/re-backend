package com.rehome.rehome.controller;

import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.exception.GlobalExceptionHandler;
import com.rehome.rehome.exception.TokenRefreshException;
import com.rehome.rehome.service.AuthService;
import com.rehome.rehome.service.OtpRateLimiter;
import com.rehome.rehome.service.OtpService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthRefreshErrorTest {
    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = {"REFRESH_TOKEN_EXPIRED", "REFRESH_TOKEN_REVOKED", "REFRESH_TOKEN_NOT_FOUND"})
    void refreshErrorsHaveConsistent401CodeAndResponseShape(ErrorCode code) throws Exception {
        AuthService auth = mock(AuthService.class);
        when(auth.refreshToken("bad-token")).thenThrow(new AppException(code));
        var mvc = MockMvcBuilders.standaloneSetup(new AuthController(auth, mock(OtpService.class), mock(OtpRateLimiter.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"bad-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.statusCode").value(401))
                .andExpect(jsonPath("$.code").value(code.name()))
                .andExpect(jsonPath("$.isSuccess").value(false));
    }

    @Test void legacyExceptionNeverEchoesTheRefreshToken() {
        TokenRefreshException ex = new TokenRefreshException("secret-refresh-token", "Expired");
        assertThat(ex.getMessage()).doesNotContain("secret-refresh-token");
    }
}
