package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.UploadedFileResponse;
import com.rehome.rehome.entity.enums.UploadFolder;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface FileUploadService {

    /**
     * Validate rồi upload một file do client gửi lên.
     *
     * @param file   file multipart
     * @param folder thư mục đích, quyết định định dạng được phép
     * @return URL công khai của file đã lưu
     */
    UploadedFileResponse upload(MultipartFile file, UploadFolder folder);

    /**
     * Upload nhiều file trong một request. Nếu có một file không hợp lệ thì
     * toàn bộ request bị từ chối trước khi upload file nào.
     */
    List<UploadedFileResponse> uploadAll(List<MultipartFile> files, UploadFolder folder);
}
