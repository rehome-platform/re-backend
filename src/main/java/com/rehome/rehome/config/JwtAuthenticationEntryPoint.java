package com.rehome.rehome.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Trả 401 theo format ApiResponse khi request chưa xác thực
 * (không có token, token sai hoặc hết hạn).
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException ex) throws IOException {
        // Filter đã ghi lý do (token hết hạn / không hợp lệ) thì trả đúng mã đó, mobile dựa vào để refresh
        Object reason = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
        ErrorCode ec = reason instanceof ErrorCode code ? code : ErrorCode.UNAUTHORIZED;

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(ec));
    }
}