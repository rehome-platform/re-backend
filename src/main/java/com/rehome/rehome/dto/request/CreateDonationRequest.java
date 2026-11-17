package com.rehome.rehome.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateDonationRequest {
    private String campaignId;
    private String donorName;
    private Boolean isAnonymous;
    private String note;
    @Size(max = 5)
    private List<@NotBlank @Size(max = 2048) String> photoUrls;

    public CreateDonationRequest(String campaignId, String donorName, Boolean isAnonymous, String note) {
        this(campaignId, donorName, isAnonymous, note, null);
    }
}
