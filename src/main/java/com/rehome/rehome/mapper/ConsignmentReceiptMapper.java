package com.rehome.rehome.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.rehome.rehome.dto.response.ConsignmentReceiptConfirmResponse;
import com.rehome.rehome.dto.response.ConsignmentReceiptResponse;
import com.rehome.rehome.dto.response.ConsignmentReceiptResponse.ClauseItem;
import com.rehome.rehome.entity.marketplace.ConsignmentForm;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.EndOfTermDecision;
import java.util.List;

public final class ConsignmentReceiptMapper {

    private ConsignmentReceiptMapper() {
    }

    public static ConsignmentReceiptResponse toResponse(ConsignmentReceipt r) {
        if (r == null) {
            return null;
        }
        ConsignmentReceiptResponse dto = new ConsignmentReceiptResponse();
        dto.setId(r.getId());
        dto.setStatus(r.effectiveStatus());
        dto.setVersion(r.getVersion());
        dto.setEditReason(r.getEditReason());
        dto.setEditedAt(r.getEditedAt());
        dto.setVoidReason(r.getVoidReason());
        dto.setVoidedAt(r.getVoidedAt());
        dto.setVoidedBy(r.getVoidedBy() == null ? null : r.getVoidedBy().getId());
        dto.setPreviousReceiptId(r.getPreviousReceipt() == null ? null : r.getPreviousReceipt().getId());
        dto.setReceiptNo(r.getReceiptNo());
        dto.setCreatedAt(r.getCreatedAt());
        dto.setAgreedPrice(r.getAgreedPrice());
        dto.setFloorPrice(r.getFloorPrice());
        dto.setTermLengthDays(r.getTermLengthDays());
        dto.setMaxRenewals(r.getMaxRenewals());
        dto.setFreeWithdrawalWindowDays(r.getFreeWithdrawalWindowDays());
        dto.setCommissionRateTerm1(r.getCommissionRateTerm1());
        dto.setCommissionRateTerm2(r.getCommissionRateTerm2());
        dto.setCommissionRateTerm3(r.getCommissionRateTerm3());
        dto.setWithdrawalFee(r.getWithdrawalFee());
        dto.setDegradedCompensationRate(r.getDegradedCompensationRate());
        dto.setConfirmedAt(r.getConfirmedAt());
        dto.setCreatedAt(r.getCreatedAt());
        dto.setPdfAvailable(r.getConfirmedAt() != null);
        dto.setClauses(toClauseItems(r.getClausesSnapshot()));
        return dto;
    }

    private static List<ClauseItem> toClauseItems(JsonNode snapshot) {
        if (snapshot == null || !snapshot.isArray()) {
            return List.of();
        }
        List<ClauseItem> items = new java.util.ArrayList<>();
        for (JsonNode node : snapshot) {
            items.add(ClauseItem.builder()
                    .displayOrder(node.path("displayOrder").asInt())
                    .title(node.path("title").asText())
                    .body(node.path("body").asText())
                    .build());
        }
        return items;
    }

    public static ConsignmentReceiptResponse toResponse(ConsignmentReceipt r, String sku) {
        ConsignmentReceiptResponse dto = toResponse(r);
        if (dto == null) {
            return null;
        }
        ConsignmentForm f = r.getConsignmentForm();
        dto.setProductName(f.getProductName());
        dto.setBrandName(f.getBrand().getName());
        dto.setSize(f.getSize());
        dto.setWarehouseName(r.getWarehouse().getName());
        dto.setConditionNote(r.getConditionNote());
        dto.setSku(sku);
        dto.setModeratorNote(r.getModeratorNote());
        return dto;
    }

    public static ConsignmentReceiptResponse toResponse(ConsignmentReceipt r, String sku,
                                                        EndOfTermDecision reConsignDecision) {
        ConsignmentReceiptResponse dto = toResponse(r, sku);
        if (dto != null && reConsignDecision != null) {
            dto.setProductId(reConsignDecision.getProduct().getId());
            dto.setPreviousPrice(reConsignDecision.getProduct().getCurrentPrice());
            dto.setReConsignStatus(reConsignDecision.getReConsignStatus());
            dto.setProposalDeadline(reConsignDecision.getProposalDeadline());
            dto.setProposalRound(reConsignDecision.getProposalRound());
            dto.setCounterRoundsUsed(reConsignDecision.getCounterRoundsUsed());
            dto.setMemberCounterPrice(reConsignDecision.getMemberCounterPrice());
            dto.setMemberCounterNote(reConsignDecision.getMemberCounterNote());
            dto.setModeratorResponse(reConsignDecision.getModeratorResponse());
            dto.setModeratorResponseNote(reConsignDecision.getModeratorResponseNote());
        }
        return dto;
    }

    public static ConsignmentReceiptConfirmResponse toConfirmResponse(ConsignmentReceipt r) {
        ConsignmentReceiptConfirmResponse dto = new ConsignmentReceiptConfirmResponse();
        dto.setId(r.getId());
        dto.setConfirmedAt(r.getConfirmedAt());
        dto.setPdfAvailable(r.getConfirmedAt() != null);
        return dto;
    }
}
