package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.entity.enums.ReturnRequestStatus;
import com.rehome.rehome.entity.marketplace.ReturnRequest;
import com.rehome.rehome.repository.ReturnRequestRepository;
import com.rehome.rehome.service.ReturnRequestAutoExpireService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

// F-069 + F-071: hai mốc hết hạn của flow trả hàng
@Service
@RequiredArgsConstructor
@Slf4j
public class ReturnRequestAutoExpireServiceImpl implements ReturnRequestAutoExpireService {

    private final ReturnRequestRepository returnRequestRepository;

    @Override
    @Scheduled(fixedRateString = "${app.return.auto-expire-check-interval-ms}")
    @Transactional
    public void cancelNotHandedOverRequests() {

        List<ReturnRequest> expired = returnRequestRepository.findAllByStatusAndHandoverDeadlineBefore(
                ReturnRequestStatus.APPROVED, Instant.now());

        for (ReturnRequest returnRequest : expired) {
            returnRequest.setStatus(ReturnRequestStatus.CANCELLED);
            returnRequest.setResolutionNote("Quá hạn bàn giao hàng trả cho GHN, yêu cầu bị hủy tự động.");
            returnRequest.setResolvedAt(Instant.now());
            returnRequestRepository.save(returnRequest);

            log.info(">>>> Tu huy yeu cau tra hang qua han ban giao: returnRequestId={}", returnRequest.getId());
        }
    }

    @Override
    @Scheduled(fixedRateString = "${app.return.auto-expire-check-interval-ms}")
    @Transactional
    public void expireUncollectedInvalidReturns() {

        List<ReturnRequest> expired = returnRequestRepository
                .findAllByStatusAndPickupDeadlineBeforeAndResolvedAtIsNull(
                        ReturnRequestStatus.INVALID, Instant.now());

        for (ReturnRequest returnRequest : expired) {
            returnRequest.setResolutionNote("Quá hạn đến kho nhận lại hàng trả không hợp lệ.");
            returnRequest.setResolvedAt(Instant.now());
            returnRequestRepository.save(returnRequest);

            log.info(">>>> Het han nhan lai hang tra khong hop le: returnRequestId={}", returnRequest.getId());
        }
    }
}
