package com.rehome.rehome.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateCampaignDonationNoteRequest {
    private String donorName;
    private Boolean isAnonymous;

    @Size(max = 500, message = "note tối đa 500 ký tự.")
    private String note;
}
