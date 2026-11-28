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

    private String url;

    private String originalFileName;

    private String contentType;

    private long sizeBytes;
}
