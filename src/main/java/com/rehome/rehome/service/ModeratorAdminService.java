package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.ChangeModeratorWarehouseRequest;
import com.rehome.rehome.dto.request.CreateModeratorRequest;
import com.rehome.rehome.dto.response.ModeratorAdminResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.AccountStatus;

import java.util.UUID;

public interface ModeratorAdminService {

    /** API 138: tạo tài khoản Moderator, sinh mã nhân viên và gửi mật khẩu tạm qua email. */
    ModeratorAdminResponse create(Account admin, CreateModeratorRequest request);

    /**
     * API 139: danh sách Moderator. warehouseId/search/status đều không bắt buộc và kết hợp được với nhau;
     * lọc xong mới phân trang. search khớp họ tên, email hoặc số điện thoại.
     */
    PageResponse<ModeratorAdminResponse> list(UUID warehouseId, String search, AccountStatus status,
                                              int page, int size);

    /** API 140: chuyển Moderator sang kho khác. */
    ModeratorAdminResponse changeWarehouse(Account admin, UUID moderatorId, ChangeModeratorWarehouseRequest request);
}
