package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.ModeratorConsignmentFormResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.auth.Account;

import java.util.UUID;

public interface ModeratorConsignmentFormService {

    PageResponse<ModeratorConsignmentFormResponse> list(Account moderator, String stage, int page, int size);

    ModeratorConsignmentFormResponse getDetail(Account moderator, UUID id);
}