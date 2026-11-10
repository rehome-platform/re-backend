package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.entity.enums.EndOfTermChoice;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.EndOfTermDecision;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.repository.EndOfTermDecisionRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.service.EndOfTermDecisionService;
import com.rehome.rehome.service.EndOfTermLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
