package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.ForgotPasswordRequest;
import com.rehome.rehome.dto.request.GoogleLoginRequest;
import com.rehome.rehome.dto.request.LoginRequest;
import com.rehome.rehome.dto.request.RegisterRequest;
import com.rehome.rehome.dto.request.organization.RegisterOrganizationRequest;
import com.rehome.rehome.dto.response.AuthResponse;
import com.rehome.rehome.dto.response.OtpResponse;
import com.rehome.rehome.dto.response.RegisterOrganizationResponse;

public interface AuthService {

    OtpResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse loginWithGoogle(GoogleLoginRequest request);

    AuthResponse refreshToken(String requestRefreshToken);

    void logout(String email);

    RegisterOrganizationResponse registerOrganization(RegisterOrganizationRequest request);
}
