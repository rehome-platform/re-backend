package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.MembershipTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiTryOnQuotaResponse {

    private MembershipTier tier;
    private String tierName;
    private Integer limitPerDay;
    private long usedToday;
    private Integer remainingToday;
    private Integer limitPerMonth;
    private long usedThisMonth;
    private Integer remainingThisMonth;
    private boolean canGenerate;
    private int bonusCredits;
    private MembershipTier upsellTier;
}
