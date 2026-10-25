package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderTrackingResponse {
    private String id;
    private String orderNo;
    private String status;
    private String ghnTrackingCode;

    // GHN hứa giao trước thời điểm này, chụp lại lúc tạo vận đơn
    private Instant expectedDeliveryDate;

    private Instant deliveredAt;

    // true khi sự kiện mới nhất là một trạng thái có vấn đề (giao thất bại, thất lạc, hư hỏng,
    // đang hoàn về...), để app hiện cảnh báo thay vì thanh tiến trình bình thường
    private Boolean hasIssue;

    // Mô tả sự cố lấy từ chính sự kiện đó; null khi hasIssue = false
    private String issueNote;

    private List<OrderTrackingEventResponse> events;
}
