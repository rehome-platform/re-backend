package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.ConsignmentFormStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConsignmentFormDetailResponse {
    private UUID id;
    private ConsignmentFormStatus status;
    private String productName;
    private String rejectReason;
    private LocalDate bringInDeadline;
    private UUID receiptId;
}
