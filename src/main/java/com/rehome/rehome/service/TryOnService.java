package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.CreateTryOnImageRequest;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.TryOnImageResponse;
import com.rehome.rehome.dto.response.TryOnSuggestionResponse;
import com.rehome.rehome.entity.auth.Account;

import java.util.List;
import java.util.UUID;

public interface TryOnService {

    TryOnImageResponse createImage(Account account, CreateTryOnImageRequest request);

    PageResponse<TryOnImageResponse> getCollection(Account account, int page, int size);

    List<TryOnSuggestionResponse> getOutfitSuggestions(String productId, Double latitude, Double longitude);

    List<TryOnSuggestionResponse> getSuggestions(String slot, String excludeProductId);

    TryOnImageResponse getImage(Account account, UUID imageId);

    void deleteImage(Account account, UUID imageId);
}