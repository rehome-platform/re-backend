package com.rehome.rehome.serviceImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rehome.rehome.dto.response.AccountMeResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.ModeratorProfile;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.ModeratorProfileRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AccountMeServiceImplTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private ModeratorProfileRepository moderatorProfileRepository;

    @InjectMocks
    private AccountMeServiceImpl service;

    private UUID accountId;
    private Warehouse accountWarehouse;
    private Warehouse profileWarehouse;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();

        accountWarehouse = new Warehouse();
        accountWarehouse.setId(UUID.randomUUID());
        accountWarehouse.setName("Kho Quận 7");

        profileWarehouse = new Warehouse();
        profileWarehouse.setId(UUID.randomUUID());
        profileWarehouse.setName("Kho Thủ Đức");
    }

    private Account buildAccount(Role role, Warehouse warehouse) {
        return Account.builder()
                .id(accountId)
                .email("moderator1@rehome.test")
                .role(role)
                .warehouse(warehouse)
                .build();
    }

    private ModeratorProfile buildProfile(Warehouse warehouse) {
        return ModeratorProfile.builder()
                .accountId(accountId)
                .fullName("Moderator 1")
                .moderatorCode("MOD-01-001")
                .warehouse(warehouse)
                .build();
    }

    @Test
    void getMe_moderatorWithWarehouseAndProfile_returnsAllFields() {
        when(accountRepository.findWithWarehouseById(accountId))
                .thenReturn(Optional.of(buildAccount(Role.MODERATOR, accountWarehouse)));
        when(moderatorProfileRepository.findById(accountId))
                .thenReturn(Optional.of(buildProfile(profileWarehouse)));

        AccountMeResponse result = service.getMe(accountId);

        assertEquals(accountId, result.id());
        assertEquals("moderator1@rehome.test", result.email());
        assertEquals("[ROLE_MODERATOR]", result.authorities());
        assertEquals("MODERATOR", result.role());
        // Ưu tiên kho từ accounts.warehouse_id, không lấy từ profile
        assertEquals(accountWarehouse.getId(), result.warehouseId());
        assertEquals("Kho Quận 7", result.warehouseName());
        assertEquals("Moderator 1", result.fullName());
        assertEquals("MOD-01-001", result.moderatorCode());
    }

    @Test
    void getMe_moderatorAccountWarehouseNull_fallsBackToProfileWarehouse() {
        when(accountRepository.findWithWarehouseById(accountId))
                .thenReturn(Optional.of(buildAccount(Role.MODERATOR, null)));
        when(moderatorProfileRepository.findById(accountId))
                .thenReturn(Optional.of(buildProfile(profileWarehouse)));

        AccountMeResponse result = service.getMe(accountId);

        assertEquals(profileWarehouse.getId(), result.warehouseId());
        assertEquals("Kho Thủ Đức", result.warehouseName());
        assertEquals("Moderator 1", result.fullName());
        assertEquals("MOD-01-001", result.moderatorCode());
    }

    @Test
    void getMe_moderatorWithoutProfile_returnsWarehouseButNullProfileFields() {
        // Trường hợp đang gặp trên Swagger: có kho nhưng chưa có dòng moderator_profiles
        when(accountRepository.findWithWarehouseById(accountId))
                .thenReturn(Optional.of(buildAccount(Role.MODERATOR, accountWarehouse)));
        when(moderatorProfileRepository.findById(accountId)).thenReturn(Optional.empty());

        AccountMeResponse result = service.getMe(accountId);

        assertEquals(accountWarehouse.getId(), result.warehouseId());
        assertEquals("Kho Quận 7", result.warehouseName());
        assertNull(result.fullName());
        assertNull(result.moderatorCode());
    }

    @Test
    void getMe_moderatorWithNoWarehouseAndNoProfile_returnsNullWarehouse() {
        when(accountRepository.findWithWarehouseById(accountId))
                .thenReturn(Optional.of(buildAccount(Role.MODERATOR, null)));
        when(moderatorProfileRepository.findById(accountId)).thenReturn(Optional.empty());

        AccountMeResponse result = service.getMe(accountId);

        assertEquals("MODERATOR", result.role());
        assertNull(result.warehouseId());
        assertNull(result.warehouseName());
        assertNull(result.fullName());
        assertNull(result.moderatorCode());
    }

    @Test
    void getMe_member_doesNotQueryProfileAndReturnsNullWarehouseInfo() {
        when(accountRepository.findWithWarehouseById(accountId))
                .thenReturn(Optional.of(buildAccount(Role.MEMBER, null)));

        AccountMeResponse result = service.getMe(accountId);

        assertEquals(accountId, result.id());
        assertEquals("MEMBER", result.role());
        assertEquals("[ROLE_MEMBER]", result.authorities());
        assertNull(result.warehouseId());
        assertNull(result.warehouseName());
        assertNull(result.fullName());
        assertNull(result.moderatorCode());
        verify(moderatorProfileRepository, never()).findById(any());
    }

    @Test
    void getMe_accountNotFound_throwsUnauthorized() {
        when(accountRepository.findWithWarehouseById(accountId)).thenReturn(Optional.empty());

        ResponseStatusException ex =
                assertThrows(ResponseStatusException.class, () -> service.getMe(accountId));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verify(moderatorProfileRepository, never()).findById(any());
    }
}