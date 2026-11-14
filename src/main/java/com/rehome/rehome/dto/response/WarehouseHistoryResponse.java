package com.rehome.rehome.dto.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Một dòng lịch sử thao tác kho. Cố ý không có trường detail: JSON nội bộ của audit không ra ngoài. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseHistoryResponse {
    // Đối tượng bị tác động, ví dụ "ORDER RH-000123", "CONSIGNMENT_FORM CF-0001", "PRODUCT SKU123"
    private String target;
    // Hành động, ví dụ SCAN_SKU, CREATE_SHIPMENT
    private String action;
    // Tên Moderator thực hiện (không có tên thì email)
    private String actor;
    private Instant timestamp;
    // Kết quả ngắn, ví dụ SUCCESS, APPROVED
    private String result;
}
