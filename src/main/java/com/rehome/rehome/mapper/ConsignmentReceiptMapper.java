package com.rehome.rehome.mapper;

import com.rehome.rehome.dto.response.ConsignmentReceiptConfirmResponse;
import com.rehome.rehome.dto.response.ConsignmentReceiptResponse;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;

public final class ConsignmentReceiptMapper {

    private ConsignmentReceiptMapper() {
    }

    public static ConsignmentReceiptResponse toResponse(ConsignmentReceipt r) {
        if (r == null) {
            return null;
        }
        ConsignmentReceiptResponse dto = new ConsignmentReceiptResponse();
        dto.setId(r.getId());
        dto.setReceiptNo(r.getReceiptNo());
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
        dto.setPdfUrl(r.getPdfUrl());
        return dto;
    }

    public static ConsignmentReceiptConfirmResponse toConfirmResponse(ConsignmentReceipt r) {
        ConsignmentReceiptConfirmResponse dto = new ConsignmentReceiptConfirmResponse();
        dto.setId(r.getId());
        dto.setConfirmedAt(r.getConfirmedAt());
        dto.setPdfUrl(r.getPdfUrl());
        return dto;
    }
}