package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.VoucherTemplateRequest;
import com.rehome.rehome.dto.response.VoucherTemplateResponse;
import com.rehome.rehome.entity.auth.Account;
import java.util.List;
import java.util.UUID;

public interface VoucherTemplateAdminService {

    // §9.3 - danh sách mẫu kèm rule theo hạng (màn W-04)
    List<VoucherTemplateResponse> list();

    VoucherTemplateResponse create(Account admin, VoucherTemplateRequest request);

    VoucherTemplateResponse update(Account admin, UUID id, VoucherTemplateRequest request);

    // Không có DELETE: member_vouchers trỏ FK vào template để thống kê. Chỉ "Ngừng phát".
    VoucherTemplateResponse deactivate(Account admin, UUID id);
}
