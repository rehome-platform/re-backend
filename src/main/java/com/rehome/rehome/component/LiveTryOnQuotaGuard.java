package com.rehome.rehome.component;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.LiveTryOnStatus;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.tryon.LiveTryOnSession;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.LiveTryOnSessionRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đếm + trừ quota "N lượt thử đồ AI real-time / ngày" trong một bean riêng, TÁCH khỏi
 * {@code LiveTryOnServiceImpl}: method {@code @Transactional} chỉ có hiệu lực khi được gọi qua
 * proxy Spring, tức là qua một bean KHÁC - gọi {@code this.method()} trong cùng class (self-invocation)
 * sẽ bỏ qua proxy và chạy không transaction. Tách ra đây để {@link #reserve} thực sự atomic.
 *
 * <p>Vẫn không chặn 100% race nếu cùng 1 account bắn 2 request start gần như đồng thời (đếm xong
 * ở 2 transaction song song trước khi transaction nào kịp insert) - chấp nhận cho MVP, lệch tối
 * đa 1 lượt, không cần thêm bảng counter riêng + khoá dòng.
 */
@Component
@RequiredArgsConstructor
public class LiveTryOnQuotaGuard {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final LiveTryOnSessionRepository liveTryOnSessionRepository;

    @Value("${tryon.live.daily-limit:2}")
    private int dailyLimit;

    public int getDailyLimit() {
        return dailyLimit;
    }

    public int countToday(UUID memberId) {
        LocalDate today = LocalDate.now(VN_ZONE);
        Instant startOfDay = today.atStartOfDay(VN_ZONE).toInstant();
        Instant endOfDay = today.plusDays(1).atStartOfDay(VN_ZONE).toInstant();
        return (int) liveTryOnSessionRepository.countByMember_IdAndCreatedAtBetween(memberId, startOfDay, endOfDay);
    }

    // Đây là nơi quota THỰC SỰ bị trừ: tạo row STARTED ngay trước khi gọi Decart mint token,
    // không đợi mobile xác nhận kết nối camera thành công (xem DecartVtonClient).
    @Transactional
    public LiveTryOnSession reserve(Account account, Product product, String modelId) {
        if (countToday(account.getId()) >= dailyLimit) {
            throw new AppException(ErrorCode.TRYON_LIVE_QUOTA_EXCEEDED);                             // 429
        }
        return liveTryOnSessionRepository.save(LiveTryOnSession.builder()
                .member(account)
                .product(product)
                .modelId(modelId)
                .status(LiveTryOnStatus.STARTED)
                .build());
    }
}
