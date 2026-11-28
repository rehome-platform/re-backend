package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReConsignTermsResponse {

    private UUID decisionId;

    private UUID newReceiptId;
}
