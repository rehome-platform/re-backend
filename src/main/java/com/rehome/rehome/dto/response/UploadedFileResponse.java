package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadedFileResponse {

    /** URL công khai để FE gắn vào imageUrl/fileUrl/avatarUrl */
    private String url;

    /** Tên file gốc do client gửi lên, chỉ để hiển thị lại */
    private String originalFileName;

    private String contentType;

    private long sizeBytes;
}
