package com.rehome.rehome.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ConfirmTransferRequest {
    private String partnerOrganizationName;
}