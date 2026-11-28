package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.component.BusinessNotifications;
import com.rehome.rehome.entity.enums.NotificationType;
import com.rehome.rehome.entity.enums.QueueType;
import com.rehome.rehome.entity.enums.ReturnRequestStatus;
import com.rehome.rehome.entity.enums.ShipBackStatus;
import com.rehome.rehome.entity.marketplace.ReturnRequest;
import com.rehome.rehome.repository.ReturnRequestRepository;
import com.rehome.rehome.service.QueueService;
import com.rehome.rehome.service.ReturnRequestAutoExpireService;
import com.rehome.rehome.util.NotificationFormatting;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReturnRequestAutoExpireServiceImpl implements ReturnRequestAutoExpireService {

    private static final Set<ShipBackStatus> ACTIVE_OR_DONE_SHIP_BACK = EnumSet.of(
            ShipBackStatus.PAID, ShipBackStatus.SHIPPING, ShipBackStatus.DELIVERED);

    private final ReturnRequestRepository returnRequestRepository;
    private final QueueService queueService;
    private final BusinessNotifications businessNotifications;

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
            queueService.dequeue(QueueType.RETURN, returnRequest.getId().toString());

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
            if (returnRequest.getShipBackStatus() != null
                    && ACTIVE_OR_DONE_SHIP_BACK.contains(returnRequest.getShipBackStatus())) {
                continue;
            }
            if (returnRequest.getShipBackStatus() == ShipBackStatus.AWAITING_PAYMENT) {
                returnRequest.setInvalidReturnChoice(null);
                ReturnRequestServiceImpl.clearUnpaidShipBackFields(returnRequest);
            }
            returnRequest.setResolutionNote("Quá hạn đến kho nhận lại hàng trả không hợp lệ.");
            returnRequest.setResolvedAt(Instant.now());
            returnRequestRepository.save(returnRequest);

            log.info(">>>> Het han nhan lai hang tra khong hop le: returnRequestId={}", returnRequest.getId());
        }
    }

    @Override
    @Scheduled(fixedRateString = "${app.return.auto-expire-check-interval-ms}")
    @Transactional
    public void expireOverdueShipBackPayments() {

        Instant now = Instant.now();
        List<ReturnRequest> overdue = returnRequestRepository
                .findAllByShipBackStatusAndShipBackFeeDeadlineBefore(ShipBackStatus.AWAITING_PAYMENT, now);

        for (ReturnRequest returnRequest : overdue) {
            returnRequest.setInvalidReturnChoice(null);
            ReturnRequestServiceImpl.clearUnpaidShipBackFields(returnRequest);
            returnRequestRepository.save(returnRequest);

            if (returnRequest.getResolvedAt() == null
                    && (returnRequest.getPickupDeadline() == null || !now.isAfter(returnRequest.getPickupDeadline()))
                    && returnRequest.getOrder() != null) {
                String message = returnRequest.getPickupDeadline() != null
                        ? "Quá hạn trả phí gửi lại, vui lòng chọn lại cách nhận hàng trước "
                                + NotificationFormatting.deadline(returnRequest.getPickupDeadline()) + "."
                        : "Quá hạn trả phí gửi lại, vui lòng chọn lại cách nhận hàng.";
                businessNotifications.returned(returnRequest, NotificationType.ORDER_UPDATE,
                        "Quá hạn trả phí gửi lại hàng", message);
            }

            log.info(">>>> Qua han tra phi gui lai hang tra, xoa lua chon: returnRequestId={}", returnRequest.getId());
        }
    }
}
