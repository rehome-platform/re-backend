package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.CreateTryOnImageRequest;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.TryOnImageResponse;
import com.rehome.rehome.entity.auth.Account;

import java.util.UUID;

public interface TryOnService {

    // F-072..F-079 (API 97): sinh ảnh thử đồ bằng AI rồi lưu vào bộ sưu tập, chỉ lưu khi sinh thành công
    TryOnImageResponse createImage(Account account, CreateTryOnImageRequest request);

    // F-080: bộ sưu tập ảnh thử đồ của Member, mới nhất trước
    PageResponse<TryOnImageResponse> getCollection(Account account, int page, int size);

    // F-081: xóa ảnh khỏi bộ sưu tập, chỉ chủ ảnh được xóa
    void deleteImage(Account account, UUID imageId);
}