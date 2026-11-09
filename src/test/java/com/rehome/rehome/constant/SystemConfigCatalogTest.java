package com.rehome.rehome.constant;

import com.rehome.rehome.constant.SystemConfigCatalog.Spec;
import com.rehome.rehome.constant.SystemConfigCatalog.ValueType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SystemConfigCatalogTest {

    @Test
    @DisplayName("defaults: mọi key đều có Spec, giá trị mặc định đúng kiểu và nằm trong khoảng cho phép")
    void everyDefault_hasSpec_andFitsItsRange() {
        SystemConfigCatalog.defaults().forEach((key, value) -> {
            Spec spec = SystemConfigCatalog.find(key)
                    .orElseThrow(() -> new AssertionError("Thiếu Spec cho key " + key));

            assertThat(new BigDecimal(value))
                    .as("mặc định của %s", key)
                    .isBetween(spec.min(), spec.max());
            if (spec.type() == ValueType.INT) {
                assertThat(value).as("mặc định của %s phải là số nguyên", key).matches("-?\\d+");
            }
        });
    }

    @Test
    @DisplayName("Mọi Spec trong catalog đều có giá trị mặc định (để seed và GET /api/admin/config không sót key)")
    void everyKeyWithSpec_hasDefault() {
        assertThat(SystemConfigCatalog.defaults())
                .containsKeys(SystemConfigKeys.MIN_PAYOUT_AMOUNT, SystemConfigKeys.MAX_PAYOUT_REQUESTS_PER_DAY,
                        SystemConfigKeys.MAX_CONCURRENT_CAMPAIGNS, SystemConfigKeys.WITHDRAWAL_FEE);
        SystemConfigCatalog.defaults().keySet()
                .forEach(key -> assertThat(SystemConfigCatalog.find(key)).as(key).isPresent());
    }

    @Test
    @DisplayName("Tham số rút tiền: min_payout_amount = 50000 (DECIMAL), max_payout_requests_per_day = 3 (INT)")
    void payoutKeys_areRegisteredWithExpectedDefaults() {
        assertThat(SystemConfigCatalog.defaults())
                .containsEntry("min_payout_amount", "50000")
                .containsEntry("max_payout_requests_per_day", "3");
        assertThat(SystemConfigCatalog.find("min_payout_amount").orElseThrow().type()).isEqualTo(ValueType.DECIMAL);
        assertThat(SystemConfigCatalog.find("max_payout_requests_per_day").orElseThrow().type()).isEqualTo(ValueType.INT);
    }

    @Test
    @DisplayName("find: key ngoài catalog (vd force_payout_failure) -> không có Spec")
    void find_unknownKey_isEmpty() {
        assertThat(SystemConfigCatalog.find("force_payout_failure")).isEmpty();
    }
}