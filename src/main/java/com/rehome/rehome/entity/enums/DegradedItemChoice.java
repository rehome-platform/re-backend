package com.rehome.rehome.entity.enums;

public enum DegradedItemChoice {
    FULL_COMPENSATION,
    PARTIAL_WITH_ITEM_BACK,
    // Member không chọn trong hạn (decisionDeadline) - job tự gán, không phải Member chọn.
    // Cùng cách EndOfTermChoice.UNCLAIMED đánh dấu "quá hạn, không ai chọn".
    UNCLAIMED
}
