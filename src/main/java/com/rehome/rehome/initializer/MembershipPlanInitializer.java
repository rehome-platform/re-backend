package com.rehome.rehome.initializer;

import com.rehome.rehome.entity.enums.MembershipTier;
import com.rehome.rehome.entity.membership.MembershipPlan;
import com.rehome.rehome.repository.MembershipPlanRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seed 4 dòng {@code membership_plans} (FREE/SILVER/GOLD/DIAMOND), giống cách
 * {@code SystemConfigInitializer} seed tham số cấu hình.
 *
 * <p><b>Chỉ tạo hạng CHƯA có dòng; không bao giờ ghi đè giá trị Admin đã sửa.</b> Các con số dưới
 * đây là <b>giá trị seed ban đầu</b>, không phải nguồn cấu hình: Admin đổi được hết ở màn W-01 và
 * lần restart sau sẽ không bị initializer ghi về số cũ. Muốn đổi hạn mức thì sửa trong app, đừng
 * sửa file này.
 *
 * <p>{@code live_tryon_per_month} / {@code ai_tryon_per_month} seed {@code null} = KHÔNG chặn theo
 * tháng (hạn mức chính là theo ngày). Cột vẫn tồn tại để sau này siết thêm trần tháng mà không phải
 * migrate DB - xem cảnh báo chi phí ở đặc tả §2.2: hạn mức ngày của DIAMOND mở trần lý thuyết tới
 * 600 lượt real-time + 1.800 lượt phối đồ mỗi tháng cho 99.000đ.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class MembershipPlanInitializer implements CommandLineRunner {

    private final MembershipPlanRepository membershipPlanRepository;

    @Override
    @Transactional
    public void run(String... args) {
        int created = 0;
        created += seed(MembershipPlan.builder()
                .tier(MembershipTier.FREE)
                .name("Thành viên thường")
                .description("Hạn mức cơ bản, miễn phí cho mọi thành viên")
                .pricePerMonth(BigDecimal.ZERO)
                .pricePerYear(null)
                .liveTryonPerDay(2)
                .liveTryonPerMonth(null)
                .liveTryonMaxSeconds(10)
                .aiTryonPerDay(5)
                .aiTryonPerMonth(null)
                .featuredListing(false)
                .maxPendingConsignmentForms(5)
                // FREE không mua được: nó là hạng mặc định, tồn tại để hạn mức miễn phí và trả phí
                // cùng đọc từ một nguồn
                .purchasable(false)
                .active(true)
                .sortOrder(0)
                .build());

        created += seed(MembershipPlan.builder()
                .tier(MembershipTier.SILVER)
                .name("Thành viên Bạc")
                .description("Gói nhập môn, dùng thử đồ thoải mái hơn")
                .pricePerMonth(new BigDecimal("29000"))
                // Gói năm seed = 10 tháng tiền (tiết kiệm ~17%)
                .pricePerYear(new BigDecimal("290000"))
                .liveTryonPerDay(5)
                .liveTryonPerMonth(null)
                .liveTryonMaxSeconds(10)
                .aiTryonPerDay(15)
                .aiTryonPerMonth(null)
                // Bảng quyền lợi gốc ghi SILVER là "không", nhưng quyết định chốt (§17 mục 12) là
                // SILVER TRỞ LÊN đều có. Muốn thu hẹp lại thì Admin tắt ở W-01, không sửa code.
                .featuredListing(true)
                .maxPendingConsignmentForms(10)
                .purchasable(true)
                .active(true)
                .sortOrder(1)
                .build());

        created += seed(MembershipPlan.builder()
                .tier(MembershipTier.GOLD)
                .name("Thành viên Vàng")
                .description("Gói phổ thông, có voucher đều đặn")
                .pricePerMonth(new BigDecimal("59000"))
                .pricePerYear(new BigDecimal("590000"))
                .liveTryonPerDay(10)
                .liveTryonPerMonth(null)
                .liveTryonMaxSeconds(15)
                .aiTryonPerDay(40)
                .aiTryonPerMonth(null)
                .featuredListing(true)
                .maxPendingConsignmentForms(15)
                .purchasable(true)
                .active(true)
                .sortOrder(2)
                .build());

        created += seed(MembershipPlan.builder()
                .tier(MembershipTier.DIAMOND)
                .name("Thành viên Kim cương")
                .description("Gói cao cấp, gần như không giới hạn")
                .pricePerMonth(new BigDecimal("99000"))
                .pricePerYear(new BigDecimal("990000"))
                .liveTryonPerDay(20)
                .liveTryonPerMonth(null)
                .liveTryonMaxSeconds(20)
                .aiTryonPerDay(60)
                .aiTryonPerMonth(null)
                .featuredListing(true)
                .maxPendingConsignmentForms(30)
                .purchasable(true)
                .active(true)
                .sortOrder(3)
                .build());

        log.info("Seed membership_plans: tạo {} gói mới", created);
    }

    private int seed(MembershipPlan plan) {
        if (membershipPlanRepository.findByTier(plan.getTier()).isPresent()) {
            return 0;
        }
        membershipPlanRepository.save(plan);
        return 1;
    }
}
