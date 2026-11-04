package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.CreateTryOnImageRequest;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.TryOnImageResponse;
import com.rehome.rehome.dto.response.TryOnSuggestionResponse;
import com.rehome.rehome.entity.auth.Account;

import java.util.List;
import java.util.UUID;

public interface TryOnService {

    // F-072..F-079 (API 97): sinh ảnh thử đồ bằng AI rồi lưu vào bộ sưu tập, chỉ lưu khi sinh thành công
    TryOnImageResponse createImage(Account account, CreateTryOnImageRequest request);

    // F-080: bộ sưu tập ảnh thử đồ của Member, mới nhất trước
    PageResponse<TryOnImageResponse> getCollection(Account account, int page, int size);

    // API 96 (F-075): gợi ý món còn thiếu cho slot TOP/BOTTOM, chỉ ON_SALE; rỗng thì FE tự mở GET /products (F-076)
    List<TryOnSuggestionResponse> getSuggestions(String slot, String excludeProductId);

    // GET /tryon/images/{id}: xem 1 ảnh trong bộ sưu tập, chỉ chủ ảnh được xem
    TryOnImageResponse getImage(Account account, UUID imageId);

    // F-081: xóa ảnh khỏi bộ sưu tập, chỉ chủ ảnh được xóa
    void deleteImage(Account account, UUID imageId);
}