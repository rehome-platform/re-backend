package com.rehome.rehome.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptClauseTemplateRequest {

    private Integer displayOrder;
    private String title;
    private String bodyTemplate;
    private Boolean active;
}
