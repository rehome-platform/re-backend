package com.rehome.rehome.serviceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.WarehouseHistoryResponse;
import com.rehome.rehome.entity.audit.AuditLog;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.ModeratorProfile;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.AuditLogRepository;
import com.rehome.rehome.repository.MemberProfileRepository;
import com.rehome.rehome.repository.ModeratorProfileRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class WarehouseHistoryServiceImplTest {

    @Mock AuditLogRepository auditLogRepository;
    @Mock ModeratorProfileRepository moderatorProfileRepository;
    @Mock MemberProfileRepository memberProfileRepository;
    @InjectMocks WarehouseHistoryServiceImpl service;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Warehouse warehouse = Warehouse.builder().id(UUID.randomUUID()).build();

    private Account moderator(Warehouse w) {
        return Account.builder().id(UUID.randomUUID()).email("mod@example.com").role(Role.MODERATOR)
                .warehouse(w).build();
    }

    @Test
    void list_usesWarehouseOfLoggedInModerator_andPagesWithGivenSize() {
        Account mod = moderator(warehouse);
        when(auditLogRepository.findWarehouseHistory(eq(warehouse.getId()), eq("PACKING"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.list(mod, "packing", 2, 50);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(auditLogRepository).findWarehouseHistory(eq(warehouse.getId()), eq("PACKING"), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void list_mapsToCleanDto_withoutExposingRawDetail() {
        Account mod = moderator(warehouse);
        UUID orderId = UUID.randomUUID();
        AuditLog log = AuditLog.builder().id(UUID.randomUUID()).actorAccount(mod)
                .actionType("SCAN_SKU").targetType("ORDER").targetId(orderId.toString())
                .detail(objectMapper.createObjectNode().put("label", "OD-1").put("secret", "internal"))
                .result("SUCCESS").auditGroup("PACKING").warehouseId(warehouse.getId())
                .createdAt(Instant.parse("2026-11-14T01:00:00Z")).build();
        when(auditLogRepository.findWarehouseHistory(eq(warehouse.getId()), eq("PACKING"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log)));
        ModeratorProfile profile = ModeratorProfile.builder().accountId(mod.getId()).fullName("Nguyễn Văn A").build();
        when(moderatorProfileRepository.findAllById(any())).thenReturn(List.of(profile));

        PageResponse<WarehouseHistoryResponse> result = service.list(mod, "PACKING", 0, 20);

        WarehouseHistoryResponse row = result.getContent().get(0);
        assertThat(row.getTarget()).isEqualTo("ORDER OD-1");
        assertThat(row.getTargetType()).isEqualTo("ORDER");
        assertThat(row.getTargetId()).isEqualTo(orderId.toString());
        assertThat(row.getAction()).isEqualTo("SCAN_SKU");
        assertThat(row.getActor()).isEqualTo("Nguyễn Văn A");
        assertThat(row.getResult()).isEqualTo("SUCCESS");
        assertThat(row.getTimestamp()).isEqualTo(Instant.parse("2026-11-14T01:00:00Z"));
        var json = objectMapper.copy().findAndRegisterModules().valueToTree(row);
        assertThat(json.get("target").asText()).isEqualTo("ORDER OD-1");
        assertThat(json.get("targetType").asText()).isEqualTo("ORDER");
        assertThat(json.get("targetId").asText()).isEqualTo(orderId.toString());
        assertThat(json.has("detail")).isFalse();
        assertThat(json.toString()).doesNotContain("secret", "internal");
    }

    @ParameterizedTest
    @CsvSource({
            "ORDER, 11111111-1111-4111-8111-111111111111, OD-2026-000019, PACKING",
            "CONSIGNMENT_FORM, 22222222-2222-4222-8222-222222222222, KG-000019, INSPECTION",
            "PRODUCT, Q7-AK-000845, Q7-AK-000845, INVENTORY"
    })
    void list_returnsRawTargetIdSeparatelyFromDisplayLabel(String targetType, String targetId,
                                                          String label, String group) {
        Account mod = moderator(warehouse);
        AuditLog log = AuditLog.builder().actorAccount(mod).targetType(targetType).targetId(targetId)
                .detail(objectMapper.createObjectNode().put("label", label)).build();
        when(auditLogRepository.findWarehouseHistory(eq(warehouse.getId()), eq(group), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log)));

        WarehouseHistoryResponse row = service.list(mod, group, 0, 20).getContent().get(0);

        assertThat(row.getTarget()).isEqualTo(targetType + " " + label);
        assertThat(row.getTargetType()).isEqualTo(targetType);
        assertThat(row.getTargetId()).isEqualTo(targetId);
    }

    @Test
    void list_actorWithoutProfile_fallsBackToEmail() {
        Account mod = moderator(warehouse);
        AuditLog log = AuditLog.builder().id(UUID.randomUUID()).actorAccount(mod).actionType("STOCK_IN")
                .targetType("PRODUCT").targetId("SKU-1").result("ON_SALE").build();
        when(auditLogRepository.findWarehouseHistory(any(), eq("INSPECTION"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log)));

        PageResponse<WarehouseHistoryResponse> result = service.list(mod, "INSPECTION", 0, 20);

        assertThat(result.getContent().get(0).getActor()).isEqualTo("mod@example.com");
        // Không có label trong detail thì target dùng targetId
        assertThat(result.getContent().get(0).getTarget()).isEqualTo("PRODUCT SKU-1");
        assertThat(result.getContent().get(0).getTargetType()).isEqualTo("PRODUCT");
        assertThat(result.getContent().get(0).getTargetId()).isEqualTo("SKU-1");
    }

    @Test
    void list_rejectsSizeAbove50() {
        assertThatThrownBy(() -> service.list(moderator(warehouse), "PACKING", 0, 51))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(auditLogRepository);
    }

    @Test
    void list_rejectsUnknownOrMissingType() {
        assertThatThrownBy(() -> service.list(moderator(warehouse), "SOMETHING", 0, 20))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.list(moderator(warehouse), null, 0, 20))
                .isInstanceOf(BadRequestException.class);
        verify(auditLogRepository, never()).findWarehouseHistory(any(), any(), any());
    }

    @Test
    void list_rejectsModeratorWithoutWarehouse() {
        assertThatThrownBy(() -> service.list(moderator(null), "PACKING", 0, 20))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.MODERATOR_NO_WAREHOUSE);
    }
}
