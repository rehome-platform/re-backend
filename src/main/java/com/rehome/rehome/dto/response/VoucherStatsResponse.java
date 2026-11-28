package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.VoucherType;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoucherStatsResponse {

    private long totalIssued;
    private long totalUsed;
    private long totalExpired;
    private BigDecimal totalSpent;
    @Builder.Default
    private List<TemplateStats> byTemplate = List.of();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TemplateStats {
        private String templateId;
        private String name;
        private VoucherType type;
        private long issued;
        private long used;
        private long expired;
        private BigDecimal usageRatePercent;
    }
}
