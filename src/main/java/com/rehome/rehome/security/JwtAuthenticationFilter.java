package com.rehome.rehome.security;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.AccountStatus;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter chạy trước mỗi request: đọc header Authorization: Bearer <token>,
 * validate JWT, nếu hợp lệ và account còn ACTIVE thì set Authentication vào SecurityContext.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AccountDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        final String authHeader = request.getHeader(HEADER_NAME);

        if (authHeader == null || !authHeader.startsWith(PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }
        final String jwt = authHeader.substring(PREFIX.length());
        boolean banned = false;

        try {
            final String email = jwtService.extractEmail(jwt);

            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                Account account = (Account) userDetailsService.loadUserByUsername(email);

                if (jwtService.isTokenValid(jwt, account)) {
                    if (account.getStatus() != AccountStatus.ACTIVE) {
                        banned = true;
                    } else {
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                account, null, account.getAuthorities());
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                }
            }
        } catch (ExpiredJwtException ex) {
            SecurityContextHolder.clearContext();
            writeError(response, ErrorCode.ACCESS_TOKEN_EXPIRED);
            return;
        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException ex) {
            SecurityContextHolder.clearContext();
            writeError(response, ErrorCode.ACCESS_TOKEN_INVALID);
            return;
        }

        if (banned) {
            writeError(response, ErrorCode.ACCOUNT_BANNED);
            return; // dừng chain, không cho request đi tiếp
        }

        filterChain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, ErrorCode ec) throws IOException {
        response.setStatus(ec.getStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        String body = String.format(
                "{\"result\":null,\"isSuccess\":false,\"statusCode\":%d,\"code\":\"%s\",\"message\":\"%s\"}",
                ec.getStatus().value(), ec.name(), ec.getDefaultMessage());
        response.getWriter().write(body);
    }
}