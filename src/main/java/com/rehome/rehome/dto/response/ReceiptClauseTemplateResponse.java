package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.marketplace.ReceiptClauseTemplate;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// GET /api/admin/receipt-clause-templates - danh sách điều khoản dạng đoạn văn của biên nhận ký gửi
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceiptClauseTemplateResponse {

    private UUID id;
    private Integer displayOrder;
    private String title;
    private String bodyTemplate;
    private Boolean active;
    private Instant updatedAt;

    public static ReceiptClauseTemplateResponse from(ReceiptClauseTemplate t) {
        return ReceiptClauseTemplateResponse.builder()
                .id(t.getId())
                .displayOrder(t.getDisplayOrder())
                .title(t.getTitle())
                .bodyTemplate(t.getBodyTemplate())
                .active(t.getActive())
                .updatedAt(t.getUpdatedAt())
                .build();
    }
}
