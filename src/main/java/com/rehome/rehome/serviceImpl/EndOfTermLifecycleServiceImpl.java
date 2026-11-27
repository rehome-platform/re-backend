package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.component.BusinessNotifications;
import com.rehome.rehome.constant.SystemConfigKeys;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.EndOfTermChoice;
import com.rehome.rehome.entity.enums.NotificationType;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.ReConsignNegotiationAction;
import com.rehome.rehome.entity.enums.ReConsignStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.EndOfTermDecision;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.ReConsignNegotiationHistory;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.ConsignmentReceiptRepository;
import com.rehome.rehome.repository.EndOfTermDecisionRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.repository.ReConsignNegotiationHistoryRepository;
import com.rehome.rehome.service.EndOfTermDecisionService;
import com.rehome.rehome.service.EndOfTermLifecycleService;
import com.rehome.rehome.service.SystemConfigReader;
import com.rehome.rehome.util.NotificationFormatting;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

// Điều 8 hợp đồng: kỳ ký gửi tự chạy mà không cần ai bấm gì - hết kỳ thì sang kỳ, hết 90 ngày
// thì gỡ khỏi sàn và mở quyết định cho Member, TAKE_BACK không đến lấy quá hạn thì vô thừa nhận
@Service
@RequiredArgsConstructor
@Slf4j
public class EndOfTermLifecycleServiceImpl implements EndOfTermLifecycleService {

    private final ProductRepository productRepository;
    private final EndOfTermDecisionRepository endOfTermDecisionRepository;
    private final EndOfTermDecisionService endOfTermDecisionService;
    private final ConsignmentReceiptRepository consignmentReceiptRepository;
    private final AccountRepository accountRepository;
    private final BusinessNotifications businessNotifications;
    private final SystemConfigReader systemConfigReader;
    private final ReConsignNegotiationHistoryRepository negotiationHistoryRepository;

    // Điều 8.3: 30 ngày kể từ lúc chọn TAKE_BACK để đến kho nhận hàng, quá hạn thì vô thừa nhận
    @Value("${app.end-of-term.take-back-pickup-deadline-days}")
    private long takeBackPickupDeadlineDays;

    @Override
    @Scheduled(fixedRateString = "${app.end-of-term.auto-process-check-interval-ms}")
    @Transactional
    public void processTermsAndDeadlines() {
        Instant now = Instant.now();
        advanceTerms(now);
        openEndOfTermDecisions(now);
        markUnclaimedForOverdueTakeBack(now);
        remindReConsignProposals(now);
        expireReConsignProposals(now);
        flagOverdueReConsignCounterOffers(now);
    }

    // F-029 (Điều 8): mỗi kỳ dài termLengthDays (snapshot trên biên nhận); còn kỳ để lên thì tăng
    // currentTerm và dời lại termStartedAt, mốc tính cửa sổ rút hàng miễn phí 7 ngày đầu mỗi kỳ
    private void advanceTerms(Instant now) {
        for (Product product : productRepository.findOnSaleNotYetFinalTerm()) {
            ConsignmentReceipt receipt = product.getConsignmentReceipt();
            Instant currentTermDeadline = product.getTermStartedAt()
                    .plus(receipt.getTermLengthDays(), ChronoUnit.DAYS);

            if (!now.isBefore(currentTermDeadline)) {
                short nextTerm = (short) (product.getCurrentTerm() + 1);
                product.setCurrentTerm(nextTerm);
                product.setTermStartedAt(now);
                productRepository.save(product);

                log.info(">>>> Sang ky moi: productId={}, currentTerm={}", product.getId(), nextTerm);
            }
        }
    }

    // Điều 8.1: hết 90 ngày (kỳ cuối, endOfTermDeadline đã tính sẵn lúc nhập kho/ký gửi lại) ->
    // gỡ khỏi sàn ngay, mở EndOfTermDecision cho Member chọn TAKE_BACK/RE_CONSIGN trong 30 ngày
    private void openEndOfTermDecisions(Instant now) {
        for (Product product : productRepository
                .findAllByStatusAndEndOfTermDeadlineBefore(ProductStatus.ON_SALE, now)) {
            product.setStatus(ProductStatus.WAREHOUSE);
            productRepository.save(product);

            endOfTermDecisionService.createDecision(product, now);

            log.info(">>>> Het ky ky gui, mo quyet dinh cuoi ky: productId={}", product.getId());
        }
    }

    // Điều 8.3: Member đã chọn TAKE_BACK nhưng quá takeBackPickupDeadlineDays kể từ ngày quyết
    // định vẫn chưa đến kho nhận hàng (takenBackAt null) -> coi như từ bỏ quyền sở hữu
    private void markUnclaimedForOverdueTakeBack(Instant now) {
        Instant cutoff = now.minus(takeBackPickupDeadlineDays, ChronoUnit.DAYS);

        List<EndOfTermDecision> overdue = endOfTermDecisionRepository
                .findAllByDecisionAndTakenBackAtIsNullAndDecidedAtBefore(EndOfTermChoice.TAKE_BACK, cutoff);

        for (EndOfTermDecision decision : overdue) {
            Product product = decision.getProduct();
            product.setStatus(ProductStatus.UNCLAIMED);
            productRepository.save(product);

            log.info(">>>> Qua han den kho nhan hang sau TAKE_BACK, danh dau vo thua nhan: productId={}",
                    product.getId());
        }
    }

    // Nhắc Member 1 ngày trước proposalDeadline của đề xuất giá ký gửi lại đang chờ xác nhận/đề
    // xuất lại; mỗi vòng đề xuất chỉ nhắc 1 lần (proposalReminderSentAt reset mỗi vòng mới)
    private void remindReConsignProposals(Instant now) {
        Instant reminderBefore = now.plus(1, ChronoUnit.DAYS);

        for (EndOfTermDecision decision : endOfTermDecisionRepository
                .findDueForProposalReminder(now, reminderBefore)) {
            Product product = decision.getProduct();

            businessNotifications.product(product, NotificationType.TERM_EXPIRY,
                    "Sắp hết hạn xác nhận giá ký gửi lại",
                    "vui lòng xác nhận hoặc đề xuất lại trước hạn "
                            + NotificationFormatting.deadline(decision.getProposalDeadline()) + ".");

            decision.setProposalReminderSentAt(now);
            endOfTermDecisionRepository.save(decision);

            log.info(">>>> Da nhac Member sap het han xac nhan gia ky gui lai: decisionId={}", decision.getId());
        }
    }

    // Quá proposalDeadline mà Member không xác nhận/đề xuất lại -> coi như từ chối (Điều 8.3 áp
    // dụng y hệt decline của Member): huỷ biên nhận nháp, chuyển TAKE_BACK/WITHDRAWN
    private void expireReConsignProposals(Instant now) {
        for (EndOfTermDecision decision : endOfTermDecisionRepository
                .findAllByReConsignStatusAndProposalDeadlineBefore(ReConsignStatus.PROPOSED, now)) {
            Product product = decision.getProduct();
            ConsignmentReceipt draft = decision.getNewReceipt();

            if (draft != null) {
                draft.setStatus(com.rehome.rehome.entity.enums.ReceiptStatus.VOIDED);
                draft.setVoidReason("Quá hạn xác nhận đề xuất giá ký gửi lại");
                draft.setVoidedAt(now);
                consignmentReceiptRepository.save(draft);
            }

            decision.setReConsignStatus(ReConsignStatus.EXPIRED);
            decision.setDeclinedAt(now);
            decision.setDecision(EndOfTermChoice.TAKE_BACK);
            decision.setDecidedAt(now);
            endOfTermDecisionRepository.save(decision);

            negotiationHistoryRepository.save(ReConsignNegotiationHistory.builder()
                    .decision(decision)
                    .actor(Role.MODERATOR)
                    .action(ReConsignNegotiationAction.EXPIRE)
                    .note("Quá hạn xác nhận đề xuất giá ký gửi lại")
                    .build());

            product.setStatus(ProductStatus.WITHDRAWN);
            productRepository.save(product);

            businessNotifications.product(product, NotificationType.TERM_EXPIRY,
                    "Đã chuyển sang nhận lại hàng",
                    "bạn đã không xác nhận đề xuất giá ký gửi lại đúng hạn, vui lòng đến kho nhận lại hàng.");
            notifyWarehouseModerators(product, "Hết hạn xác nhận giá ký gửi lại",
                    "Món \"" + product.getName() + "\" chuyển sang nhận lại do Member không phản hồi đúng hạn.");

            log.info(">>>> Qua han xac nhan gia ky gui lai, chuyen TAKE_BACK: decisionId={}, productId={}",
                    decision.getId(), product.getId());
        }
    }

    // COUNTER_OFFERED quá re_consign_moderator_response_days ngày - chỉ đánh dấu quá hạn (log cảnh
    // báo để lên dashboard/CSKH theo dõi), KHÔNG tự xử lý: quyết định cuối cùng vẫn phải là của
    // Moderator (ACCEPT/REPROPOSE/KEEP), không có phương án hợp lý nào để hệ thống tự chọn thay.
    private void flagOverdueReConsignCounterOffers(Instant now) {
        int responseDays = systemConfigReader.getInt(SystemConfigKeys.RE_CONSIGN_MODERATOR_RESPONSE_DAYS,
                SystemConfigKeys.DEFAULT_RE_CONSIGN_MODERATOR_RESPONSE_DAYS);
        Instant cutoff = now.minus(responseDays, ChronoUnit.DAYS);

        List<EndOfTermDecision> overdue = endOfTermDecisionRepository
                .findAllByReConsignStatusAndCounteredAtBefore(ReConsignStatus.COUNTER_OFFERED, cutoff);

        for (EndOfTermDecision decision : overdue) {
            log.warn(">>>> COUNTER_OFFERED qua han phan hoi cua Moderator: decisionId={}, productId={}, counteredAt={}",
                    decision.getId(), decision.getProduct().getId(), decision.getCounteredAt());
        }
    }

    private void notifyWarehouseModerators(Product product, String title, String message) {
        if (product.getWarehouse() == null) return;
        for (Account moderator : accountRepository
                .findByRoleAndWarehouse_Id(Role.MODERATOR, product.getWarehouse().getId())) {
            businessNotifications.withoutTarget(moderator, NotificationType.QUEUE_TASK, title, message);
        }
    }
}
