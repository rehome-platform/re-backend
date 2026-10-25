package com.rehome.rehome.controller;

import com.rehome.rehome.dto.response.MyWarehouseResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import org.apache.tomcat.util.net.openssl.ciphers.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/moderator")
@RequiredArgsConstructor
public class ModeratorProfileController {
    private final WarehouseService service;

}
