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

    // Biên nhận ký gửi lại vừa tạo, chờ Member xác nhận (confirmedAt = null)
    private UUID newReceiptId;
}
