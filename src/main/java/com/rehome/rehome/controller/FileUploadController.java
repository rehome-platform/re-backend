package com.rehome.rehome.controller;

import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.UploadedFileResponse;
import com.rehome.rehome.entity.enums.UploadFolder;
import com.rehome.rehome.service.FileUploadService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Upload ảnh/PDF, trả về URL để FE gắn vào imageUrl/fileUrl của các entity.
 * Đặt ở /uploads chứ không phải /files vì /files/** là nơi serve file tĩnh ở local.
 */
@RestController
@RequestMapping("/uploads")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileUploadService fileUploadService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UploadedFileResponse>> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam("folder") UploadFolder folder) {

        UploadedFileResponse uploaded = fileUploadService.upload(file, folder);

        return ResponseEntity.ok(ApiResponse.success(uploaded, "Đã tải file lên"));
    }

    @PostMapping(value = "/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<UploadedFileResponse>>> uploadBatch(
            @RequestPart("files") List<MultipartFile> files,
            @RequestParam("folder") UploadFolder folder) {

        List<UploadedFileResponse> uploaded = fileUploadService.uploadAll(files, folder);

        return ResponseEntity.ok(ApiResponse.success(uploaded, "Đã tải " + uploaded.size() + " file lên"));
    }
}
