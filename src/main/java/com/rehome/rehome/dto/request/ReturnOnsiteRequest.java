package com.rehome.rehome.dto.request;

import com.rehome.rehome.entity.enums.ReturnOnsiteResult;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ReturnOnsiteRequest {
    // FAIL / SUSPECTED_FAKE / SUSPECTED_ILLEGAL / MEMBER_DECLINED
    private ReturnOnsiteResult result;
    private String reason;
}