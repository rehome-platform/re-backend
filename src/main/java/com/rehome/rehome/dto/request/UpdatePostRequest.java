package com.rehome.rehome.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * F-105/OB-03: sửa nội dung, thay toàn bộ ảnh (danh sách mới thay thế hẳn danh sách cũ,
 * không phải patch từng ảnh) và chiến dịch gắn kèm. campaignId rỗng/null = gỡ chiến dịch.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePostRequest {
    private String content;
    private List<String> photoUrls;
    private String campaignId;
}
