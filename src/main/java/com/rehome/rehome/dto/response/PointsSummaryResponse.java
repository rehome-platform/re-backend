package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PointsSummaryResponse {

    private int pointsBalance;
    private int bonusAiTryonCredits;
    private int bonusLiveTryonCredits;

    private int vndPerPoint;
    private int costPerAiTryon;
    private int costPerLiveTryon;
}
