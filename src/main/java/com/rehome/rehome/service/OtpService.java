package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.ForgotPasswordRequest;
import com.rehome.rehome.dto.request.OtpRequest;
import com.rehome.rehome.dto.request.VerifyOtpRequest;
import com.rehome.rehome.dto.request.ResetPasswordRequest;
import com.rehome.rehome.dto.response.OtpResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.OtpPurpose;

public interface OtpService {

    void issue(Account account, OtpPurpose purpose);

    OtpResponse resend(OtpRequest request);

    OtpResponse verify(VerifyOtpRequest request);

    OtpResponse resetPassword(ResetPasswordRequest request);

    void forgotPassword(ForgotPasswordRequest request);

}
