package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.QueueType;
import com.rehome.rehome.entity.marketplace.QueueItem;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueueItemResponse {
    private String id;
    private QueueType type;
    private String refId;
    private String claimedBy;
    private Instant claimedAt;
    private Instant createdAt;

    public static QueueItemResponse from(QueueItem q) {
        // getId() trên proxy lazy không kích hoạt query
        String claimedBy = q.getClaimedBy() == null ? null : q.getClaimedBy().getId().toString();
        return QueueItemResponse.builder()
                .id(q.getId())
                .type(q.getType())
                .refId(q.getRefId())
                .claimedBy(claimedBy)
                .claimedAt(q.getClaimedAt())
                .createdAt(q.getCreatedAt())
                .build();
    }
}