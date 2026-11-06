package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.ViolationWordAction;
import com.rehome.rehome.entity.enums.ViolationWordCategory;
import com.rehome.rehome.entity.enums.ViolationWordSeverity;
import com.rehome.rehome.entity.marketplace.ViolationWord;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ViolationWordResponse {
    private final UUID id;
    private final String word;
    private final ViolationWordCategory category;
    private final ViolationWordSeverity severity;
    private final ViolationWordAction action;
    private final Boolean isActive;
    private final String note;
    private final Instant createdAt;
    private final Instant updatedAt;

    public static ViolationWordResponse from(ViolationWord w) {
        return ViolationWordResponse.builder()
                .id(w.getId())
                .word(w.getWord())
                .category(w.getCategory())
                .severity(w.getSeverity())
                .action(w.getAction())
                .isActive(w.getIsActive())
                .note(w.getNote())
                .createdAt(w.getCreatedAt())
                .updatedAt(w.getUpdatedAt())
                .build();
    }
}
