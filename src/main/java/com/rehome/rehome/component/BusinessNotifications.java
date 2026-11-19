package com.rehome.rehome.component;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.NotificationTargetType;
import com.rehome.rehome.entity.enums.NotificationType;
import com.rehome.rehome.entity.marketplace.*;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.service.NotificationService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Snapshot notification data before commit; persist and deliver in an independent transaction afterwards. */
@Component
@RequiredArgsConstructor
@Slf4j
public class BusinessNotifications {
    private final NotificationService notificationService;
    private final AccountRepository accountRepository;
    private final PlatformTransactionManager transactionManager;

    public void send(Account recipient, NotificationType type, String title, String body,
                     NotificationTargetType target, UUID targetId) {
        schedule(recipient.getId(), type, title, body, target, targetId.toString());
    }

    public void order(Order order, String title, String message) {
        send(order.getBuyer(), NotificationType.ORDER_UPDATE, title,
                "Đơn " + order.getOrderNo() + ": " + message, NotificationTargetType.ORDER, order.getId());
    }

    public void product(Product product, NotificationType type, String title, String message) {
        schedule(product.getMember().getId(), type, title, "Món \"" + product.getName() + "\": " + message,
                NotificationTargetType.PRODUCT, product.getId());
    }

    public void form(ConsignmentForm form, String title, String message) {
        send(form.getMember(), NotificationType.QUEUE_TASK, title,
                "Yêu cầu " + form.getFormNo() + " · " + form.getProductName() + ": " + message,
                NotificationTargetType.CONSIGNMENT_FORM, form.getId());
    }

    public void receipt(ConsignmentReceipt receipt) {
        send(receipt.getMember(), NotificationType.QUEUE_TASK, "Biên nhận ký gửi cần xác nhận",
                "Biên nhận " + receipt.getReceiptNo() + " · " + receipt.getConsignmentForm().getProductName()
                        + " đã được lập. Vui lòng xem và xác nhận.",
                NotificationTargetType.CONSIGNMENT_RECEIPT, receipt.getId());
    }

    public void returned(ReturnRequest request, NotificationType type, String title, String message) {
        send(request.getOrder().getBuyer(), type, title,
                "Trả hàng đơn " + request.getOrder().getOrderNo() + ": " + message,
                NotificationTargetType.RETURN_REQUEST, request.getId());
    }

    private void schedule(UUID recipientId, NotificationType type, String title, String body,
                          NotificationTargetType target, String targetId) {
        Runnable deliver = () -> {
            try {
                TransactionTemplate transaction = new TransactionTemplate(transactionManager);
                transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                transaction.executeWithoutResult(status -> {
                    Account recipient = accountRepository.findById(recipientId).orElseThrow();
                    if (target == NotificationTargetType.PRODUCT) {
                        notificationService.notifyProduct(recipient, type, title, body, targetId, null);
                    } else {
                        notificationService.notify(recipient, type, title, body, target, UUID.fromString(targetId), null);
                    }
                });
            } catch (Exception ex) {
                log.error("Notification failed after business commit: recipient={}, target={}, id={}",
                        recipientId, target, targetId, ex);
            }
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { deliver.run(); }
            });
        } else {
            deliver.run();
        }
    }
}
