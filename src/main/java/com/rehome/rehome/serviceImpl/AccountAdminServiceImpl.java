package com.rehome.rehome.serviceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rehome.rehome.dto.response.AccountStatusResponse;
import com.rehome.rehome.entity.audit.AuditLog;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.AccountStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.AuditLogRepository;
import com.rehome.rehome.service.AccountAdminService;
import com.rehome.rehome.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountAdminServiceImpl implements AccountAdminService {

    private final AccountRepository accountRepository;
    private final RefreshTokenService refreshTokenService;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public AccountStatusResponse ban(Account admin, UUID targetId, String reason) {
        Account target = accountRepository.findById(targetId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (target.getRole() == Role.ADMIN || target.getId().equals(admin.getId())) {
            throw new AppException(ErrorCode.CANNOT_BAN_ADMIN_OR_SELF);
        }
        if (target.getStatus() == AccountStatus.BANNED) {
            throw new AppException(ErrorCode.ACCOUNT_ALREADY_BANNED);
        }

        target.setStatus(AccountStatus.BANNED);
        target.setBanReason(reason.trim());
        accountRepository.save(target);

        // Đá khỏi các phiên đang đăng nhập; access token còn hạn bị JwtAuthenticationFilter chặn
        refreshTokenService.revokeAllAccountTokens(target);

        ObjectNode detail = objectMapper.createObjectNode();
        detail.put("reason", target.getBanReason());
        writeAudit(admin, "BAN_ACCOUNT", target, detail);

        return toResponse(target);
    }

    @Override
    @Transactional
    public AccountStatusResponse unban(Account admin, UUID targetId) {
        Account target = accountRepository.findById(targetId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (target.getStatus() != AccountStatus.BANNED) {
            throw new AppException(ErrorCode.ACCOUNT_NOT_BANNED);
        }

        ObjectNode detail = objectMapper.createObjectNode();
        detail.put("previousReason", target.getBanReason());

        target.setStatus(AccountStatus.ACTIVE);
        target.setBanReason(null);
        accountRepository.save(target);

        writeAudit(admin, "UNBAN_ACCOUNT", target, detail);

        return toResponse(target);
    }

    private void writeAudit(Account admin, String action, Account target, ObjectNode detail) {
        auditLogRepository.save(AuditLog.builder()
                .actorAccount(admin)
                .actionType(action)
                .targetType("ACCOUNT")
                .targetId(target.getId().toString())
                .detail(detail)
                .build());
    }

    private AccountStatusResponse toResponse(Account account) {
        return AccountStatusResponse.builder()
                .accountId(account.getId().toString())
                .status(account.getStatus())
                .banReason(account.getBanReason())
                .build();
    }
}
