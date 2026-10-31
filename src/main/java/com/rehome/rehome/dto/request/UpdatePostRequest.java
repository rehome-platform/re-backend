package com.rehome.rehome.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * F-105 chỉ sửa nội dung bài viết; ảnh không nằm trong phạm vi sửa.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePostRequest {
    private String content;
}
