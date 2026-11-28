package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.entity.marketplace.SystemConfig;
import com.rehome.rehome.repository.SystemConfigRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemConfigReaderImplTest {

    @Mock
    SystemConfigRepository systemConfigRepository;

    @InjectMocks
    SystemConfigReaderImpl reader;

    private void stubConfig(String key, String value) {
        when(systemConfigRepository.findByKey(key))
                .thenReturn(Optional.of(SystemConfig.builder().key(key).value(value).build()));
    }

    // ==================== getInt ====================

    @Test
    @DisplayName("getInt: có cấu hình -> parse ra số nguyên")
    void getInt_present_returnsParsedValue() {
        stubConfig("max_pending_forms", "5");

        assertThat(reader.getInt("max_pending_forms", 99)).isEqualTo(5);
        verify(systemConfigRepository).findByKey("max_pending_forms");
    }

    @Test
    @DisplayName("getInt: giá trị có khoảng trắng hai đầu -> trim rồi parse")
    void getInt_valueWithWhitespace_isTrimmed() {
        stubConfig("k", "  30  ");

        assertThat(reader.getInt("k", 1)).isEqualTo(30);
    }

    @Test
    @DisplayName("getInt: số âm vẫn parse được")
    void getInt_negativeValue_isParsed() {
        stubConfig("k", "-7");

        assertThat(reader.getInt("k", 1)).isEqualTo(-7);
    }

    @Test
    @DisplayName("getInt: không có cấu hình -> trả defaultValue")
    void getInt_missing_returnsDefault() {
        when(systemConfigRepository.findByKey("missing")).thenReturn(Optional.empty());

        assertThat(reader.getInt("missing", 42)).isEqualTo(42);
    }

    @Test
    @DisplayName("getInt: giá trị không phải số nguyên -> IllegalStateException nêu rõ key")
    void getInt_invalidNumber_throwsIllegalState() {
        stubConfig("bad_key", "abc");

        assertThatThrownBy(() -> reader.getInt("bad_key", 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bad_key")
                .hasMessageContaining("abc")
                .hasCauseInstanceOf(NumberFormatException.class);
    }

    @Test
    @DisplayName("getInt: giá trị thập phân -> coi là không hợp lệ")
    void getInt_decimalValue_throwsIllegalState() {
        stubConfig("k", "1.5");

        assertThatThrownBy(() -> reader.getInt("k", 1))
                .isInstanceOf(IllegalStateException.class);
    }

    // ==================== getDecimal ====================

    @Test
    @DisplayName("getDecimal: có cấu hình -> parse ra BigDecimal")
    void getDecimal_present_returnsParsedValue() {
        stubConfig("commission_rate", "0.15");

        assertThat(reader.getDecimal("commission_rate", BigDecimal.ZERO))
                .isEqualByComparingTo(new BigDecimal("0.15"));
    }

    @Test
    @DisplayName("getDecimal: giá trị có khoảng trắng hai đầu -> trim rồi parse")
    void getDecimal_valueWithWhitespace_isTrimmed() {
        stubConfig("k", " 200000 ");

        assertThat(reader.getDecimal("k", BigDecimal.ZERO))
                .isEqualByComparingTo(new BigDecimal("200000"));
    }

    @Test
    @DisplayName("getDecimal: không có cấu hình -> trả defaultValue (đúng instance truyền vào)")
    void getDecimal_missing_returnsDefault() {
        BigDecimal def = new BigDecimal("0.20");
        when(systemConfigRepository.findByKey("missing")).thenReturn(Optional.empty());

        assertThat(reader.getDecimal("missing", def)).isSameAs(def);
    }

    @Test
    @DisplayName("getDecimal: không có cấu hình và default null -> trả null")
    void getDecimal_missingAndNullDefault_returnsNull() {
        when(systemConfigRepository.findByKey("missing")).thenReturn(Optional.empty());

        assertThat(reader.getDecimal("missing", null)).isNull();
    }

    @Test
    @DisplayName("getDecimal: giá trị không phải số -> IllegalStateException nêu rõ key")
    void getDecimal_invalidNumber_throwsIllegalState() {
        stubConfig("bad_key", "12,5%");

        assertThatThrownBy(() -> reader.getDecimal("bad_key", BigDecimal.ONE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bad_key")
                .hasMessageContaining("12,5%")
                .hasCauseInstanceOf(NumberFormatException.class);
    }

    // ==================== cache ====================

    @Test
    @DisplayName("cache: đọc lại cùng key trong thời hạn cache -> không query DB lần nữa")
    void repeatedRead_withinTtl_queriesDatabaseOnce() {
        stubConfig("k", "5");

        assertThat(reader.getInt("k", 1)).isEqualTo(5);
        assertThat(reader.getInt("k", 1)).isEqualTo(5);
        assertThat(reader.getDecimal("k", BigDecimal.ONE)).isEqualByComparingTo("5");

        verify(systemConfigRepository, times(1)).findByKey("k");
    }

    @Test
    @DisplayName("cache: key chưa có dòng -> mỗi nơi gọi vẫn nhận đúng defaultValue của mình")
    void missingKey_isCached_andEachCallerGetsItsOwnDefault() {
        when(systemConfigRepository.findByKey("missing")).thenReturn(Optional.empty());

        assertThat(reader.getInt("missing", 1)).isEqualTo(1);
        assertThat(reader.getInt("missing", 2)).isEqualTo(2);

        verify(systemConfigRepository, times(1)).findByKey("missing");
    }

    @Test
    @DisplayName("cache: evict -> lần đọc sau lấy giá trị mới từ DB")
    void evict_forcesReloadOnNextRead() {
        when(systemConfigRepository.findByKey("k")).thenReturn(
                Optional.of(SystemConfig.builder().key("k").value("5").build()),
                Optional.of(SystemConfig.builder().key("k").value("7").build()));

        assertThat(reader.getInt("k", 1)).isEqualTo(5);
        reader.evict("k");
        assertThat(reader.getInt("k", 1)).isEqualTo(7);

        verify(systemConfigRepository, times(2)).findByKey("k");
    }

    @Test
    @DisplayName("cache: evict trong transaction -> xóa thêm lần nữa khi transaction kết thúc (commit hay rollback)")
    void evict_insideTransaction_clearsAgainAfterCompletion() {
        when(systemConfigRepository.findByKey("k")).thenReturn(
                Optional.of(SystemConfig.builder().key("k").value("5").build()),
                Optional.of(SystemConfig.builder().key("k").value("6").build()));
        TransactionSynchronizationManager.initSynchronization();
        try {
            reader.evict("k");
            assertThat(reader.getInt("k", 1)).isEqualTo(5);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertThat(reader.getInt("k", 1)).isEqualTo(6);
        verify(systemConfigRepository, times(2)).findByKey("k");
    }
}
