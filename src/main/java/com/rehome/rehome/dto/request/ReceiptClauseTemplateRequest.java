package com.rehome.rehome.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * POST / PUT /api/admin/receipt-clause-templates.
 *
 * <p>{@code displayOrder} để trống khi tạo mới -> tự gán số lớn nhất hiện có + 1 (thêm vào cuối).
 * {@code bodyTemplate} chỉ được dùng các placeholder trong whitelist của
 * {@code ReceiptClauseRenderer} (sai -> 400 {@code RECEIPT_CLAUSE_TEMPLATE_INVALID}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptClauseTemplateRequest {

    private Integer displayOrder;
    private String title;
    private String bodyTemplate;
    private Boolean active;
}
