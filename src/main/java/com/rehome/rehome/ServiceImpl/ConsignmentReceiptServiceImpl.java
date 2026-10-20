package com.rehome.rehome.ServiceImpl;

import com.rehome.rehome.dto.response.ConsignmentReceiptConfirmResponse;
import com.rehome.rehome.dto.response.ConsignmentReceiptResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.mapper.ConsignmentReceiptMapper;
import com.rehome.rehome.repository.ConsignmentReceiptRepository;
import com.rehome.rehome.service.ConsignmentReceiptService;
import com.rehome.rehome.service.ReceiptPdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConsignmentReceiptServiceImpl implements ConsignmentReceiptService {

    private final ConsignmentReceiptRepository consignmentReceiptRepository;
    private final ReceiptPdfService receiptPdfService;   // interface, không phải ReceiptPdfServiceImpl
    @Override
    public ConsignmentReceiptResponse getMyReceipt(Account account, UUID id) {
        if(account.getRole() != Role.MEMBER){
            throw new AppException(ErrorCode.NOT_MEMBER_ACCOUNT);
        }

        ConsignmentReceipt receipt = consignmentReceiptRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONSIGNMENT_RECEIPT_NOT_FOUND));

        if (!receipt.getMember().getId().equals(account.getId())) {
            throw new AppException(ErrorCode.CONSIGNMENT_RECEIPT_FORBIDDEN);
        }

        return ConsignmentReceiptMapper.toResponse(receipt);
    }

    @Override
    @Transactional
    public ConsignmentReceiptConfirmResponse confirmReceipt(Account account, UUID id) {
        // Khóa dòng để chống bấm xác nhận đồng thời
        ConsignmentReceipt receipt = consignmentReceiptRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONSIGNMENT_RECEIPT_NOT_FOUND));

        if (!receipt.getMember().getId().equals(account.getId())) {
            throw new AppException(ErrorCode.CONSIGNMENT_RECEIPT_FORBIDDEN);
        }
        if (receipt.getConfirmedAt() != null) {
            throw new AppException(ErrorCode.CONSIGNMENT_RECEIPT_ALREADY_CONFIRMED);
        }

        Instant now = Instant.now();

        // Set confirmedAt trước để PDF snapshot có đúng mốc xác nhận
        receipt.setConfirmedAt(now);
        receipt.setPdfUrl(receiptPdfService.generate(receipt)); // F-022

        // TODO: ghi audit log (account, receipt id, thời điểm xác nhận)

        consignmentReceiptRepository.save(receipt);

        return ConsignmentReceiptMapper.toConfirmResponse(receipt);
    }
}
