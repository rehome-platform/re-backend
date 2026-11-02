package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Bài viết sau khi sửa (F-105/OB-03).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostUpdateResponse {
    private String id;
    private String content;
    private List<String> photoUrls;
    private String campaignId;
    private String campaignTitle;
}
