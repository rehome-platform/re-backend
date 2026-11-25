package com.rehome.rehome.component;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class OutfitPreferenceModelTest {
    private final OutfitPreferenceModel.Item formal = new OutfitPreferenceModel.Item(
            "Quan tay den", "Quan tay", "Brand A", "M", new BigDecimal("240000"));
    private final OutfitPreferenceModel.Item sport = new OutfitPreferenceModel.Item(
            "Quan jogger den", "Quan the thao", "Brand B", "M", new BigDecimal("240000"));

    @Test void differentPurchasesLearnDifferentPreferenceOrders() {
        var catalogue = List.of(formal, sport);
        var formalModel = new OutfitPreferenceModel(Collections.nCopies(10, formal), catalogue);
        var sportModel = new OutfitPreferenceModel(Collections.nCopies(10, sport), catalogue);
        assertThat(formalModel.score(formal)).isGreaterThan(formalModel.score(sport));
        assertThat(sportModel.score(sport)).isGreaterThan(sportModel.score(formal));
    }

    @Test void coldStartAndUnseenFeaturesRemainFinite() {
        assertThat(new OutfitPreferenceModel(List.of(), List.of(formal, sport)).score(formal)).isZero();
        var model = new OutfitPreferenceModel(List.of(formal), List.of(formal, sport));
        assertThat(model.score(new OutfitPreferenceModel.Item("unknown", "unknown", null, null, null))).isFinite();
    }
}
