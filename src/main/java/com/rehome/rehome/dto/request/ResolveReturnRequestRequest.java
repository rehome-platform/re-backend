package com.rehome.rehome.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResolveReturnRequestRequest {
    // VALID / INVALID
    private String resolution;
    // RELIST / RETURN_TO_SELLER / DISCARD — bắt buộc khi resolution = VALID
    private String action;
    private String resolutionNote;
}
