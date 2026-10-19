package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.*;
import com.rehome.rehome.dto.request.organization.RegisterOrganizationRequest;
import com.rehome.rehome.dto.response.AuthResponse;
import com.rehome.rehome.dto.response.OrganizationResubmitResponse;
import com.rehome.rehome.dto.response.OtpResponse;
import com.rehome.rehome.dto.response.RegisterOrganizationResponse;
import com.rehome.rehome.entity.auth.Account;

public interface AuthService {

    OtpResponse registerMember(RegisterRequest request);

    OtpResponse registerOrganization(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse loginWithGoogle(GoogleLoginRequest request);

    AuthResponse refreshToken(String requestRefreshToken);

    void logout(String email);

    void changePassword(Account account, ChangePasswordRequest changePasswordRequest);


}
