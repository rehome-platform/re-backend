package com.rehome.rehome.mapper;

import com.rehome.rehome.dto.response.ConsignmentReceiptConfirmResponse;
import com.rehome.rehome.dto.response.ConsignmentReceiptResponse;
import com.rehome.rehome.entity.marketplace.ConsignmentForm;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.EndOfTermDecision;

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
        dto.setPdfAvailable(r.getConfirmedAt() != null);
        return dto;
    }

    // Bản đầy đủ cho màn xác nhận biên nhận; phải gọi trong transaction vì đọc quan hệ lazy
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

    // Thêm productId và previousPrice khi biên nhận sinh ra từ quyết định ký gửi lại
    public static ConsignmentReceiptResponse toResponse(ConsignmentReceipt r, String sku,
                                                        EndOfTermDecision reConsignDecision) {
        ConsignmentReceiptResponse dto = toResponse(r, sku);
        if (dto != null && reConsignDecision != null) {
            dto.setProductId(reConsignDecision.getProduct().getId());
            dto.setPreviousPrice(reConsignDecision.getProduct().getCurrentPrice());
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