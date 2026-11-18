package com.rehome.rehome.initializer;

import com.rehome.rehome.entity.marketplace.ReceiptClauseTemplate;
import com.rehome.rehome.repository.ReceiptClauseTemplateRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seed 6 điều khoản dạng đoạn văn, nguyên văn từ bản hardcode cũ ở mobile
 * ({@code receiptTerms.js} — lúc đó là Điều 4, 5, 6, 7, 9, 10), chỉ đổi số liệu sang placeholder
 * để Admin chỉnh được từ đây, và dịch số theo cách đánh số mới (Điều 1–4 cố định trong code,
 * nên 6 điều này giờ là Điều 5–10).
 *
 * <p>Chỉ tạo khi bảng rỗng, nên Admin sửa/xoá rồi thì restart không dựng lại.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.initializer.seed-enabled", havingValue = "true")
public class ReceiptClauseTemplateInitializer implements CommandLineRunner {

    private final ReceiptClauseTemplateRepository receiptClauseTemplateRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (receiptClauseTemplateRepository.count() > 0) {
            return;
        }

        // Điều 1–4 (Hàng hoá / Giá & hoa hồng / Thời hạn / Vô thừa nhận) cố định trong code,
        // không nằm trong bảng này -> 6 điều Admin quản lý tiếp tục từ Điều 5
        List<ReceiptClauseTemplate> seed = List.of(
                clause(1, "ĐIỀU 5 — GIẢM GIÁ",
                        "Sau khi xác nhận, bạn chỉ được giảm giá bán, mỗi ngày tối đa 1 lần và không "
                                + "thấp hơn giá sàn {{floorPrice}}. Muốn tăng giá phải lập lại biên nhận với ReHome."),
                clause(2, "ĐIỀU 6 — RÚT HÀNG",
                        "Trong {{freeWithdrawalWindowDays}} ngày đầu của mỗi kỳ, bạn được rút hàng "
                                + "không mất phí. Ngoài khoảng này, phí rút hàng là {{withdrawalFee}}."),
                clause(3, "ĐIỀU 7 — HOA HỒNG VÀ THANH TOÁN",
                        "Hoa hồng tính theo kỳ đang chạy tại thời điểm đơn hàng được đặt. Tiền bán hàng "
                                + "sau khi trừ hoa hồng được cộng vào ví ReHome khi đơn hoàn thành."),
                clause(4, "ĐIỀU 8 — HÀNG XUỐNG CẤP",
                        "Nếu hàng bị hư hỏng trong thời gian ReHome giữ hàng, bạn được bồi thường "
                                + "{{degradedCompensationRate}} giá bán hiện tại."),
                clause(5, "ĐIỀU 9 — HIỆU LỰC",
                        "Các mức phí, tỷ lệ và thời hạn trong biên nhận được giữ nguyên đến khi kết "
                                + "thúc. ReHome thay đổi cấu hình sau này không ảnh hưởng biên nhận này."),
                clause(6, "ĐIỀU 10 — XÁC NHẬN ĐIỆN TỬ",
                        "Biên nhận không có chữ ký tay. Giá trị xác nhận dựa trên thao tác bấm xác "
                                + "nhận trên tài khoản đã đăng nhập, cùng thời điểm được hệ thống ghi nhận và lưu "
                                + "trong nhật ký."));

        receiptClauseTemplateRepository.saveAll(seed);
        log.info("Seed receipt_clause_templates: tạo {} điều khoản", seed.size());
    }

    private static ReceiptClauseTemplate clause(int order, String title, String body) {
        return ReceiptClauseTemplate.builder()
                .displayOrder(order)
                .title(title)
                .bodyTemplate(body)
                .active(true)
                .build();
    }
}
