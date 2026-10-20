package com.rehome.rehome.ServiceImpl;

import com.rehome.rehome.dto.response.ConsignmentReceiptResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.mapper.ConsignmentReceiptMapper;
import com.rehome.rehome.repository.ConsignmentReceiptRepository;
import com.rehome.rehome.service.ConsignmentReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConsignmentReceiptServiceImpl implements ConsignmentReceiptService {

    private final ConsignmentReceiptRepository consignmentReceiptRepository;

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
}
