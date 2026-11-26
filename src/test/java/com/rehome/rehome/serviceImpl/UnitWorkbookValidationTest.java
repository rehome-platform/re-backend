package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.request.LoginRequest;
import com.rehome.rehome.dto.request.RegisterRequest;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.OtpPurpose;
import com.rehome.rehome.repository.*;
import com.rehome.rehome.mapper.OrganizationProfileMapper;
import com.rehome.rehome.security.GoogleTokenVerifier;
import com.rehome.rehome.security.JwtService;
import com.rehome.rehome.service.*;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Missing request-validation/normalization evidence for the unit-test workbook. */
@ExtendWith(MockitoExtension.class)
class UnitWorkbookValidationTest {
    static ValidatorFactory validators;
    @BeforeAll static void beginValidation() { validators = Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void endValidation() { validators.close(); }
    @Mock AccountRepository accounts;
    @Mock PasswordEncoder encoder;
    @Mock AuthenticationManager authentication;
    @Mock JwtService jwt;
    @Mock RefreshTokenService refresh;
    @Mock OtpService otp;
    @Mock MemberProfileRepository profiles;
    @Mock OrganizationProfileRepository organizations;
    @Mock OrganizationProfileMapper mapper;
    @Mock GoogleTokenVerifier google;
    @Mock WalletRepository wallets;
    @Mock ContentModerationService moderation;
    @Mock OtpRateLimiter limiter;
    @InjectMocks AuthServiceImpl service;

    private RegisterRequest request() {
        return new RegisterRequest("member@example.test", "123456", "Workbook Member", null);
    }

    @Test void register_acceptsExactlySixCharacterPassword() {
        var request = request();
        assertThat(validators.getValidator().validate(request)).isEmpty();
        when(encoder.encode("123456")).thenReturn("hashed");
        assertThat(service.registerMember(request)).isNotNull();
        verify(otp).issue(any(Account.class), eq(OtpPurpose.REGISTER));
        verify(wallets).save(any());
    }

    @Test void register_rejectsFiveCharacterPasswordAtDtoBoundary() {
        var request = request(); request.setPassword("12345");
        assertThat(validators.getValidator().validate(request))
                .anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("password"));
        verifyNoInteractions(accounts, otp, wallets);
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"   ", "invalid-email", "a@"})
    void register_rejectsBlankOrMalformedEmailAtDtoBoundary(String email) {
        var request = request(); request.setEmail(email);
        assertThat(validators.getValidator().validate(request))
                .anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("email"));
        verifyNoInteractions(accounts, otp, wallets);
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"   ", "invalid-email", "a@"})
    void login_rejectsBlankOrMalformedEmailAtDtoBoundary(String email) {
        assertThat(validators.getValidator().validate(new LoginRequest(email, "123456")))
                .anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("email"));
        verifyNoInteractions(authentication, accounts, jwt);
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"   "})
    void login_rejectsBlankPasswordAtDtoBoundary(String password) {
        assertThat(validators.getValidator().validate(new LoginRequest("member@example.test", password)))
                .anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("password"));
        verifyNoInteractions(authentication, accounts, jwt);
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"   ", "\t"})
    void register_normalizesMissingOrWhitespacePhoneToNull(String phone) {
        var request = request(); request.setPhone(phone);
        when(encoder.encode("123456")).thenReturn("hashed");
        service.registerMember(request);
        var saved = ArgumentCaptor.forClass(Account.class);
        verify(accounts).save(saved.capture());
        assertThat(saved.getValue().getPhone()).isNull();
        verify(accounts, never()).existsByPhone(any());
        verify(profiles).save(any()); verify(wallets).save(any());
        verify(otp).issue(saved.getValue(), OtpPurpose.REGISTER);
    }

    @Test void register_trimsProvidedPhoneBeforeUniquenessCheckAndSave() {
        var request = request(); request.setPhone(" 0909000000 ");
        when(encoder.encode("123456")).thenReturn("hashed");
        service.registerMember(request);
        verify(accounts).existsByPhone("0909000000");
        var saved = ArgumentCaptor.forClass(Account.class);
        verify(accounts).save(saved.capture());
        assertThat(saved.getValue().getPhone()).isEqualTo("0909000000");
    }
}
