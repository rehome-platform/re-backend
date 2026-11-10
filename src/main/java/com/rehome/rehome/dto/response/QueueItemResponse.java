package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.auth.OrganizationLegalChangeRequest;
import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.entity.enums.QueueType;
import com.rehome.rehome.entity.marketplace.ConsignmentForm;
import com.rehome.rehome.entity.marketplace.ItemWithdrawalRequest;
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

    // Chung cho mọi loại việc (trừ VIOLATION_REPORT): tên đối tượng hiển thị trên queue và dòng phụ
    //   PACKING / RETURN: mã đơn / người nhận hoặc người bán - người mua
    //   FORM_REVIEW / INSPECTION: tên sản phẩm ký gửi / tên người gửi
    //   ORG_VERIFICATION / LEGAL_CHANGE: tên tổ chức / người đại diện
    //   WITHDRAWAL: tên sản phẩm cần rút / tên người ký gửi
    private String targetName;
    private String subjectSubtitle;

    // Chỉ có giá trị với QueueType.WITHDRAWAL: tài khoản và mã Member ký gửi món cần rút
    private String accountId;
    private String memberCode;

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

    // FORM_REVIEW / INSPECTION: refId là id form ký gửi; memberName null thì dùng mã form làm dòng phụ
    public static QueueItemResponse fromConsignmentForm(QueueItem q, ConsignmentForm form, String memberName) {
        QueueItemResponse response = from(q);
        response.setTargetName(form.getProductName());
        response.setSubjectSubtitle(memberName != null ? memberName : form.getFormNo());
        return response;
    }

    // WITHDRAWAL: refId là id yêu cầu rút hàng; productName là tên món cần rút, memberName là người ký gửi.
    // status phân biệt PENDING_APPROVAL (cần duyệt/từ chối) với PENDING_PICKUP (cần bàn giao - API 72)
    public static QueueItemResponse fromWithdrawal(
            QueueItem q, ItemWithdrawalRequest withdrawal, String memberName, String memberCode) {
        QueueItemResponse response = from(q);
        response.setTargetName(withdrawal.getProduct().getName());
        response.setSubjectSubtitle(memberName);
        response.setStatus(withdrawal.getStatus().name());
        return response;
    }

    // ORG_VERIFICATION: refId là accountId của tổ chức
    public static QueueItemResponse fromOrganization(QueueItem q, OrganizationProfile org) {
        QueueItemResponse response = from(q);
        response.setTargetName(org.getDisplayName());
        response.setSubjectSubtitle(representativeName(org));
        return response;
    }

    private static String representativeName(OrganizationProfile org) {
        return org.getRepresentative() == null ? null : org.getRepresentative().getFullName();
    }

    public static QueueItemResponse fromLegalChange(QueueItem q, OrganizationLegalChangeRequest request,
                                                    String fieldChanged, String oldValue, String newValue) {
        QueueItemResponse response = from(q);
        response.setRequestId(request.getId().toString());
        response.setOrgName(request.getOrganization().getDisplayName());
        response.setTargetName(request.getOrganization().getDisplayName());
        response.setSubjectSubtitle(representativeName(request.getOrganization()));
        response.setStatus(request.getStatus().name());
        response.setFieldChanged(fieldChanged);
        response.setOldValue(oldValue);
        response.setNewValue(newValue);
        return response;
    }
}