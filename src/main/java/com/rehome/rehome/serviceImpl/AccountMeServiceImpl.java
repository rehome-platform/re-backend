package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.response.AccountMeResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.ModeratorProfile;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.ModeratorProfileRepository;
import com.rehome.rehome.service.AccountMeService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountMeServiceImpl implements AccountMeService {
    private final AccountRepository accountRepository;
    private final ModeratorProfileRepository moderatorProfileRepository;

    @Transactional
    public AccountMeResponse getMe(UUID accountId) {
        Account account = accountRepository.findWithWarehouseById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        Warehouse warehouse = null;
        ModeratorProfile profile = null;

        if (account.getRole() == Role.MODERATOR) {
            profile = moderatorProfileRepository.findById(accountId).orElse(null);
            // Ưu tiên accounts.warehouse_id (giống getManagedWarehouses), fallback sang moderator_profiles
            warehouse = account.getWarehouse() != null
                    ? account.getWarehouse()
                    : (profile != null ? profile.getWarehouse() : null);
        }

        return new AccountMeResponse(
                account.getId(),
                account.getEmail(),
                account.getAuthorities().toString(),
                account.getRole().name(),
                warehouse != null ? warehouse.getId() : null,
                warehouse != null ? warehouse.getName() : null,
                profile != null ? profile.getFullName() : null,
                profile != null ? profile.getModeratorCode() : null
        );
    }
}
