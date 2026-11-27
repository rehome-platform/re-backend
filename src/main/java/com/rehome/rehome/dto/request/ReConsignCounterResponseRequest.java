package com.rehome.rehome.dto.request;

import com.rehome.rehome.entity.enums.ReConsignCounterResponseAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

// Moderator phản hồi đề xuất giá của Member (POST /end-of-term-decisions/{id}/counter-response)
@Getter
@Setter
public class ReConsignCounterResponseRequest {

    @NotNull(message = "action là bắt buộc.")
    private ReConsignCounterResponseAction action;

    // Bắt buộc với REPROPOSE; với ACCEPT không truyền thì lấy đúng giá Member đã đề xuất
    @Positive(message = "agreedPrice phải lớn hơn 0.")
    private BigDecimal agreedPrice;

    // Không truyền thì tính lại theo tỉ lệ cấu hình (floor_price_ratio) trên agreedPrice áp dụng
    @Positive(message = "floorPrice phải lớn hơn 0.")
    private BigDecimal floorPrice;

    // Bắt buộc với KEEP (lý do giữ nguyên giá)
    private String note;
}
