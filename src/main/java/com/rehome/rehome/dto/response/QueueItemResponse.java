package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.auth.OrganizationLegalChangeRequest;
import com.rehome.rehome.entity.enums.QueueType;
import com.rehome.rehome.entity.marketplace.Order;
import com.rehome.rehome.entity.marketplace.QueueItem;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueueItemResponse {
    private String id;
    private QueueType type;
    private String refId;
    private String claimedBy;
    private Instant claimedAt;
    private Instant createdAt;

    // Chỉ có giá trị với QueueType.PACKING - DG-01
    private String orderNo;
    private String recipientName;
    private Integer itemCount;
    private Long waitingMinutes;

    // Chung cho PACKING / RETURN: tên đối tượng hiển thị trên queue và dòng phụ
    private String targetName;
    private String subjectSubtitle;

    // Chỉ có giá trị với QueueType.LEGAL_CHANGE
    private String requestId;
    private String orgName;
    private String fieldChanged;
    private String oldValue;
    private String newValue;
    private String status;

    public static QueueItemResponse from(QueueItem q) {
        // getId() trên proxy lazy không kích hoạt query
        String claimedBy = q.getClaimedBy() == null ? null : q.getClaimedBy().getId().toString();
        return QueueItemResponse.builder()
                .id(q.getId())
                .type(q.getType())
                .refId(q.getRefId())
                .claimedBy(claimedBy)
                .claimedAt(q.getClaimedAt())
                .createdAt(q.getCreatedAt())
                .build();
    }

    public static QueueItemResponse fromPacking(
            QueueItem q, Order order, int itemCount, long waitingMinutes) {
        QueueItemResponse response = from(q);
        response.setOrderNo(order.getOrderNo());
        response.setRecipientName(order.getShippingRecipientName());
        response.setItemCount(itemCount);
        response.setWaitingMinutes(waitingMinutes);
        response.setTargetName(order.getOrderNo());
        response.setSubjectSubtitle(order.getShippingRecipientName());
        return response;
    }

    public static QueueItemResponse fromReturn(
            QueueItem q, Order order, String sellerName, String buyerName) {
        QueueItemResponse response = from(q);
        response.setOrderNo(order.getOrderNo());
        response.setTargetName(order.getOrderNo());
        response.setSubjectSubtitle(sellerName + " / " + buyerName);
        return response;
    }

    public static QueueItemResponse fromLegalChange(QueueItem q, OrganizationLegalChangeRequest request,
            String fieldChanged, String oldValue, String newValue) {
        QueueItemResponse response = from(q);
        response.setRequestId(request.getId().toString());
        response.setOrgName(request.getOrganization().getDisplayName());
        response.setStatus(request.getStatus().name());
        response.setFieldChanged(fieldChanged);
        response.setOldValue(oldValue);
        response.setNewValue(newValue);
        return response;
    }
}