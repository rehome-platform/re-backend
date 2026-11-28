package com.rehome.rehome.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StartInspectionRequest {
    private Boolean matchesOriginal;
    private Boolean degraded;
    private Boolean counterfeitSuspected;
    private String conditionNote;

    private List<String> evidenceImageUrls;
}
