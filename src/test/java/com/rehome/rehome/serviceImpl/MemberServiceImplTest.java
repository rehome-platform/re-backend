package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.request.UpdateMemberProfileRequest;
import com.rehome.rehome.dto.response.MemberCodeResponse;
import com.rehome.rehome.dto.response.MemberProfileResponse;
import com.rehome.rehome.dto.response.MemberProfileUpdateResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.MemberProfile;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.MemberProfileRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberServiceImplTest {

    private static final String EMAIL = "member@rehome.vn";

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private MemberProfileRepository memberProfileRepository;

    @InjectMocks
    private MemberServiceImpl service;

    private UUID accountId;
    private Account account;
    private MemberProfile profile;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();

        account = mock(Account.class);
        when(account.getId()).thenReturn(accountId);
        when(account.getEmail()).thenReturn(EMAIL);
        when(account.getPhone()).thenReturn("0900000000");
        when(account.isEmailVerified()).thenReturn(true);

        profile = mock(MemberProfile.class);
        when(profile.getFullName()).thenReturn("Nguyễn Văn A");
        when(profile.getAvatarUrl()).thenReturn("http://img/a.png");
        when(profile.getMemberCode()).thenReturn("MB001");
    }

    private void assertAppException(ThrowingCallable callable, ErrorCode expected) {
        assertThatThrownBy(callable)
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(expected);
    }

    private void givenAccountAndProfileExist() {
        when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(account));
        when(memberProfileRepository.findByAccountId(accountId)).thenReturn(Optional.of(profile));
    }

    private UpdateMemberProfileRequest request(String fullName, String avatarUrl, String phone) {
        UpdateMemberProfileRequest r = mock(UpdateMemberProfileRequest.class);
        when(r.getFullName()).thenReturn(fullName);
        when(r.getAvatarUrl()).thenReturn(avatarUrl);
        when(r.getPhone()).thenReturn(phone);
        return r;
    }

    // ================= getMemberProfile =================

    @Test
    void getMemberProfile_returnsProfile_whenFound() {
        givenAccountAndProfileExist();

        MemberProfileResponse res = service.getMemberProfile(EMAIL);

        assertThat(res.getAccountId()).isEqualTo(accountId);
        assertThat(res.getEmail()).isEqualTo(EMAIL);
        assertThat(res.getPhone()).isEqualTo("0900000000");
        assertThat(res.getFullName()).isEqualTo("Nguyễn Văn A");
        assertThat(res.getAvatarUrl()).isEqualTo("http://img/a.png");
        assertThat(res.getMemberCode()).isEqualTo("MB001");
    }

    @Test
    void getMemberProfileThrowsWhenAccountNotFound() {
        when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertAppException(() -> service.getMemberProfile(EMAIL), ErrorCode.ACCOUNT_NOT_FOUND);

        verify(memberProfileRepository, never()).findByAccountId(accountId);
    }

    @Test
    void getMemberProfileThrowsWhenProfileNotFound() {
        when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(account));
        when(memberProfileRepository.findByAccountId(accountId)).thenReturn(Optional.empty());

        assertAppException(() -> service.getMemberProfile(EMAIL), ErrorCode.MEMBER_PROFILE_NOT_FOUND);
    }

    // ================= updateMemberProfile =================

    @Test
    void updateMemberProfile_updatesAllFields_andTrimsInput() {
        givenAccountAndProfileExist();
        UpdateMemberProfileRequest req = request("  Trần B  ", "  http://img/b.png  ", " 0912345678 ");

        MemberProfileUpdateResponse res = service.updateMemberProfile(EMAIL, req);

        verify(profile).setFullName("Trần B");
        verify(profile).setAvatarUrl("http://img/b.png");
        verify(account).setPhone("0912345678");
        verify(memberProfileRepository).save(profile);
        verify(accountRepository).save(account);
        assertThat(res.getAccountId()).isEqualTo(accountId);
    }

    @Test
    void updateMemberProfile_skipsBlankFields() {
        givenAccountAndProfileExist();
        UpdateMemberProfileRequest req = request(null, "   ", "");

        service.updateMemberProfile(EMAIL, req);

        verify(profile, never()).setFullName(org.mockito.ArgumentMatchers.any());
        verify(profile, never()).setAvatarUrl(org.mockito.ArgumentMatchers.any());
        verify(account, never()).setPhone(org.mockito.ArgumentMatchers.any());
        // vẫn lưu lại
        verify(memberProfileRepository).save(profile);
        verify(accountRepository).save(account);
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "0123", "1234567890", "09123456789", "09abc45678", "+84912345678"})
    void updateMemberProfileRejectsInvalidPhoneFormat(String badPhone) {
        givenAccountAndProfileExist();
        UpdateMemberProfileRequest req = request(null, null, badPhone);

        assertAppException(() -> service.updateMemberProfile(EMAIL, req), ErrorCode.INVALID_PHONE_FORMAT);

        verify(account, never()).setPhone(org.mockito.ArgumentMatchers.any());
        verify(memberProfileRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(accountRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateMemberProfileThrowsWhenAccountNotFound() {
        when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        UpdateMemberProfileRequest req = request("A", null, null);

        assertAppException(() -> service.updateMemberProfile(EMAIL, req), ErrorCode.ACCOUNT_NOT_FOUND);
    }

    @Test
    void updateMemberProfileThrowsWhenProfileNotFound() {
        when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(account));
        when(memberProfileRepository.findByAccountId(accountId)).thenReturn(Optional.empty());
        UpdateMemberProfileRequest req = request("A", null, null);

        assertAppException(() -> service.updateMemberProfile(EMAIL, req), ErrorCode.MEMBER_PROFILE_NOT_FOUND);
    }

    // ================= getMemberCode =================

    @Test
    void getMemberCode_returnsCode_whenFound() {
        givenAccountAndProfileExist();

        MemberCodeResponse res = service.getMemberCode(EMAIL);

        assertThat(res.getMemberCode()).isEqualTo("MB001");
    }

    @Test
    void getMemberCodeThrowsWhenAccountNotFound() {
        when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertAppException(() -> service.getMemberCode(EMAIL), ErrorCode.ACCOUNT_NOT_FOUND);
    }

    @Test
    void getMemberCodeThrowsWhenProfileNotFound() {
        when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(account));
        when(memberProfileRepository.findByAccountId(accountId)).thenReturn(Optional.empty());

        assertAppException(() -> service.getMemberCode(EMAIL), ErrorCode.MEMBER_PROFILE_NOT_FOUND);
    }
}