package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.VoucherTemplateRequest;
import com.rehome.rehome.dto.response.VoucherTemplateResponse;
import com.rehome.rehome.entity.auth.Account;
import java.util.List;
import java.util.UUID;

public interface VoucherTemplateAdminService {

    List<VoucherTemplateResponse> list();

    VoucherTemplateResponse create(Account admin, VoucherTemplateRequest request);

    VoucherTemplateResponse update(Account admin, UUID id, VoucherTemplateRequest request);

    VoucherTemplateResponse deactivate(Account admin, UUID id);

    VoucherTemplateResponse activate(Account admin, UUID id);
}
