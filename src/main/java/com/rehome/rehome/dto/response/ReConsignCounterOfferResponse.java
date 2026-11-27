package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.ReConsignStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReConsignCounterOfferResponse {
    private UUID decisionId;
    private ReConsignStatus reConsignStatus;
    private int counterRoundsUsed;
    private int maxCounterRounds;
}
