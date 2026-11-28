package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.UploadedFileResponse;
import com.rehome.rehome.entity.enums.UploadFolder;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface FileUploadService {

    UploadedFileResponse upload(MultipartFile file, UploadFolder folder);

    List<UploadedFileResponse> uploadAll(List<MultipartFile> files, UploadFolder folder);
}
