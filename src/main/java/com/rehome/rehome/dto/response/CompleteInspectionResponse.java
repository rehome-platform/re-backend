package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class CompleteInspectionResponse {
    private UUID receiptId;
}
