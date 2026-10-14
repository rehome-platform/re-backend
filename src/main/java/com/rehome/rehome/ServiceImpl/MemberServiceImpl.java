package com.rehome.rehome.ServiceImpl;

import com.rehome.rehome.dto.response.MemberProfileResponse;
import com.rehome.rehome.entity.Account;
import com.rehome.rehome.entity.auth.MemberProfile;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.MemberProfileRepository;
import com.rehome.rehome.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberServiceImpl implements MemberService {

    private final AccountRepository accountRepository;
    private final MemberProfileRepository memberProfileRepository;

    @Override
    public MemberProfileResponse getMemberProfile(String email) {
        Account account = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        MemberProfile memberProfile = memberProfileRepository.findByAccountId(account.getId())
                .orElseThrow(() -> new IllegalArgumentException("Hồ sơ Member không tồn tại"));

        return MemberProfileResponse.builder()
                .accountId(account.getId())
                .email(account.getEmail())
                .phone(account.getPhone())
                .fullName(memberProfile.getFullName())
                .avatarUrl(memberProfile.getAvatarUrl())
                .memberCode(memberProfile.getMemberCode())
                .emailVerified(account.isEmailVerified())
                .build();
    }
}