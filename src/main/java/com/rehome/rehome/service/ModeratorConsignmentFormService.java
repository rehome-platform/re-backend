package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.ModeratorConsignmentFormResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.auth.Account;

import java.util.UUID;

public interface ModeratorConsignmentFormService {

    // GET /moderator/consignment-forms: form thuộc kho của Moderator, không gồm nháp; stage null = tất cả
    PageResponse<ModeratorConsignmentFormResponse> list(Account moderator, String stage, int page, int size);

    // GET /moderator/consignment-forms/{id}
    ModeratorConsignmentFormResponse getDetail(Account moderator, UUID id);
}