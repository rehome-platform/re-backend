package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Yêu cầu đổi thông tin pháp lý vừa gửi, chờ Moderator duyệt (F-097).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegalChangeRequestResponse {
    private String id;
    private String status;
}
