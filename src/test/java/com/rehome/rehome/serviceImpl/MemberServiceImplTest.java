package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.request.UpdateMemberProfileRequest;
import com.rehome.rehome.dto.response.MemberCodeResponse;
import com.rehome.rehome.dto.response.MemberProfileResponse;
import com.rehome.rehome.dto.response.MemberProfileUpdateResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.MemberProfile;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.MemberProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceImplTest {

    @Mock AccountRepository accountRepository;
    @Mock MemberProfileRepository memberProfileRepository;

    @InjectMocks
    MemberServiceImpl memberService;

    Account account;
    MemberProfile profile;
    String email;

    @BeforeEach void setup() {
        email = "member@example.com";
        account = Account.builder()
                .id(UUID.randomUUID())
                .email(email)
                .phone("0900000000")
                .emailVerified(true)
                .build();
        profile = MemberProfile.builder()
                .accountId(account.getId())
                .account(account)
                .fullName("Nguyen Van A")
                .avatarUrl("https://example.com/avatar.png")
                .memberCode("MEM-ABC123")
                .build();
    }

    // ---------- getMemberProfile ----------

    @Test void getMemberProfileReturnsFullProfile() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(account));
        when(memberProfileRepository.findByAccountId(account.getId())).thenReturn(Optional.of(profile));

        MemberProfileResponse result = memberService.getMemberProfile(email);

        assertThat(result.getAccountId()).isEqualTo(account.getId());
        assertThat(result.getEmail()).isEqualTo(email);
        assertThat(result.getPhone()).isEqualTo("0900000000");
        assertThat(result.getFullName()).isEqualTo("Nguyen Van A");
        assertThat(result.getAvatarUrl()).isEqualTo("https://example.com/avatar.png");
        assertThat(result.getMemberCode()).isEqualTo("MEM-ABC123");
        assertThat(result.isEmailVerified()).isTrue();
    }

    @Test void getMemberProfileThrowsWhenAccountNotFound() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.getMemberProfile(email))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account không tồn tại");
    }

    @Test void getMemberProfileThrowsWhenProfileNotFound() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(account));
        when(memberProfileRepository.findByAccountId(account.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.getMemberProfile(email))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Hồ sơ Member không tồn tại");
    }

    // ---------- updateMemberProfile ----------

    @Test void updateMemberProfileUpdatesAllProvidedFields() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(account));
        when(memberProfileRepository.findByAccountId(account.getId())).thenReturn(Optional.of(profile));

        UpdateMemberProfileRequest request = new UpdateMemberProfileRequest(
                "Tran Thi B", "0912345678", "https://example.com/new.png");

        MemberProfileUpdateResponse result = memberService.updateMemberProfile(email, request);

        assertThat(result.getFullName()).isEqualTo("Tran Thi B");
        assertThat(result.getPhone()).isEqualTo("0912345678");
        assertThat(result.getAvatarUrl()).isEqualTo("https://example.com/new.png");
        assertThat(account.getPhone()).isEqualTo("0912345678");
        assertThat(profile.getFullName()).isEqualTo("Tran Thi B");
        verify(memberProfileRepository).save(profile);
        verify(accountRepository).save(account);
    }

    @Test void updateMemberProfilePartialUpdateKeepsOtherFieldsUnchanged() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(account));
        when(memberProfileRepository.findByAccountId(account.getId())).thenReturn(Optional.of(profile));

        UpdateMemberProfileRequest request = new UpdateMemberProfileRequest(null, null, null);

        MemberProfileUpdateResponse result = memberService.updateMemberProfile(email, request);

        assertThat(result.getFullName()).isEqualTo("Nguyen Van A");
        assertThat(result.getPhone()).isEqualTo("0900000000");
        assertThat(result.getAvatarUrl()).isEqualTo("https://example.com/avatar.png");
    }

    @Test void updateMemberProfileTrimsWhitespaceInFields() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(account));
        when(memberProfileRepository.findByAccountId(account.getId())).thenReturn(Optional.of(profile));

        UpdateMemberProfileRequest request = new UpdateMemberProfileRequest("  Le Van C  ", "  0987654321  ", null);

        MemberProfileUpdateResponse result = memberService.updateMemberProfile(email, request);

        assertThat(result.getFullName()).isEqualTo("Le Van C");
        assertThat(result.getPhone()).isEqualTo("0987654321");
    }

    @Test void updateMemberProfileRejectsInvalidPhoneFormat() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(account));
        when(memberProfileRepository.findByAccountId(account.getId())).thenReturn(Optional.of(profile));

        UpdateMemberProfileRequest request = new UpdateMemberProfileRequest(null, "12345", null);

        assertThatThrownBy(() -> memberService.updateMemberProfile(email, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Số điện thoại không đúng định dạng.");

        assertThat(account.getPhone()).isEqualTo("0900000000");
        verify(memberProfileRepository, never()).save(any());
        verify(accountRepository, never()).save(any());
    }

    @Test void updateMemberProfileThrowsWhenAccountNotFound() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.updateMemberProfile(email, new UpdateMemberProfileRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account không tồn tại");
    }

    @Test void updateMemberProfileThrowsWhenProfileNotFound() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(account));
        when(memberProfileRepository.findByAccountId(account.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.updateMemberProfile(email, new UpdateMemberProfileRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Hồ sơ Member không tồn tại");
    }

    // ---------- getMemberCode ----------

    @Test void getMemberCodeReturnsCode() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(account));
        when(memberProfileRepository.findByAccountId(account.getId())).thenReturn(Optional.of(profile));

        MemberCodeResponse result = memberService.getMemberCode(email);

        assertThat(result.getMemberCode()).isEqualTo("MEM-ABC123");
        assertThat(result.getQrPayload()).isEqualTo("REHOME:MEMBER:v1:MEM-ABC123");
    }

    @Test void getMemberCodeThrowsWhenAccountNotFound() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.getMemberCode(email))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account không tồn tại");
    }

    @Test void getMemberCodeThrowsWhenProfileNotFound() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(account));
        when(memberProfileRepository.findByAccountId(account.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.getMemberCode(email))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Hồ sơ Member không tồn tại");
    }
}
