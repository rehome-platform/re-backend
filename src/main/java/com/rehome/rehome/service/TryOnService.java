package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.TryOnImageResponse;
import com.rehome.rehome.entity.auth.Account;

import java.util.UUID;

public interface TryOnService {

    // F-080: bộ sưu tập ảnh thử đồ của Member, mới nhất trước
    PageResponse<TryOnImageResponse> getCollection(Account account, int page, int size);

    // F-081: xóa ảnh khỏi bộ sưu tập, chỉ chủ ảnh được xóa
    void deleteImage(Account account, UUID imageId);
}