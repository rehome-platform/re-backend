package com.rehome.rehome.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateCommentRequest {
    private String content;

    // Trả lời 1 bình luận gốc hoặc 1 reply khác; để trống = bình luận gốc mới. Trả lời vào 1
    // reply thì BE tự gắn vào bình luận gốc của reply đó (chỉ trả lời 1 cấp).
    private String parentId;
}
