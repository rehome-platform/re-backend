package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.UpdateMemberProfileRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.MemberCodeResponse;
import com.rehome.rehome.dto.response.MemberProfileResponse;
import com.rehome.rehome.dto.response.MemberProfileUpdateResponse;
import com.rehome.rehome.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/me")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<MemberProfileResponse>> getMemberProfile(Authentication authentication) {
        String email = authentication.getName();
        MemberProfileResponse profile = memberService.getMemberProfile(email);
        return ResponseEntity.ok(ApiResponse.success(profile));
    }

    @PatchMapping("/me")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<MemberProfileUpdateResponse>> updateMemberProfile(
            Authentication authentication,
            @RequestBody UpdateMemberProfileRequest request) {
        String email = authentication.getName();
        MemberProfileUpdateResponse updated = memberService.updateMemberProfile(email, request);
        return ResponseEntity.ok(ApiResponse.success(updated, "Cập nhật hồ sơ thành công"));
    }

    @GetMapping("/me/code")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<MemberCodeResponse>> getMemberCode(Authentication authentication) {
        String email = authentication.getName();
        MemberCodeResponse code = memberService.getMemberCode(email);
        return ResponseEntity.ok(ApiResponse.success(code));
    }
}