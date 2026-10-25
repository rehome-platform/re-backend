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
import com.rehome.rehome.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberServiceImpl implements MemberService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^0\\d{9}$");

    private final AccountRepository accountRepository;
    private final MemberProfileRepository memberProfileRepository;

    @Override
    public MemberProfileResponse getMemberProfile(String email) {
        Account account = accountRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        MemberProfile memberProfile = memberProfileRepository.findByAccountId(account.getId())
                .orElseThrow(() -> new AppException(ErrorCode.MEMBER_PROFILE_NOT_FOUND));

        return MemberProfileResponse.builder()
                .accountId(account.getId())
                .email(account.getEmail())
                .phone(account.getPhone())
                .fullName(memberProfile.getFullName())
                .avatarUrl(memberProfile.getAvatarUrl())
                .memberCode(memberProfile.getMemberCode())
                .createdAt(account.getCreatedAt())
                .emailVerified(account.isEmailVerified())
                .build();
    }

    @Override
    @Transactional
    public MemberProfileUpdateResponse updateMemberProfile(String email, UpdateMemberProfileRequest request) {
        Account account = accountRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        MemberProfile memberProfile = memberProfileRepository.findByAccountId(account.getId())
                .orElseThrow(() -> new AppException(ErrorCode.MEMBER_PROFILE_NOT_FOUND));

        if (StringUtils.hasText(request.getFullName())) {
            memberProfile.setFullName(request.getFullName().trim());
        }

        if (StringUtils.hasText(request.getAvatarUrl())) {
            memberProfile.setAvatarUrl(request.getAvatarUrl().trim());
        }

        if (StringUtils.hasText(request.getPhone())) {
            String phone = request.getPhone().trim();
            if (!PHONE_PATTERN.matcher(phone).matches()) {
                throw new AppException(ErrorCode.INVALID_PHONE_FORMAT);
            }
            account.setPhone(phone);
        }

        memberProfileRepository.save(memberProfile);
        accountRepository.save(account);

        return MemberProfileUpdateResponse.builder()
                .accountId(account.getId())
                .fullName(memberProfile.getFullName())
                .phone(account.getPhone())
                .avatarUrl(memberProfile.getAvatarUrl())
                .build();
    }

    @Override
    public MemberCodeResponse getMemberCode(String email) {
        Account account = accountRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        MemberProfile memberProfile = memberProfileRepository.findByAccountId(account.getId())
                .orElseThrow(() -> new AppException(ErrorCode.MEMBER_PROFILE_NOT_FOUND));

        return MemberCodeResponse.builder()
                .memberCode(memberProfile.getMemberCode())
                .build();
    }
}