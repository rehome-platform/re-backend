package com.rehome.rehome.controller;

import com.rehome.rehome.dto.response.AccountMeResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.service.AccountMeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AccountController {

    private final AccountMeService accountMeService;

    @GetMapping("/api/account/me")
    public AccountMeResponse me(@AuthenticationPrincipal Account account) {
        return accountMeService.getMe(account.getId());
    }
}