package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.LoginRequest;
import com.rehome.rehome.dto.request.RegisterRequest;
import com.rehome.rehome.dto.response.AuthResponse;
import com.rehome.rehome.dto.response.OtpResponse;

public interface AuthService {

    OtpResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refreshToken(String requestRefreshToken);

    void logout(String email);
}
