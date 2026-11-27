package com.rehome.rehome.dto.request;

import lombok.Getter;
import lombok.Setter;

// Member không đồng ý đề xuất giá ký gửi lại (POST /end-of-term-decisions/{id}/decline)
@Getter
@Setter
public class ReConsignDeclineRequest {
    private String reason;
}
