package com.rehome.rehome.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MlOutfitSuggestionsResponse {
    String model;
    boolean personalized;
    int historyItemsUsed;
    int candidateItemsScored;
    List<TryOnSuggestionResponse> items;
}
