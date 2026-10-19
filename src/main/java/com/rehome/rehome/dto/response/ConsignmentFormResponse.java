package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.ConsignmentFormStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConsignmentFormResponse {
    private UUID id;
    private ConsignmentFormStatus status;
    private LocalDate bringInDeadline;
    private Instant createdAt;
}
