package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.auth.OrganizationLegalChangeRequest;
import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.entity.enums.QueueType;
import com.rehome.rehome.entity.marketplace.Campaign;
import com.rehome.rehome.entity.marketplace.ConsignmentForm;
import com.rehome.rehome.entity.marketplace.Donation;
import com.rehome.rehome.entity.marketplace.ItemWithdrawalRequest;
import com.rehome.rehome.entity.marketplace.Order;
import com.rehome.rehome.entity.marketplace.QueueItem;
import com.rehome.rehome.entity.marketplace.ReturnRequest;
import java.math.BigDecimal;
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

    private String orderNo;
    private String recipientName;
    private Integer itemCount;
    private Instant orderCreatedAt;
    private Long waitingMinutes;

    private String targetName;
    private String subjectSubtitle;

    private String accountId;
    private String memberCode;

    private String sku;

    private String productImageUrl;
    private BigDecimal feeCharged;
    private Boolean feePaid;
    private Instant pickupDeadline;

    private String campaignId;
    private String campaignTitle;
    private String organizationId;
    private String organizationName;

    private String requestId;
    private String orgName;
    private String fieldChanged;
    private String oldValue;
    private String newValue;
    private String status;

    public static QueueItemResponse from(QueueItem q) {
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
        response.setOrderCreatedAt(order.getCreatedAt());
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

    public static QueueItemResponse fromReturnShipBack(
            QueueItem q, ReturnRequest returnRequest, Order order, String buyerName) {
        QueueItemResponse response = from(q);
        String recipient = returnRequest.getShipBackRecipientName() != null
                ? returnRequest.getShipBackRecipientName() : buyerName;
        response.setOrderNo(order.getOrderNo());
        response.setRecipientName(recipient);
        response.setTargetName(order.getOrderNo());
        response.setSubjectSubtitle(recipient);
        response.setFeeCharged(returnRequest.getShipBackFee());
        response.setFeePaid(returnRequest.getShipBackPaidAt() != null);
        response.setStatus(returnRequest.getShipBackStatus() != null
                ? returnRequest.getShipBackStatus().name() : null);
        return response;
    }

    public static QueueItemResponse fromConsignmentForm(QueueItem q, ConsignmentForm form, String memberName) {
        QueueItemResponse response = from(q);
        response.setTargetName(form.getProductName());
        response.setSubjectSubtitle(memberName != null ? memberName : form.getFormNo());
        return response;
    }

    public static QueueItemResponse fromWithdrawal(
            QueueItem q, ItemWithdrawalRequest withdrawal, String memberName, String memberCode) {
        return fromWithdrawal(q, withdrawal, memberName, memberCode, null);
    }

    public static QueueItemResponse fromWithdrawal(
            QueueItem q, ItemWithdrawalRequest withdrawal, String memberName, String memberCode,
            String productImageUrl) {
        QueueItemResponse response = from(q);
        response.setTargetName(withdrawal.getProduct().getName());
        response.setSubjectSubtitle(memberName);
        response.setStatus(withdrawal.getStatus().name());
        response.setAccountId(withdrawal.getMember().getId().toString());
        response.setMemberCode(memberCode);
        response.setSku(withdrawal.getProduct().getId());
        response.setProductImageUrl(productImageUrl);
        response.setFeeCharged(withdrawal.getFeeCharged());
        response.setFeePaid(withdrawal.getFeeCharged().compareTo(BigDecimal.ZERO) == 0
                || withdrawal.getFeePaidAt() != null);
        response.setPickupDeadline(withdrawal.getPickupDeadline());
        return response;
    }

    public static QueueItemResponse fromDonationPickup(QueueItem q, Donation donation) {
        QueueItemResponse response = from(q);
        Campaign campaign = donation.getCampaign();
        response.setSku(donation.getProduct() == null ? null : donation.getProduct().getId());
        response.setTargetName(donation.getProduct() == null ? null : donation.getProduct().getName());
        response.setSubjectSubtitle(q.getType() == QueueType.DONATION_PREP && response.getSku() != null
                ? "Lấy món SKU " + response.getSku() + " khỏi kệ, để vào khu chờ bàn giao"
                : donation.getDonorName());
        response.setStatus(donation.getStatus().name());
        if (campaign != null) {
            response.setCampaignId(campaign.getId().toString());
            response.setCampaignTitle(campaign.getTitle());
            if (campaign.getOrganization() != null) {
                response.setOrganizationId(campaign.getOrganization().getAccountId().toString());
                response.setOrganizationName(campaign.getOrganization().getDisplayName());
            }
        }
        return response;
    }

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