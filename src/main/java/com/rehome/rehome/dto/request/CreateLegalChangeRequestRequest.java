package com.rehome.rehome.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Yêu cầu đổi thông tin pháp lý (F-097).
 * proposedChanges để dạng JSON tự do vì mỗi lần xin đổi có thể gồm các trường khác nhau
 * (legalName, người đại diện, documents...) — entity lưu nguyên vào cột jsonb.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateLegalChangeRequestRequest {
    private JsonNode proposedChanges;
}
