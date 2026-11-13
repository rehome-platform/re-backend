package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.MembershipTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GET /tryon/live/quota - mobile gọi trước khi mở camera để hiện "còn X/{limitPerDay} lượt hôm nay"
 * hoặc ẩn nút khi hết lượt.
 *
 * <p>Shape cũ (chỉ có {@code dailyLimit}/{@code usedToday}/{@code remaining}) đã bị thay: hạn mức
 * giờ lấy theo gói thành viên nên phải trả cả {@code tier} và gợi ý nâng cấp.
 *
 * <ul>
 *   <li>{@code limitPerDay = null} -> không giới hạn, hiện "Không giới hạn", {@code remainingToday}
 *       cũng null.</li>
 *   <li>Chỉ khi {@code limitPerMonth != null} mới hiện thêm "· Y/{limitPerMonth} tháng này"
 *       (seed hiện tại là null nên thường không hiện).</li>
 *   <li>{@code canStart = false} khi hết lượt ngày HOẶC hết lượt tháng.</li>
 *   <li>{@code upsellTier} = hạng thấp nhất có hạn mức cao hơn hạng hiện tại, để hiện nút
 *       "Nâng cấp để thử thêm". null = đã ở hạng cao nhất -> ẩn nút.</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LiveTryOnQuotaResponse {

    private MembershipTier tier;
    private String tierName;
    private Integer limitPerDay;
    private long usedToday;
    private Integer remainingToday;
    private Integer limitPerMonth;
    private long usedThisMonth;
    private Integer remainingThisMonth;
    private int maxSeconds;
    private boolean canStart;
    private MembershipTier upsellTier;
}
