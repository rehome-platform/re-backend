package com.rehome.rehome.component;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.*;
import com.rehome.rehome.entity.marketplace.*;
import com.rehome.rehome.entity.marketplace.Order;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.service.NotificationService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BusinessNotificationsTest {
    @Test void queueNotificationPreservesWarehouseAndOnlyDeliversAfterCommit() {
        UUID warehouseId = UUID.randomUUID();
        UUID donationId = UUID.randomUUID();
        notifications.send(member, NotificationType.QUEUE_TASK, "Queue", "Donation", NotificationTargetType.DONATION,
                donationId, warehouseId);
        verifyNoInteractions(notificationService);
        commitCallbacks();
        verify(notificationService).notify(member, NotificationType.QUEUE_TASK, "Queue", "Donation",
                NotificationTargetType.DONATION, donationId, warehouseId);
    }
    @Mock NotificationService notificationService;
    @Mock AccountRepository accountRepository;
    @Mock PlatformTransactionManager transactionManager;
    @InjectMocks BusinessNotifications notifications;
    Account member = Account.builder().id(UUID.randomUUID()).build();

    @BeforeEach void begin() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        lenient().when(accountRepository.findById(member.getId())).thenReturn(Optional.of(member));
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }
    @AfterEach void clear() {
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }
    void commitCallbacks() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    @Test void onlyDeliversAfterCommit_inIndependentTransaction_withCorrectOrderTarget() {
        Order order = Order.builder().id(UUID.randomUUID()).orderNo("DH-000123").buyer(member).build();
        notifications.order(order, "Đơn đang giao", "Mã GHN ABC");
        verifyNoInteractions(notificationService, transactionManager);
        commitCallbacks();
        verify(notificationService).notify(eq(member), eq(NotificationType.ORDER_UPDATE), eq("Đơn đang giao"),
                contains("DH-000123"), eq(NotificationTargetType.ORDER), eq(order.getId()), isNull());
        var tx = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(tx.capture());
        assertThat(tx.getValue().getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Test void rollbackDiscardsNotification() {
        notifications.send(member, NotificationType.WALLET_EVENT, "title", "body", NotificationTargetType.PAYOUT, UUID.randomUUID());
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verifyNoInteractions(notificationService, accountRepository, transactionManager);
    }

    @Test void notificationFailureCannotEscapeAfterCommit() {
        doThrow(new IllegalStateException("database or push unavailable")).when(notificationService)
                .notify(any(), any(), any(), any(), any(), any(), any());
        notifications.send(member, NotificationType.WALLET_EVENT, "title", "body", NotificationTargetType.PAYOUT, UUID.randomUUID());
        assertThatCode(this::commitCallbacks).doesNotThrowAnyException();
        verify(transactionManager).rollback(any());
    }

    @Test void stringSkuAndMessageAreSnapshottedBeforeCommit() {
        Product product = Product.builder().id("Q7-AK-000123").member(member).name("Áo khoác").build();
        notifications.product(product, NotificationType.ORDER_UPDATE, "Đã bán", "đơn DH-123");
        product.setName("Changed after scheduling");
        commitCallbacks();
        verify(notificationService).notifyProduct(eq(member), eq(NotificationType.ORDER_UPDATE), eq("Đã bán"),
                contains("Áo khoác"), eq("Q7-AK-000123"), isNull());
    }

    @Test void receiptFormAndReturnUseDifferentDeepLinks() {
        ConsignmentForm form = ConsignmentForm.builder().id(UUID.randomUUID()).member(member)
                .formNo("KG-1").productName("Áo").build();
        ConsignmentReceipt receipt = ConsignmentReceipt.builder().id(UUID.randomUUID()).member(member)
                .receiptNo("BN-1").consignmentForm(form).build();
        Order order = Order.builder().id(UUID.randomUUID()).buyer(member).orderNo("DH-1").build();
        ReturnRequest returned = ReturnRequest.builder().id(UUID.randomUUID()).order(order).build();
        notifications.form(form, "Duyệt", "Hạn 20/11");
        notifications.receipt(receipt);
        notifications.returned(returned, NotificationType.WALLET_EVENT, "Hoàn tiền", "Đã vào ví");
        commitCallbacks();
        verify(notificationService).notify(eq(member), eq(NotificationType.QUEUE_TASK), eq("Duyệt"), contains("20/11"),
                eq(NotificationTargetType.CONSIGNMENT_FORM), eq(form.getId()), isNull());
        verify(notificationService).notify(eq(member), eq(NotificationType.QUEUE_TASK), anyString(), contains("BN-1"),
                eq(NotificationTargetType.CONSIGNMENT_RECEIPT), eq(receipt.getId()), isNull());
        verify(notificationService).notify(eq(member), eq(NotificationType.WALLET_EVENT), anyString(), contains("DH-1"),
                eq(NotificationTargetType.RETURN_REQUEST), eq(returned.getId()), isNull());
    }
}
