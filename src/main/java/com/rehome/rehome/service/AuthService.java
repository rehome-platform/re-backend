package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.*;
import com.rehome.rehome.dto.request.organization.RegisterOrganizationRequest;
import com.rehome.rehome.dto.response.AuthResponse;
import com.rehome.rehome.dto.response.OtpResponse;
import com.rehome.rehome.dto.response.RegisterOrganizationResponse;
import com.rehome.rehome.entity.auth.Account;

public interface AuthService {

    OtpResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse loginWithGoogle(GoogleLoginRequest request);

    AuthResponse refreshToken(String requestRefreshToken);

    void logout(String email);

    RegisterOrganizationResponse registerOrganization(Account account,RegisterOrganizationRequest request);

    void changePassword(Account account, ChangePasswordRequest changePasswordRequest);
}
