package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.organization.RegisterOrganizationRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import com.rehome.rehome.dto.response.RegisterOrganizationResponse;
import com.rehome.rehome.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class OrganizationProfileController {
    private final AuthService authService;

    @PostMapping("/register/organization")
    @Operation(summary = "Đăng ký tài khoản tổ chức")
    public ResponseEntity<RegisterOrganizationResponse> registerOrganization(
            @Valid @RequestBody RegisterOrganizationRequest request) {
        return ResponseEntity.status(201).body(authService.registerOrganization(request));
    }
}
