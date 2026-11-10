package com.rehome.rehome.serviceImpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rehome.rehome.component.AuditLogger;
import com.rehome.rehome.constant.SystemConfigCatalog;
import com.rehome.rehome.constant.SystemConfigKeys;
import com.rehome.rehome.dto.response.SystemConfigItemResponse;
import com.rehome.rehome.dto.response.SystemConfigResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.marketplace.SystemConfig;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.SystemConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemConfigAdminServiceImplTest {

    @Mock private SystemConfigRepository systemConfigRepository;
    @Mock private AuditLogger auditLogger;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private SystemConfigAdminServiceImpl service;

    private Account admin;

    @BeforeEach
    void setUp() {
        admin = Account.builder().email("admin@rehome.vn").build();
    }

    private SystemConfigItemResponse item(List<SystemConfigItemResponse> items, String key) {
        return items.stream().filter(i -> i.getKey().equals(key)).findFirst().orElseThrow();
    }

    // ==================== list (GET /api/admin/config) ====================

    @Test
    @DisplayName("list: DB chưa có dòng nào -> trả đủ mọi key trong catalog bằng giá trị mặc định")
    void list_emptyDb_returnsAllDefaults() {
        when(systemConfigRepository.findAll()).thenReturn(List.of());

        List<SystemConfigItemResponse> items = service.list();

        assertThat(items).hasSize(SystemConfigCatalog.defaults().size());
        assertThat(items).allSatisfy(i -> {
            assertThat(i.isUsingDefault()).isTrue();
            assertThat(i.getValue()).isEqualTo(i.getDefaultValue());
            assertThat(i.getUpdatedBy()).isNull();
            assertThat(i.getUpdatedAt()).isNull();
        });
        SystemConfigItemResponse minPayout = item(items, SystemConfigKeys.MIN_PAYOUT_AMOUNT);
        assertThat(minPayout.getValue()).isEqualTo("50000");
        assertThat(minPayout.getType()).isEqualTo("DECIMAL");
        SystemConfigItemResponse maxPerDay = item(items, SystemConfigKeys.MAX_PAYOUT_REQUESTS_PER_DAY);
        assertThat(maxPerDay.getValue()).isEqualTo("3");
        assertThat(maxPerDay.getType()).isEqualTo("INT");
    }

    @Test
    @DisplayName("list: key đã có dòng trong DB -> trả giá trị DB kèm người sửa, các key khác vẫn là mặc định")
    void list_storedRow_overridesDefault() {
        Instant at = Instant.parse("2026-11-01T03:00:00Z");
        when(systemConfigRepository.findAll()).thenReturn(List.of(SystemConfig.builder()
                .key(SystemConfigKeys.MIN_PAYOUT_AMOUNT).value("70000").updatedBy(admin).updatedAt(at).build()));

        List<SystemConfigItemResponse> items = service.list();

        SystemConfigItemResponse minPayout = item(items, SystemConfigKeys.MIN_PAYOUT_AMOUNT);
        assertThat(minPayout.getValue()).isEqualTo("70000");
        assertThat(minPayout.getDefaultValue()).isEqualTo("50000");
        assertThat(minPayout.isUsingDefault()).isFalse();
        assertThat(minPayout.getUpdatedBy()).isEqualTo("admin@rehome.vn");
        assertThat(minPayout.getUpdatedAt()).isEqualTo(at);
        assertThat(item(items, SystemConfigKeys.MAX_PAYOUT_REQUESTS_PER_DAY).isUsingDefault()).isTrue();
    }

    @Test
    @DisplayName("list: dòng DB có key ngoài catalog (vd force_payout_failure) -> không lộ ra API")
    void list_ignoresKeysOutsideCatalog() {
        when(systemConfigRepository.findAll()).thenReturn(List.of(SystemConfig.builder()
                .key("force_payout_failure").value("true").updatedBy(admin).build()));

        List<SystemConfigItemResponse> items = service.list();

        assertThat(items).hasSize(SystemConfigCatalog.defaults().size());
        assertThat(items).noneMatch(i -> i.getKey().equals("force_payout_failure"));
    }

    // ==================== update với 2 key mới ====================

    @Test
    @DisplayName("update min_payout_amount: key chưa có dòng -> tạo dòng mới, trim giá trị, ghi audit log")
    void update_minPayout_createsRow() {
        when(systemConfigRepository.findByKey(SystemConfigKeys.MIN_PAYOUT_AMOUNT)).thenReturn(Optional.empty());
        when(systemConfigRepository.save(any(SystemConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        SystemConfigResponse response = service.update(admin, SystemConfigKeys.MIN_PAYOUT_AMOUNT, " 100000 ");

        assertThat(response.getKey()).isEqualTo("min_payout_amount");
        assertThat(response.getValue()).isEqualTo("100000");
        verify(auditLogger).log(eq(admin), eq("UPDATE_CONFIG"), eq("SYSTEM_CONFIG"),
                eq(SystemConfigKeys.MIN_PAYOUT_AMOUNT), any(JsonNode.class));
    }

    @Test
    @DisplayName("update max_payout_requests_per_day: hợp lệ -> lưu số nguyên")
    void update_maxPayoutPerDay_valid() {
        when(systemConfigRepository.findByKey(SystemConfigKeys.MAX_PAYOUT_REQUESTS_PER_DAY)).thenReturn(Optional.empty());
        when(systemConfigRepository.save(any(SystemConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        SystemConfigResponse response = service.update(admin, SystemConfigKeys.MAX_PAYOUT_REQUESTS_PER_DAY, "5");

        assertThat(response.getValue()).isEqualTo("5");
    }

    @Test
    @DisplayName("update min_payout_amount = 0 (dưới khoảng cho phép) -> 422 CONFIG_VALUE_INVALID, không lưu")
    void update_minPayout_zero_invalid() {
        AppException ex = assertThrows(AppException.class,
                () -> service.update(admin, SystemConfigKeys.MIN_PAYOUT_AMOUNT, "0"));

        assertEquals(ErrorCode.CONFIG_VALUE_INVALID, ex.getErrorCode());
        verify(systemConfigRepository, never()).save(any());
    }

    @Test
    @DisplayName("update max_payout_requests_per_day: 101 (vượt khoảng) hoặc 2.5 (không phải số nguyên) -> 422")
    void update_maxPayoutPerDay_invalid() {
        AppException tooBig = assertThrows(AppException.class,
                () -> service.update(admin, SystemConfigKeys.MAX_PAYOUT_REQUESTS_PER_DAY, "101"));
        AppException notInt = assertThrows(AppException.class,
                () -> service.update(admin, SystemConfigKeys.MAX_PAYOUT_REQUESTS_PER_DAY, "2.5"));

        assertEquals(ErrorCode.CONFIG_VALUE_INVALID, tooBig.getErrorCode());
        assertEquals(ErrorCode.CONFIG_VALUE_INVALID, notInt.getErrorCode());
        verify(systemConfigRepository, never()).save(any());
    }
}