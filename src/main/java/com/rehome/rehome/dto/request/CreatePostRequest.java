package com.rehome.rehome.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePostRequest {
    private String content;

    // Ảnh đã upload qua POST /uploads; mỗi url thành 1 dòng community_post_photos
    private List<String> photoUrls;

    // OB-02: không bắt buộc; chỉ cho gắn chiến dịch đang OPEN của chính tổ chức đang đăng bài
    private String campaignId;
}
