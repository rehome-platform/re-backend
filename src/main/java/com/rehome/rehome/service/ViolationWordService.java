package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.CreateViolationWordRequest;
import com.rehome.rehome.dto.request.UpdateViolationWordRequest;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.ViolationWordResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ViolationWordCategory;
import com.rehome.rehome.entity.enums.ViolationWordSeverity;
import java.util.UUID;

public interface ViolationWordService {

    /** Tìm theo từ khóa (không phân biệt hoa thường), lọc nhóm / mức độ / trạng thái; mới nhất trước. */
    PageResponse<ViolationWordResponse> search(String keyword, ViolationWordCategory category,
                                               ViolationWordSeverity severity, Boolean isActive,
                                               int page, int size);

    ViolationWordResponse create(Account admin, CreateViolationWordRequest request);

    ViolationWordResponse update(Account admin, UUID id, UpdateViolationWordRequest request);

    void delete(Account admin, UUID id);

    /** Đảo trạng thái bật/tắt. */
    ViolationWordResponse toggle(Account admin, UUID id);
}
