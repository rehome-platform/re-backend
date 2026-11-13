# ĐẶC TẢ TÍNH NĂNG: GÓI THÀNH VIÊN VIP + VOUCHER + ƯU TIÊN HIỂN THỊ

Phiên bản: **1.3 (BẢN CHỐT)** — Ngày: 2026-11-13

- **v1.1**: chốt bảng quyền lợi thật — hạn mức tính theo NGÀY, thêm quyền lợi "số form ký gửi",
  bỏ giới hạn bộ sưu tập, voucher giảm đơn bắt buộc theo phần trăm, mọi giá trị Admin sửa được.
- **v1.2**: chốt mức voucher seed (ship 15.000đ cố định, giảm đơn 10% trần 50.000đ);
  **một mã cho cả lần thanh toán, phân bổ về từng đơn** (§12.6 — mới); hủy đơn đã thanh toán
  không trả mã; bỏ `member_vouchers.used_order_id`.
- **v1.3 — BẢN CHỐT**: "sản phẩm nổi bật" đổi từ *slot từng món* thành **bật cho toàn bộ hàng ký
  gửi, SILVER trở lên, tự động** (bỏ 2 API, 2 error code, nút bật/tắt ở M-09); chốt quy tắc đổi
  hạng giữa kỳ; bỏ phương án thay thế của §12.6. **Không còn mục nào chờ quyết định.**
Dành cho: team Mobile (app Member), team Web (khu vực Admin), team Backend.

---

## 0. CÁCH ĐỌC FILE NÀY

- **Phần 1–3**: nghiệp vụ, đọc trước để hiểu toàn cảnh.
- **Phần 4–7**: dành cho Backend (entity, enum, config, error code).
- **Phần 8–9**: hợp đồng API, dành cho cả 3 team.
- **Phần 10**: màn hình Mobile.
- **Phần 11**: màn hình Web Admin.
- **Phần 12–14**: quy tắc tính toán, ghi sổ, job nền — Backend bắt buộc đọc.
- **Phần 15–17**: thứ tự làm, chỗ code cũ bị ảnh hưởng, bảng các quyết định đã chốt.

> ✅ **File này đã chốt xong, không còn quyết định nào treo.** Toàn bộ lựa chọn nghiệp vụ nằm ở
> §17. Các con số giá và hạn mức là **giá trị seed** — Admin sửa được trong app, nên đổi số
> không cần sửa file này và không cần sửa code.

---

## 1. MỤC TIÊU VÀ PHẠM VI

### 1.1. Vấn đề

Thử đồ AI (ảnh tĩnh và real-time qua camera) là tính năng tốn chi phí thật theo từng lượt gọi
(API Decart cho real-time, tài nguyên sinh ảnh cho ảnh tĩnh). Hiện tại mọi Member đều dùng miễn
phí với hạn mức cứng `tryon.live.daily-limit` (mặc định 2 lượt/ngày), nền tảng chịu toàn bộ chi phí
và không có nguồn thu bù lại.

### 1.2. Giải pháp

Bán **gói thành viên trả phí theo tháng/năm** với 3 hạng: Bạc, Vàng, Kim cương. Quyền lợi gồm:

1. Hạn mức thử đồ AI cao hơn — real-time qua camera và phối đồ AI (ảnh tĩnh), tính theo ngày.
2. Voucher tặng định kỳ hằng tháng — miễn phí vận chuyển và giảm giá đơn hàng.
3. Toàn bộ hàng ký gửi được ưu tiên hiển thị trên sàn + nhãn "Nổi bật", tự động, không phải bật.
4. Gửi được nhiều form ký gửi chờ duyệt hơn.

### 1.3. Nguyên tắc bất di bất dịch

Những điều sau **không được vi phạm** vì đụng tới hợp đồng ký gửi (`docs/OVERVIEW.md` §Biên nhận):

| Nguyên tắc | Giải thích |
|---|---|
| Gói VIP **không** đổi tỷ lệ hoa hồng | Hoa hồng đã snapshot trong `consignment_receipts.commission_rate_term1/2/3` |
| Gói VIP **không** đổi phí rút hàng, kỳ ký gửi, giá sàn | Đều là snapshot trong biên nhận |
| Voucher do **nền tảng chịu**, không trừ vào tiền người ký gửi | `order_items.price_at_order` giữ nguyên giá bán, không trừ voucher |
| Nhãn "Nổi bật" **không** hàm ý chất lượng | Mọi món trên sàn đều đã qua kiểm định như nhau. Không dùng chữ "Chính hãng"/"Uy tín" |

### 1.4. Ngoài phạm vi (không làm ở phiên bản này)

- Tổ chức (ORGANIZATION) mua gói — chỉ MEMBER mua được.
- Nâng/hạ hạng giữa kỳ có tính tiền chênh lệch theo ngày (pro-rata).
- Gói quà tặng, chia sẻ voucher cho người khác.
- Tự động gia hạn (auto-renew) bằng thẻ lưu sẵn.
- Giới hạn số ảnh lưu trong bộ sưu tập thử đồ — giữ nguyên hành vi hiện tại, không gắn vào gói.
- Giảm hoa hồng, giảm phí rút hàng, kéo dài kỳ ký gửi — xem §1.3, tuyệt đối không làm.

---

## 2. MÔ HÌNH NGHIỆP VỤ

### 2.1. Bốn hạng thành viên

`FREE` là hạng mặc định của mọi Member, **không mua được**, tồn tại như một dòng dữ liệu để
hạn mức miễn phí và hạn mức trả phí cùng đọc từ một nguồn.

| Hạng | Mã | Mua được? |
|---|---|---|
| Thành viên thường | `FREE` | Không — mặc định |
| Thành viên Bạc | `SILVER` | Có |
| Thành viên Vàng | `GOLD` | Có |
| Thành viên Kim cương | `DIAMOND` | Có |

### 2.2. Bảng quyền lợi

> **TOÀN BỘ các ô trong bảng này đều là dữ liệu, không phải hằng số.** Tất cả nằm trong bảng
> `membership_plans` (và `voucher_template_tier_rules` cho 2 dòng voucher), Admin sửa được ở màn
> W-01 / W-04 — kể cả **giá theo tháng, giá theo năm, số lượt, số mã voucher**.
> Code **không được hard-code** bất kỳ con số nào dưới đây; các giá trị chỉ là **seed ban đầu**.

| Quyền lợi | FREE | SILVER | GOLD | DIAMOND | Nguồn dữ liệu |
|---|---|---|---|---|---|
| Giá / tháng (đ) | 0 | 29.000 | 59.000 | 99.000 | `membership_plans.price_per_month` |
| Giá / năm (đ) | — | 290.000 | 590.000 | 990.000 | `membership_plans.price_per_year` |
| Thử đồ AI real-time — lượt/ngày | 2 | 5 | 10 | 20 | `live_tryon_per_day` |
| Thử đồ AI real-time — giây/lượt | 10 | 10 | 15 | 20 | `live_tryon_max_seconds` |
| Phối đồ AI — lượt/ngày | 5 | 15 | 40 | 60 | `ai_tryon_per_day` |
| Voucher giảm giá đơn hàng — mã/tháng | 0 | 1 | 2 | 4 | `voucher_template_tier_rules` |
| Voucher miễn phí vận chuyển — mã/tháng | 0 | 2 | 4 | 6 | `voucher_template_tier_rules` |
| Sản phẩm nổi bật (toàn bộ hàng ký gửi) | ✗ | ✓ | ✓ | ✓ | `featured_listing` |
| Form ký gửi được gửi tối đa | 5 | 10 | 15 | 30 | `max_pending_consignment_forms` |

**Ghi chú từng dòng — đọc kỹ, đây là chỗ dễ hiểu sai nhất:**

1. **Thử đồ AI real-time** và **Phối đồ AI** đều tính **theo NGÀY**, reset lúc 00:00 giờ VN.
   Không có hạn mức theo tháng. Entity vẫn giữ sẵn cột `live_tryon_per_month` /
   `ai_tryon_per_month`, seed = `null` (không chặn), phòng khi sau này cần siết thêm trần tháng
   mà không phải migrate DB.

2. **Voucher giảm giá đơn hàng** bắt buộc là loại **PERCENT** (giảm theo phần trăm), và số tiền
   giảm **không được vượt quá hoa hồng nền tảng thu được theo kỳ ký gửi hiện tại** của món hàng.
   Kỳ 1 = 10%, kỳ 2 = 15%, kỳ 3 = 20% → cùng một mã giảm sẽ cho số tiền khác nhau tùy món đang ở
   kỳ nào. Công thức đầy đủ ở §12.1 bước B3.

3. **Sản phẩm nổi bật**: là quyền lợi **bật/tắt**, **không có khái niệm slot**. Hạng nào có
   `featured_listing = true` thì **TOÀN BỘ** hàng ký gửi đang `ON_SALE` của Member đó được xếp
   lên đầu danh sách và gắn nhãn "Nổi bật". Member **không phải thao tác gì** — hệ thống tự bật
   khi gói có hiệu lực và tự tắt khi hết hạn (§8.10).
   > Bảng gốc ghi SILVER là "không". Theo chốt mới nhất, **SILVER trở lên đều có**. Nếu sau này
   > muốn Bạc không còn quyền này, Admin chỉ cần tắt `featured_listing` của hạng Bạc ở W-01,
   > không phải sửa code.

4. **Form ký gửi**: là **số form đang chờ Moderator duyệt**, **không phải** số sản phẩm được lên
   sàn. Hiện con số này nằm ở `system_configs.max_pending_forms_per_member` (mặc định 5) và áp
   chung cho mọi Member — phải đổi sang đọc theo gói, xem §16.

5. **Bộ sưu tập ảnh thử đồ**: giữ nguyên như hiện tại, **không** gắn vào gói.

**Quy ước "không giới hạn"**: cột để `null` trong DB và trong JSON. Mobile hiển thị
"Không giới hạn" thay vì con số.

**Kiểm tra lãi/lỗ của từng gói** — Admin phải tự tính lại mỗi lần đổi giá:
```
Giá gói / tháng  ≥  Tổng mệnh giá voucher tặng trong tháng
                 +  (Số lượt thử đồ dự kiến dùng × Chi phí thật mỗi lượt)
                 +  Phần lãi mong muốn
```
> ⚠️ **Rủi ro của hạn mức theo ngày**: trần chi phí lý thuyết = hạn mức/ngày × 30 × chi phí
> mỗi lượt. DIAMOND cho phép 20 real-time + 60 phối đồ mỗi ngày, tức tối đa **600 + 1.800
> lượt/tháng** đổi lấy 99.000đ. Thực tế không ai dùng hết, nhưng **không được định giá dựa trên
> giả định đó**. Nếu chi phí thật mỗi lượt vượt khoảng 40đ, phải hạ hạn mức, tăng giá, hoặc bật
> thêm trần theo tháng bằng cột `*_per_month` đã có sẵn.

### 2.3. Vòng đời một gói

```
Member chọn gói  →  Thanh toán (Ví / MoMo / VNPay)
                 →  Subscription ACTIVE, expiresAt = now + 1 tháng (hoặc 1 năm)
                 →  Phát voucher đợt đầu ngay
                 →  Mỗi tháng job phát tiếp voucher
                 →  Trước hạn N ngày: thông báo nhắc gia hạn
                 →  Quá expiresAt: job chuyển EXPIRED, hạn mức về FREE
```

**Quy tắc gia hạn / đổi hạng** (đã chốt — §17 mục 16):
- Mỗi Member tại một thời điểm có **tối đa 1 subscription ACTIVE**.
- Mua khi đang còn hạn **cùng hạng** → cộng dồn thời gian vào `expiresAt` hiện tại.
- Mua khi đang còn hạn **khác hạng** → subscription cũ chuyển `REPLACED`, subscription mới bắt đầu
  từ bây giờ, **không hoàn tiền phần còn lại** của gói cũ (phải ghi rõ ở màn xác nhận mua).
- Voucher đã phát từ gói cũ **vẫn dùng được** đến khi hết hạn của chính nó.
- Đổi hạng → chạy lại bước set/clear `featured_until` của §8.10 theo quyền lợi của hạng mới.

> Màn M-02 **bắt buộc** hiện cảnh báo trước khi thanh toán:
> *"Gói hiện tại của bạn sẽ dừng ngay khi gói mới có hiệu lực và không được hoàn phần còn lại."*

---

## 3. QUY ƯỚC KỸ THUẬT CHUNG

Áp dụng đúng theo codebase hiện tại:

| Mục | Quy ước |
|---|---|
| Vỏ response | `ApiResponse<T>`: `{ result, isSuccess, statusCode, code?, message }` |
| Lỗi có mã | Ném `AppException(ErrorCode.XXX)`; FE branch theo `code` |
| Phân trang | `PageResponse<T>` (dùng `PageResponse.from(Page)`) |
| Auth | JWT Bearer. Member: `@PreAuthorize("hasRole('MEMBER')")`. Admin: `hasRole('ADMIN')` |
| Danh tính caller | Lấy từ `Authentication` / `@AuthenticationPrincipal Account`, **không tin field trong body** |
| Múi giờ | Mọi mốc "ngày"/"tháng" tính theo `Asia/Ho_Chi_Minh` (xem `LiveTryOnQuotaGuard.VN_ZONE`) |
| Tiền | `BigDecimal` precision 19 scale 2, đơn vị **đồng** |
| Tỷ lệ % | `BigDecimal` precision 10 scale 6, ví dụ 5% = `0.050000` |
| Layer | Controller → Service (interface) → ServiceImpl → Repository |
| Prefix Admin | `/api/admin/...` (theo `SystemConfigAdminController`) |

---

## 4. ENTITY MỚI

### 4.1. `MembershipPlan` — bảng `membership_plans`

Cấu hình của từng hạng. Admin sửa. **Chỉ có đúng 4 dòng** (FREE/SILVER/GOLD/DIAMOND),
seed bằng một `CommandLineRunner` giống `SystemConfigInitializer`.

| Cột | Kiểu | Null | Ghi chú |
|---|---|---|---|
| `id` | UUID | ✗ | PK |
| `tier` | varchar (enum `MembershipTier`) | ✗ | **UNIQUE**. FREE/SILVER/GOLD/DIAMOND |
| `name` | varchar(255) | ✗ | Tên hiển thị: "Thành viên Vàng" |
| `description` | text | ✓ | Mô tả ngắn dưới tên gói |
| `price_per_month` | numeric(19,2) | ✗ | FREE = 0 |
| `price_per_year` | numeric(19,2) | ✓ | null = không bán gói năm |
| `live_tryon_per_day` | int | ✓ | **Hạn mức chính** của thử đồ real-time. null = không giới hạn |
| `live_tryon_per_month` | int | ✓ | Trần phụ. **Seed = `null`** (không chặn) |
| `live_tryon_max_seconds` | int | ✗ | Thời lượng tối đa mỗi phiên real-time |
| `ai_tryon_per_day` | int | ✓ | **Hạn mức chính** của phối đồ AI. null = không giới hạn |
| `ai_tryon_per_month` | int | ✓ | Trần phụ. **Seed = `null`** (không chặn) |
| `featured_listing` | boolean | ✗ | `true` = toàn bộ hàng ký gửi của Member được "Nổi bật" |
| `max_pending_consignment_forms` | int | ✗ | Số form ký gửi chờ duyệt tối đa |
| `purchasable` | boolean | ✗ | FREE = false, còn lại = true |
| `active` | boolean | ✗ | false = ngừng bán mới, người đang dùng vẫn giữ tới hết hạn |
| `sort_order` | int | ✗ | Thứ tự hiển thị trên app |
| `created_at` | timestamptz | ✗ | |
| `updated_at` | timestamptz | ✗ | |

### 4.2. `MemberSubscription` — bảng `member_subscriptions`

Một dòng = một lần Member sở hữu gói. **Snapshot toàn bộ hạn mức** tại lúc mua, để Admin hạ
hạn mức sau này không ảnh hưởng người đã trả tiền.

| Cột | Kiểu | Null | Ghi chú |
|---|---|---|---|
| `id` | UUID | ✗ | PK |
| `member_id` | UUID FK → accounts | ✗ | INDEX `(member_id, status)` |
| `plan_id` | UUID FK → membership_plans | ✗ | |
| `tier` | varchar (enum) | ✗ | Snapshot, để query nhanh không join |
| `billing_cycle` | varchar (enum `BillingCycle`) | ✗ | MONTHLY / YEARLY |
| `price_paid` | numeric(19,2) | ✗ | Giá thật đã trả, **không** đọc lại từ plan |
| `live_tryon_per_day` | int | ✓ | ↓ 7 cột dưới đây đều là SNAPSHOT từ plan lúc mua |
| `live_tryon_per_month` | int | ✓ | |
| `live_tryon_max_seconds` | int | ✗ | |
| `ai_tryon_per_day` | int | ✓ | |
| `ai_tryon_per_month` | int | ✓ | |
| `featured_listing` | boolean | ✗ | |
| `max_pending_consignment_forms` | int | ✗ | |
| `status` | varchar (enum `SubscriptionStatus`) | ✗ | PENDING_PAYMENT / ACTIVE / EXPIRED / REPLACED / CANCELLED |
| `started_at` | timestamptz | ✓ | null khi còn PENDING_PAYMENT |
| `expires_at` | timestamptz | ✓ | null khi còn PENDING_PAYMENT |
| `last_voucher_issued_at` | timestamptz | ✓ | Mốc để job biết đã phát voucher tháng nào |
| `cancelled_at` | timestamptz | ✓ | |
| `cancel_reason` | varchar(255) | ✓ | Admin hủy thủ công thì ghi lý do |
| `granted_by_admin_id` | UUID FK → accounts | ✓ | Khác null = Admin tặng tay, không phải Member mua |
| `created_at` / `updated_at` | timestamptz | ✗ | |

> **Ràng buộc nghiệp vụ**: mỗi `member_id` chỉ được có tối đa 1 dòng `status = ACTIVE`.
> DB không enforce bằng unique thường được (vì có nhiều dòng EXPIRED), nên **service phải
> khóa dòng khi tạo**. Dùng `findActiveByMemberIdForUpdate` với `PESSIMISTIC_WRITE`,
> giống cách `OrderServiceImpl` khóa ví bằng `findByMemberIdForUpdate`.

### 4.3. `MembershipPayment` — bảng `membership_payments`

**Không dùng lại bảng `payments`** vì `payments.order_id` là `NOT NULL` và trỏ vào `orders`.

| Cột | Kiểu | Null | Ghi chú |
|---|---|---|---|
| `id` | UUID | ✗ | PK |
| `subscription_id` | UUID FK → member_subscriptions | ✗ | |
| `member_id` | UUID FK → accounts | ✗ | |
| `amount` | numeric(19,2) | ✗ | |
| `method` | varchar (enum `OrderPaymentMethod`) | ✗ | Dùng lại enum sẵn có: WALLET / MOMO / VNPAY |
| `momo_order_id` | varchar(255) | ✓ | |
| `momo_request_id` | varchar(255) | ✓ | |
| `momo_transaction_id` | varchar(255) | ✓ | |
| `vnp_txn_ref` | varchar(255) | ✓ | |
| `vnp_transaction_no` | varchar(255) | ✓ | |
| `status` | varchar (enum `PaymentStatus`) | ✗ | Dùng lại enum sẵn có |
| `paid_at` | timestamptz | ✓ | |
| `created_at` | timestamptz | ✗ | |

> ⚠️ **Không đặt UNIQUE** trên `momo_order_id` / `vnp_txn_ref`. Đây là bài học đã có trong repo:
> xem `scripts/drop-payment-gateway-ref-unique.sql` và ghi chú ở `CLAUDE.md` §Order payment.

### 4.4. `VoucherTemplate` — bảng `voucher_templates`

Luật chung của một loại voucher. Admin tạo/sửa.

| Cột | Kiểu | Null | Ghi chú |
|---|---|---|---|
| `id` | UUID | ✗ | PK |
| `name` | varchar(255) | ✗ | "Miễn phí ship 15K" |
| `description` | text | ✓ | Hiển thị trong ví voucher của Member |
| `type` | varchar (enum `VoucherType`) | ✗ | `SHIPPING` / `ORDER_DISCOUNT` |
| `discount_kind` | varchar (enum `VoucherDiscountKind`) | ✗ | `FIXED` / `PERCENT` |
| `discount_value` | numeric(19,2) | ✗ | FIXED: số tiền đồng. PERCENT: tỷ lệ, 5% = `0.05` |
| `max_discount_amount` | numeric(19,2) | ✓ | Bắt buộc có khi `discount_kind = PERCENT` |
| `min_order_amount` | numeric(19,2) | ✗ | Mặc định 0 |
| `valid_days` | int | ✗ | Số ngày sống kể từ lúc phát |
| `active` | boolean | ✗ | false = ngừng phát mã mới, mã đã phát vẫn dùng |
| `created_at` / `updated_at` | timestamptz | ✗ | |

### 4.5. `VoucherTemplateTierRule` — bảng `voucher_template_tier_rules`

Hạng nào được nhận mẫu nào, mỗi tháng mấy mã.

| Cột | Kiểu | Null | Ghi chú |
|---|---|---|---|
| `id` | UUID | ✗ | PK |
| `voucher_template_id` | UUID FK | ✗ | UNIQUE cùng `tier` |
| `tier` | varchar (enum) | ✗ | SILVER / GOLD / DIAMOND (không có FREE) |
| `quantity_per_month` | int | ✗ | ≥ 1 |

### 4.6. `MemberVoucher` — bảng `member_vouchers`

Một mã cụ thể trong ví của một Member. **Snapshot luật** từ template lúc phát.

| Cột | Kiểu | Null | Ghi chú |
|---|---|---|---|
| `id` | UUID | ✗ | PK |
| `code` | varchar(32) | ✗ | **UNIQUE**. Sinh ngẫu nhiên, vd `SHIP-7K2M9A` |
| `member_id` | UUID FK → accounts | ✗ | INDEX `(member_id, status, expires_at)` |
| `voucher_template_id` | UUID FK | ✗ | Để thống kê theo mẫu |
| `source_subscription_id` | UUID FK | ✓ | Subscription nào phát ra mã này |
| `type` | varchar (enum) | ✗ | ↓ 5 cột là SNAPSHOT từ template |
| `discount_kind` | varchar (enum) | ✗ | |
| `discount_value` | numeric(19,2) | ✗ | |
| `max_discount_amount` | numeric(19,2) | ✓ | |
| `min_order_amount` | numeric(19,2) | ✗ | |
| `status` | varchar (enum `MemberVoucherStatus`) | ✗ | `UNUSED` / `USED` / `EXPIRED` |
| `issued_at` | timestamptz | ✗ | |
| `expires_at` | timestamptz | ✗ | `issued_at + valid_days` |
| `used_at` | timestamptz | ✓ | |

> **Cố ý KHÔNG có `used_order_id`.** Một mã có thể trải trên nhiều đơn của cùng một lần thanh
> toán (§12.6), nên quan hệ ngược nằm ở `orders.voucher_id`. Muốn biết mã đã dùng cho những đơn
> nào thì query `orders WHERE voucher_id = ?`.

---

## 5. ENTITY CŨ CẦN SỬA

### 5.1. `Order` (`entity/marketplace/Order.java`)

Thêm 2 cột:

| Cột | Kiểu | Null | Ghi chú |
|---|---|---|---|
| `voucher_id` | UUID FK → member_vouchers | ✓ | Mã đã dùng. **KHÔNG đặt UNIQUE** — nhiều đơn của cùng một lần thanh toán chia nhau một mã, xem §12.6 |
| `discount_amount` | numeric(19,2) | ✗ | **DEFAULT 0**. Số tiền đã giảm |

**Đổi ý nghĩa `total_amount`**:
```
total_amount = subtotal + shipping_fee − discount_amount
```
> ⚠️ `ddl-auto: update` thêm cột mới với giá trị NULL cho dòng cũ. `discount_amount` là NOT NULL
> nên phải khai báo `@ColumnDefault("0")` **và** viết script SQL tay
> `scripts/backfill-order-discount-amount.sql` chạy `UPDATE orders SET discount_amount = 0
> WHERE discount_amount IS NULL;` trước khi đổi cột thành NOT NULL trên Postgres.

### 5.2. `Product` (`entity/marketplace/Product.java`)

Thêm 1 cột:

| Cột | Kiểu | Null | Ghi chú |
|---|---|---|---|
| `featured_until` | timestamptz | ✓ | null hoặc quá khứ = không nổi bật. **Hệ thống tự set/clear**, Member không bấm gì (§8.10) |

"Đang nổi bật" = `featured_until IS NOT NULL AND featured_until > now()`.
Không cần job dọn — chỉ cần so sánh lúc query.

### 5.3. Enum cần bổ sung giá trị

**`WalletTransactionType`** (`entity/enums/WalletTransactionType.java`) — thêm 3:
```
MEMBERSHIP_PURCHASE     // Member trả tiền mua gói (âm, bên MEMBER)
MEMBERSHIP_REVENUE      // Nền tảng thu tiền gói (dương, bên PLATFORM)
VOUCHER_DISCOUNT        // Chi phí khuyến mãi (âm, bên PLATFORM, chỉ để thống kê)
```

**`NotificationType`** (`entity/enums/NotificationType.java`) — thêm 2:
```
MEMBERSHIP_EVENT        // Mua thành công / sắp hết hạn / đã hết hạn
VOUCHER_ISSUED          // Đã nhận voucher mới trong tháng
```

### 5.4. Enum mới cần tạo (đặt trong `entity/enums/`)

```java
MembershipTier          { FREE, SILVER, GOLD, DIAMOND }
BillingCycle            { MONTHLY, YEARLY }
SubscriptionStatus      { PENDING_PAYMENT, ACTIVE, EXPIRED, REPLACED, CANCELLED }
VoucherType             { SHIPPING, ORDER_DISCOUNT }
VoucherDiscountKind     { FIXED, PERCENT }
MemberVoucherStatus     { UNUSED, USED, EXPIRED }
```

### 5.5. DTO cũ cần sửa

| DTO | Thay đổi |
|---|---|
| `CreateOrderRequest` | Thêm `String voucherCode` (nullable) |
| `OrderResponse` | Thêm `discountAmount`, `voucherCode`, `voucherName` |
| `ProductSummaryResponse` | Thêm `boolean featured` |
| `ProductDetailResponse` | Thêm `boolean featured` |
| `MyProductResponse` | Thêm `boolean featured`, `Instant featuredUntil` |
| `CartResponse` (item) | Thêm `boolean featured` (tùy chọn, để hiện nhãn trong giỏ) |
| `LiveTryOnQuotaResponse` | Xem §8.7 — đổi shape |
| DTO danh sách form ký gửi | Đã có sẵn `pendingCount` / `maxPending` (`ConsignmentFormServiceImpl` ~dòng 133). **Không đổi shape**, chỉ đổi nguồn của `maxPending` sang gói — xem §16 |

---

## 6. SYSTEM CONFIG MỚI

Thêm vào `constant/SystemConfigKeys.java` và `constant/SystemConfigCatalog.java`:

```java
// Số ngày trước khi gói hết hạn thì gửi thông báo nhắc gia hạn
public static final String MEMBERSHIP_EXPIRY_REMINDER_DAYS = "membership_expiry_reminder_days";
public static final int DEFAULT_MEMBERSHIP_EXPIRY_REMINDER_DAYS = 3;

// Trần an toàn: voucher ORDER_DISCOUNT không được giảm quá tỷ lệ này của hoa hồng nền tảng
// thu được trên đơn. 1.0 = giảm tối đa bằng đúng hoa hồng (nền tảng hòa vốn trên đơn đó)
public static final String VOUCHER_MAX_RATIO_OF_COMMISSION = "voucher_max_ratio_of_commission";
public static final BigDecimal DEFAULT_VOUCHER_MAX_RATIO_OF_COMMISSION = new BigDecimal("1.00");
```

> **Giá gói và hạn mức KHÔNG để trong `system_configs`** — chúng nằm ở bảng `membership_plans`
> vì mỗi hạng có một bộ giá trị riêng, không phải một tham số toàn cục.

---

## 7. ERROR CODE MỚI

Thêm vào `exception/ErrorCode.java`, theo đúng format `TÊN(HttpStatus.X, "thông điệp tiếng Việt")`:

| Code | HTTP | Thông điệp |
|---|---|---|
| `MEMBERSHIP_PLAN_NOT_FOUND` | 404 | Không tìm thấy gói thành viên. |
| `MEMBERSHIP_PLAN_NOT_PURCHASABLE` | 409 | Gói này hiện không bán. |
| `MEMBERSHIP_PLAN_YEARLY_NOT_AVAILABLE` | 400 | Gói này không bán theo năm. |
| `MEMBERSHIP_ALREADY_ACTIVE_SAME_TIER` | 409 | Bạn đang dùng gói này, hãy dùng chức năng gia hạn. |
| `MEMBERSHIP_PAYMENT_PENDING` | 409 | Bạn đang có một giao dịch mua gói chờ thanh toán. |
| `MEMBERSHIP_NOT_FOUND` | 404 | Bạn chưa có gói thành viên nào. |
| `MEMBERSHIP_SUBSCRIPTION_NOT_FOUND` | 404 | Không tìm thấy gói đăng ký. |
| `VOUCHER_NOT_FOUND` | 404 | Mã giảm giá không tồn tại. |
| `VOUCHER_FORBIDDEN` | 403 | Mã giảm giá này không thuộc về bạn. |
| `VOUCHER_ALREADY_USED` | 409 | Mã giảm giá đã được sử dụng. |
| `VOUCHER_EXPIRED` | 409 | Mã giảm giá đã hết hạn. |
| `VOUCHER_MIN_ORDER_NOT_MET` | 400 | Đơn hàng chưa đạt giá trị tối thiểu để dùng mã này. |
| `VOUCHER_NOT_APPLICABLE` | 400 | Mã giảm giá không áp dụng được cho đơn này. |
| `VOUCHER_TEMPLATE_NOT_FOUND` | 404 | Không tìm thấy mẫu voucher. |
| `VOUCHER_TEMPLATE_INVALID` | 400 | Cấu hình mẫu voucher không hợp lệ. |
| `TRYON_IMAGE_QUOTA_EXCEEDED` | 429 | Bạn đã dùng hết lượt phối đồ AI hôm nay. |

Code cũ `TRYON_LIVE_QUOTA_EXCEEDED` (429) giữ nguyên, chỉ đổi thông điệp cho phù hợp hạn mức mới.

---

## 8. API CHO MEMBER (MOBILE)

Tất cả đều `@PreAuthorize("hasRole('MEMBER')")` trừ khi ghi khác.

### 8.1. `GET /memberships/plans` — Danh sách gói

Auth: **public** (khách chưa đăng nhập cũng xem được để quyết định đăng ký).

Response `200`:
```json
{
  "result": [
    {
      "id": "uuid",
      "tier": "GOLD",
      "name": "Thành viên Vàng",
      "description": "Thử đồ thoải mái, voucher mỗi tháng",
      "pricePerMonth": 59000,
      "pricePerYear": 590000,
      "yearlySavingPercent": 16.6,
      "benefits": {
        "liveTryonPerDay": 10,
        "liveTryonPerMonth": null,
        "liveTryonMaxSeconds": 15,
        "aiTryonPerDay": 40,
        "aiTryonPerMonth": null,
        "featuredListing": true,
        "maxPendingConsignmentForms": 15
      },
      "vouchers": [
        { "name": "Miễn phí vận chuyển", "type": "SHIPPING", "quantityPerMonth": 4 },
        { "name": "Giảm giá đơn hàng", "type": "ORDER_DISCOUNT", "quantityPerMonth": 2 }
      ],
      "sortOrder": 2
    }
  ],
  "isSuccess": true, "statusCode": 200, "message": ""
}
```
- Chỉ trả plan có `active = true` **và** `purchasable = true` (không trả FREE).
- Sắp xếp theo `sortOrder` tăng dần.

### 8.2. `GET /memberships/me` — Gói hiện tại + hạn mức đã dùng

Response `200` — **luôn trả 200**, Member chưa mua gói thì trả tier `FREE`:
```json
{
  "result": {
    "subscriptionId": "uuid hoặc null",
    "tier": "GOLD",
    "tierName": "Thành viên Vàng",
    "status": "ACTIVE",
    "billingCycle": "MONTHLY",
    "startedAt": "2026-11-01T03:00:00Z",
    "expiresAt": "2026-12-01T03:00:00Z",
    "daysRemaining": 19,
    "quota": {
      "liveTryon":        { "limitPerDay": 10, "usedToday": 2, "remainingToday": 8,
                            "limitPerMonth": null, "usedThisMonth": 31, "remainingThisMonth": null,
                            "maxSeconds": 15 },
      "aiTryon":          { "limitPerDay": 40, "usedToday": 7, "remainingToday": 33,
                            "limitPerMonth": null, "usedThisMonth": 96, "remainingThisMonth": null },
      "featuredListing":  { "enabled": true, "productCount": 7 },
      "consignmentForms": { "limit": 15, "pending": 3, "remaining": 12 }
    },
    "voucherSummary": { "unused": 5, "expiringSoon": 1 }
  }
}
```
- `limit*` = `null` nghĩa là **không giới hạn** → mobile hiển thị "Không giới hạn", và
  `remaining*` tương ứng cũng là `null` (không hiện thanh progress).
- Mốc ngày: reset 00:00 giờ `Asia/Ho_Chi_Minh`. `usedThisMonth` chỉ để hiển thị tham khảo,
  không dùng để chặn khi `limitPerMonth = null`.
- `consignmentForms.pending` = số form đang chờ Moderator duyệt, lấy cùng nguồn với
  `pendingCount` của API danh sách form ký gửi.

### 8.3. `POST /memberships/subscribe` — Mua gói

Request:
```json
{ "planId": "uuid", "billingCycle": "MONTHLY", "paymentMethod": "WALLET", "redirectUrl": "rehome://membership/result" }
```
- `redirectUrl` chỉ bắt buộc khi `paymentMethod` là `MOMO` / `VNPAY`.

Response `201` khi `WALLET` (kích hoạt ngay):
```json
{ "result": { "subscriptionId": "uuid", "status": "ACTIVE", "tier": "GOLD",
              "expiresAt": "2026-12-01T03:00:00Z", "payUrl": null,
              "vouchersIssued": 2 },
  "statusCode": 201, "message": "Đăng ký gói thành công" }
```

Response `201` khi `MOMO` / `VNPAY` (chờ thanh toán):
```json
{ "result": { "subscriptionId": "uuid", "status": "PENDING_PAYMENT", "tier": "GOLD",
              "expiresAt": null, "payUrl": "https://...", "vouchersIssued": 0 },
  "statusCode": 201, "message": "Vui lòng hoàn tất thanh toán" }
```

Lỗi: `MEMBERSHIP_PLAN_NOT_FOUND` 404 · `MEMBERSHIP_PLAN_NOT_PURCHASABLE` 409 ·
`MEMBERSHIP_PLAN_YEARLY_NOT_AVAILABLE` 400 · `MEMBERSHIP_PAYMENT_PENDING` 409 ·
`INSUFFICIENT_BALANCE` 409 (ví không đủ — dùng lại code sẵn có của ví).

### 8.4. `GET /memberships/me/history` — Lịch sử mua gói

Query: `page`, `size`. Response: `PageResponse<MembershipPurchaseResponse>`
```json
{ "id": "uuid", "tier": "GOLD", "tierName": "Thành viên Vàng",
  "billingCycle": "MONTHLY", "amount": 59000, "method": "MOMO",
  "status": "SUCCESS", "paidAt": "...", "startedAt": "...", "expiresAt": "..." }
```

### 8.5. `GET /vouchers/me` — Ví voucher

Query: `status` (`UNUSED` | `USED` | `EXPIRED`, bỏ trống = tất cả), `page`, `size`.

Response: `PageResponse<MemberVoucherResponse>`
```json
{
  "id": "uuid",
  "code": "SHIP-7K2M9A",
  "name": "Miễn phí ship 15K",
  "description": "Giảm tối đa 15.000đ phí vận chuyển",
  "type": "SHIPPING",
  "discountKind": "FIXED",
  "discountValue": 15000,
  "maxDiscountAmount": null,
  "minOrderAmount": 0,
  "status": "UNUSED",
  "issuedAt": "2026-11-01T03:00:00Z",
  "expiresAt": "2026-12-01T03:00:00Z",
  "daysRemaining": 19,
  "usedOrderNos": []
}
```
Sắp xếp mặc định: `UNUSED` trước, trong đó `expires_at` gần nhất lên đầu.

### 8.6. `GET /vouchers/applicable` — Voucher dùng được cho đơn sắp đặt

Query bắt buộc: `productIds` (CSV), `shippingAddressId`.
Server tự tính `subtotal` và `shippingFee` rồi trả về **số tiền giảm thật** của từng mã.

Response `200`:
```json
{
  "result": {
    "subtotal": 500000,
    "shippingFee": 30000,
    "vouchers": [
      { "id": "uuid", "code": "SHIP-7K2M9A", "name": "Miễn phí ship 15K",
        "applicable": true,  "discountAmount": 15000, "reason": null },
      { "id": "uuid", "code": "SALE-3B8Q1Z", "name": "Giảm 20K đơn hàng",
        "applicable": true,  "discountAmount": 20000, "reason": null },
      { "id": "uuid", "code": "SALE-9X4L0P", "name": "Giảm 50K đơn hàng",
        "applicable": false, "discountAmount": 0,
        "reason": "Đơn hàng chưa đạt 1.000.000đ" }
    ]
  }
}
```
> Mobile **phải** gọi API này để hiện số tiền giảm, **không tự tính ở client**.

### 8.7. `GET /tryon/live/quota` — SỬA API CŨ

Shape cũ (chỉ có hạn mức ngày) được thay bằng:
```json
{
  "result": {
    "tier": "GOLD",
    "limitPerDay": 10, "usedToday": 2, "remainingToday": 8,
    "limitPerMonth": null, "usedThisMonth": 31, "remainingThisMonth": null,
    "maxSeconds": 15,
    "canStart": true,
    "upsellTier": "DIAMOND"
  }
}
```
- `canStart = false` khi hết lượt ngày **hoặc** hết lượt tháng (nếu có trần tháng).
- `upsellTier`: hạng thấp nhất có hạn mức cao hơn hạng hiện tại (`"DIAMOND"`), để mobile hiện
  nút "Nâng cấp để thử thêm". `null` nếu đã ở hạng cao nhất.

### 8.8. `GET /tryon/quota` — MỚI, hạn mức phối đồ AI (ảnh tĩnh)

```json
{ "result": { "tier": "GOLD",
              "limitPerDay": 40, "usedToday": 7, "remainingToday": 33,
              "limitPerMonth": null, "usedThisMonth": 96, "remainingThisMonth": null,
              "canGenerate": true, "upsellTier": "DIAMOND" } }
```
- Đếm số lượt **sinh ảnh** trong ngày (số row tạo ra ở bảng `try_on_images` của Member hôm nay),
  không đếm số ảnh đang còn trong bộ sưu tập — Member xóa ảnh **không** được hoàn lượt.

### 8.9. Hạn mức form ký gửi — KHÔNG có API mới

Hạn mức này đi qua API danh sách form ký gửi **đã có sẵn**, vốn đã trả `pendingCount` và
`maxPending`. BE chỉ đổi nguồn của `maxPending` từ `system_configs` sang gói của Member
(xem §16). **Mobile không phải đổi gì ở shape**, chỉ nên thêm gợi ý nâng cấp khi
`pendingCount >= maxPending` (lỗi `TOO_MANY_PENDING_FORMS` 409 đã có sẵn).

### 8.10. Sản phẩm nổi bật — KHÔNG có API, hệ thống tự làm

**Không có endpoint nào để bật/tắt, Member không phải thao tác gì.** Member có gói với
`featured_listing = true` thì **toàn bộ** hàng ký gửi của họ tự động nổi bật. Backend chỉ cần
set/clear cột `products.featured_until` ở **4 thời điểm**:

| Thời điểm | Hành động |
|---|---|
| Subscription chuyển `ACTIVE` (mua mới / gia hạn / Admin tặng) | Nếu plan có `featured_listing`: set `featured_until = subscription.expires_at` cho **mọi** product của Member đang `ON_SALE` hoặc `WAREHOUSE` |
| Product mới được tạo (Moderator nhập kho) hoặc ký gửi lại | Nếu Member đang có sub `ACTIVE` + `featured_listing`: set `featured_until = subscription.expires_at` ngay lúc tạo |
| Subscription chuyển `EXPIRED` / `CANCELLED` / `REPLACED` | Set `featured_until = null` cho mọi product của Member đó |
| Đổi sang hạng **không** có `featured_listing` | Như trên — set `null` |

> **Vì sao dùng cột `featured_until` thay vì join sang subscription lúc query?**
> `GET /products` là màn nóng nhất, dùng `Specification` + sort. Join sang `member_subscriptions`
> cho **mọi** dòng sẽ đắt. Một cột tính sẵn trên `products` cho phép sort trực tiếp. Đánh đổi:
> phải nhớ set/clear ở đúng 4 chỗ trên — xem §16.

> ⚠️ **Rủi ro cần theo dõi sau khi chạy thật**: nếu tỷ lệ Member mua gói cao, phần lớn hàng trên
> sàn sẽ cùng "nổi bật" và nhãn mất tác dụng phân biệt. Khi đó Admin tắt `featured_listing` của
> hạng Bạc ở W-01 để thu hẹp lại — không cần sửa code.

### 8.11. `POST /orders` — SỬA API CŨ

Thêm field vào `CreateOrderRequest`:
```json
{ "shippingAddressId": "uuid", "productIds": ["SKU-1"], "buyerNote": "...",
  "voucherCode": "SHIP-7K2M9A" }
```
- `voucherCode` nullable. Có thì server validate + tính giảm + đánh dấu mã `USED`.
- **Một lần thanh toán dùng tối đa một mã.**

**Giỏ nhiều món → nhiều đơn, nhưng vẫn chỉ một mã.** Hiện mỗi sản phẩm là một đơn riêng
(`OrderServiceImpl` ~dòng 240). Mã **không** áp vào một đơn cụ thể mà áp cho **cả lần thanh
toán**, rồi server **phân bổ** số tiền giảm về từng đơn theo §12.6. Mỗi đơn lưu `voucher_id` của
cùng một mã và phần `discount_amount` được chia cho mình.

> ⚠️ Nhiều đơn cùng trỏ vào một `voucher_id` → cột `orders.voucher_id` **KHÔNG được UNIQUE**.

Lỗi thêm: `VOUCHER_NOT_FOUND` 404 · `VOUCHER_FORBIDDEN` 403 · `VOUCHER_ALREADY_USED` 409 ·
`VOUCHER_EXPIRED` 409 · `VOUCHER_MIN_ORDER_NOT_MET` 400.

### 8.12. IPN cổng thanh toán — SỬA LUỒNG CŨ

`POST /api/momo/ipn-handler` và `GET /api/vnpay/ipn-handler` hiện chỉ biết đơn hàng.
Phải phân biệt được giao dịch nào là mua gói:

**Cách đề xuất**: đặt tiền tố vào mã giao dịch gửi sang cổng.
- Đơn hàng: `ORD-<uuid>` (giữ nguyên hành vi hiện tại)
- Mua gói: `SUB-<uuid>`

Handler đọc tiền tố → route sang `MembershipService.applyGatewayPaymentSuccess(...)`.

**Quy tắc giống đơn hàng**: cổng báo thành công **không** tự kích hoạt. Gọi
`applyGatewayPaymentSuccess(subscriptionId, method, note)`, chỉ kích hoạt khi subscription vẫn
đang `PENDING_PAYMENT`. Nếu đã bị hủy / đã kích hoạt bằng đường khác → **hoàn tiền vào ví Member**,
và dùng cờ `refundAmount != null` để không hoàn hai lần. (Xem `OrderService.applyGatewayPaymentSuccess`
làm mẫu — `CLAUDE.md` §Order payment.)

---

## 9. API CHO ADMIN (WEB)

Tất cả: `@PreAuthorize("hasRole('ADMIN')")`, prefix `/api/admin`, `@Tag(name = "Admin - Membership")`.
Mọi thao tác ghi đều phải ghi `AuditLog`.

### 9.1. Quản lý gói

| Method | Path | Mô tả |
|---|---|---|
| `GET` | `/api/admin/membership-plans` | Danh sách 4 gói kèm `activeSubscriberCount` mỗi gói |
| `GET` | `/api/admin/membership-plans/{id}` | Chi tiết 1 gói |
| `PUT` | `/api/admin/membership-plans/{id}` | Sửa giá + hạn mức + mô tả + `active` |

`PUT` request:
```json
{
  "name": "Thành viên Vàng",
  "description": "...",
  "pricePerMonth": 59000,
  "pricePerYear": 590000,
  "liveTryonPerDay": 10,
  "liveTryonPerMonth": null,
  "liveTryonMaxSeconds": 15,
  "aiTryonPerDay": 40,
  "aiTryonPerMonth": null,
  "featuredListing": true,
  "maxPendingConsignmentForms": 15,
  "active": true,
  "sortOrder": 2
}
```
Validate:
- `pricePerMonth` ≥ 0. `pricePerYear` `null` hoặc > 0.
- Mọi hạn mức: `null` (không giới hạn) hoặc ≥ 0.
- `liveTryonPerDay ≤ liveTryonPerMonth` và `aiTryonPerDay ≤ aiTryonPerMonth` khi cả hai khác `null`.
- `liveTryonMaxSeconds` ∈ [5, 120]. `featuredListing` là boolean.
- `maxPendingConsignmentForms` ∈ [1, 50] (khớp khoảng đang khai ở `SystemConfigCatalog` dòng 39).
- Dòng `FREE`: bỏ qua `pricePerMonth` / `pricePerYear` gửi lên, luôn ép 0 / `null`.
Audit: `actionType = "UPDATE_MEMBERSHIP_PLAN"`, `targetType = "MEMBERSHIP_PLAN"`, `detail` = JSON before/after.

> **Không có DELETE.** Subscription cũ vẫn trỏ vào plan. Ngừng bán = `active: false`.

**Quy tắc áp dụng khi Admin sửa** (đã chốt, xem §17.1 mục 15):
- **Đổi giá**: chỉ áp cho lần mua/gia hạn sau. Người đã mua giữ `price_paid` cũ.
- **Tăng hạn mức**: áp ngay cho mọi subscription ACTIVE → service phải **cập nhật snapshot**
  của các dòng ACTIVE nếu giá trị mới cao hơn.
- **Giảm hạn mức**: chỉ áp từ kỳ sau, **không** đụng vào snapshot của subscription ACTIVE.

### 9.2. Quản lý thành viên VIP

| Method | Path | Mô tả |
|---|---|---|
| `GET` | `/api/admin/membership-subscriptions` | Danh sách, lọc + phân trang |
| `GET` | `/api/admin/membership-subscriptions/{id}` | Chi tiết 1 subscription |
| `POST` | `/api/admin/membership-subscriptions/grant` | *(Giai đoạn 2)* Tặng/gia hạn tay |
| `POST` | `/api/admin/membership-subscriptions/{id}/cancel` | *(Giai đoạn 2)* Hủy kèm lý do |

`GET` query: `tier`, `status`, `q` (email hoặc tên Member), `expiringInDays`, `page`, `size`.

Item response:
```json
{ "id": "uuid", "memberId": "uuid", "memberEmail": "a@b.com", "memberName": "Nguyễn Văn A",
  "memberCode": "MB-000123", "tier": "GOLD", "tierName": "Thành viên Vàng",
  "status": "ACTIVE", "billingCycle": "MONTHLY", "pricePaid": 59000,
  "startedAt": "...", "expiresAt": "...", "daysRemaining": 19,
  "grantedByAdmin": false }
```

Chi tiết `{id}` trả thêm:
```json
{ "quotaUsage": { "liveTryonUsedToday": 2,  "liveTryonUsedThisMonth": 31,
                  "aiTryonUsedToday": 7,   "aiTryonUsedThisMonth": 96,
                  "featuredListing": true, "featuredProductCount": 7,
                  "pendingConsignmentForms": 3 },
  "payments": [ { "amount": 59000, "method": "MOMO", "status": "SUCCESS", "paidAt": "..." } ],
  "vouchers":  [ { "code": "SHIP-7K2M9A", "name": "...", "status": "USED",
                   "usedOrderNos": ["ORD-2026-000987"] } ] }
```

`POST /grant` request: `{ "memberId": "uuid", "tier": "GOLD", "months": 1, "reason": "Đền bù khiếu nại #123" }`
Audit: `GRANT_MEMBERSHIP`. `POST /{id}/cancel` request: `{ "reason": "..." }`, audit `CANCEL_MEMBERSHIP`.

### 9.3. Quản lý mẫu voucher

| Method | Path | Mô tả |
|---|---|---|
| `GET` | `/api/admin/voucher-templates` | Danh sách mẫu kèm rule theo hạng |
| `POST` | `/api/admin/voucher-templates` | Tạo mẫu |
| `PUT` | `/api/admin/voucher-templates/{id}` | Sửa mẫu + rule |
| `POST` | `/api/admin/voucher-templates/{id}/deactivate` | Ngừng phát mã mới |

`POST` / `PUT` request:
```json
{
  "name": "Giảm 20K đơn hàng",
  "description": "Áp dụng cho đơn từ 200.000đ",
  "type": "ORDER_DISCOUNT",
  "discountKind": "FIXED",
  "discountValue": 20000,
  "maxDiscountAmount": null,
  "minOrderAmount": 200000,
  "validDays": 30,
  "active": true,
  "tierRules": [
    { "tier": "GOLD", "quantityPerMonth": 1 },
    { "tier": "DIAMOND", "quantityPerMonth": 1 }
  ]
}
```
Validate (→ `VOUCHER_TEMPLATE_INVALID` 400):
- **`type = ORDER_DISCOUNT` thì BẮT BUỘC `discountKind = PERCENT`** — theo quyết định ở §2.2
  ghi chú 2. Loại `FIXED` chỉ dùng được cho `type = SHIPPING`.
- `discountKind = PERCENT` thì `discountValue` ∈ (0, 1] **và** `maxDiscountAmount` bắt buộc có.
- `discountKind = FIXED` thì `discountValue > 0`.
- `validDays` ∈ [1, 365]. `minOrderAmount ≥ 0`. `quantityPerMonth` ∈ [1, 10].
- `tierRules` không được chứa `FREE`, không trùng `tier`.

Audit: `UPDATE_VOUCHER_TEMPLATE`.

> **Không có DELETE.** `member_vouchers` trỏ FK vào template để thống kê.

### 9.4. Thống kê *(Giai đoạn 2)*

| Method | Path | Mô tả |
|---|---|---|
| `GET` | `/api/admin/membership-stats?from=&to=` | Doanh thu gói, số VIP theo hạng, mua mới / không gia hạn |
| `GET` | `/api/admin/voucher-stats?from=&to=` | Phát / dùng / hết hạn + tổng tiền đã chi |

Dùng lại `util/AdminDateRange.java` cho tham số `from`/`to` như các màn thống kê sẵn có.

### 9.5. Moderator — KHÔNG có màn hình mới

Moderator không liên quan tới gói VIP. Thay đổi duy nhất họ thấy: nhãn **"Nổi bật"** xuất hiện
trong danh sách tồn kho (`GET /inventory`) nếu FE muốn hiện. Không bắt buộc.

---

## 10. MÀN HÌNH MOBILE (APP MEMBER)

### M-01. Giới thiệu & chọn gói  *(mới)*
- **Vào từ**: Trang cá nhân → "Gói thành viên"; nút "Nâng cấp" ở M-05/M-06 khi hết lượt thử đồ.
- **API**: `GET /memberships/plans`, `GET /memberships/me`
- **Nội dung**:
  - Thẻ trạng thái trên cùng: hạng hiện tại + ngày hết hạn (hoặc "Bạn đang dùng gói miễn phí").
  - Toggle **Theo tháng / Theo năm** — chọn Năm thì hiện badge "Tiết kiệm 16%".
  - 3 thẻ gói ngang (SILVER / GOLD / DIAMOND), mỗi thẻ: tên, giá, danh sách quyền lợi
    từ `benefits` + `vouchers`, nút "Chọn gói".
  - Gói đang dùng: nút đổi thành "Gói hiện tại" (disabled) hoặc "Gia hạn".
- **Lưu ý**: render quyền lợi **từ JSON trả về**, không hard-code text, vì Admin sửa hạn mức được.
  `limit = null` → hiện "Không giới hạn".

### M-02. Xác nhận mua gói  *(mới)*
- **API**: `POST /memberships/subscribe`
- **Nội dung**: tên gói, chu kỳ, số tiền; chọn phương thức (Ví ReHome — hiện số dư khả dụng /
  MoMo / VNPay); checkbox đồng ý điều khoản.
- **Cảnh báo bắt buộc hiển thị** khi Member đang có gói khác hạng còn hạn:
  > "Gói hiện tại của bạn sẽ dừng ngay khi gói mới có hiệu lực và không được hoàn phần còn lại."
- **Sau khi gọi API**:
  - `status = ACTIVE` → sang M-03 với toast "Đăng ký thành công".
  - `status = PENDING_PAYMENT` → mở `payUrl` (WebView/browser), chờ callback về `redirectUrl`,
    rồi gọi lại `GET /memberships/me` để xác nhận. **Không tin kết quả hiển thị của cổng.**
- **Lỗi cần hiển thị riêng**: `INSUFFICIENT_BALANCE` → gợi ý đổi sang MoMo/VNPay.
  `MEMBERSHIP_PAYMENT_PENDING` → "Bạn đang có giao dịch chờ thanh toán."

### M-03. Gói của tôi  *(mới)*
- **API**: `GET /memberships/me`
- **Nội dung**:
  - Hạng + ngày hết hạn + số ngày còn lại (thanh tiến trình).
  - 4 ô hạn mức dạng `đã dùng / tổng` + thanh progress:
    **Thử đồ AI real-time (hôm nay)** · **Phối đồ AI (hôm nay)** · **Form ký gửi chờ duyệt**.
  - Thêm một dòng trạng thái (không phải ô hạn mức): *"Ưu tiên hiển thị: đang bật cho 7 món"*.
  - Dòng chú thích nhỏ dưới 2 ô thử đồ: "Làm mới lúc 00:00 mỗi ngày".
  - Ô nào có `limit = null` → hiện chữ "Không giới hạn", ẩn thanh progress.
  - Nút "Gia hạn" (→ M-02) và "Xem voucher của tôi" (→ M-04).
  - Link "Lịch sử mua gói" → `GET /memberships/me/history`.
- **Hiển thị khi FREE**: ẩn ngày hết hạn, nút chính là "Nâng cấp gói".

### M-04. Voucher của tôi  *(mới)*
- **API**: `GET /vouchers/me?status=`
- **Nội dung**: 3 tab **Chưa dùng / Đã dùng / Hết hạn**. Mỗi thẻ voucher: tên, mô tả, mã,
  điều kiện đơn tối thiểu, ngày hết hạn + badge "Còn 2 ngày" khi `daysRemaining ≤ 3`.
- Tab "Đã dùng": hiện danh sách `usedOrderNos` (một mã có thể trải trên nhiều đơn của cùng
  một lần thanh toán), bấm vào từng mã đơn để mở chi tiết đơn đó.
- Empty state khi FREE: "Nâng cấp gói để nhận voucher mỗi tháng" + nút sang M-01.

### M-05. Trang sản phẩm — thử đồ real-time  *(sửa màn cũ)*
- **API**: `GET /tryon/live/quota` (shape mới — xem §8.7)
- **Sửa**: chữ "Còn X/2 lượt hôm nay" → **"Còn X/{limitPerDay} lượt hôm nay"**.
  `limitPerDay = null` → hiện "Không giới hạn". Chỉ khi `limitPerMonth != null` mới hiện thêm
  "· Y/{limitPerMonth} tháng này" (seed hiện tại là `null` nên thường không hiện).
- **Khi `canStart = false`**: nút thử đồ disabled + dòng gợi ý
  "Hết lượt hôm nay. Nâng cấp **{upsellTier}** để thử thêm" → M-01. Ẩn gợi ý khi `upsellTier = null`.

### M-06. Phòng phối đồ AI (ảnh tĩnh)  *(sửa màn cũ)*
- **API**: `GET /tryon/quota` (mới)
- Hiện **"Còn X/{limitPerDay} lượt hôm nay"** trước khi bấm "Sinh ảnh".
- Lỗi `TRYON_IMAGE_QUOTA_EXCEEDED` 429 → dialog *"Bạn đã dùng hết lượt phối đồ hôm nay,
  làm mới lúc 00:00"* + nút "Nâng cấp {upsellTier}". Ẩn nút khi `upsellTier = null`.
- **Không** còn giới hạn số ảnh trong bộ sưu tập → giữ nguyên màn bộ sưu tập hiện tại.
- Lưu ý UX: xóa ảnh khỏi bộ sưu tập **không** hoàn lại lượt đã dùng — nên ghi rõ ở dialog xác
  nhận xóa, tránh Member hiểu nhầm.

### M-07. Giỏ hàng & Thanh toán  *(sửa màn cũ)*
- **API thêm**: `GET /vouchers/applicable?productIds=...&shippingAddressId=...`
- **Sửa**: thêm dòng **"Mã giảm giá"** trong phần tổng tiền, bấm vào mở bottom-sheet chọn mã.
  - Bottom-sheet chia 2 nhóm: **Dùng được** (hiện số tiền giảm thật) và **Chưa đủ điều kiện**
    (xám + `reason`).
  - Chọn xong → cập nhật khối tổng tiền của **cả lần thanh toán**:
    ```
    Tạm tính (2 món)   800.000đ
    Phí vận chuyển      40.000đ
    Giảm giá          − 50.000đ
    ───────────────────────────
    Tổng cộng          790.000đ
    ```
- **Một mã cho cả lần thanh toán**, kể cả khi giỏ nhiều món và server tách thành nhiều đơn
  (§12.6). Người mua **không** phải chọn áp mã cho món nào — giống Shopee.
- **Gọi `POST /orders` kèm `voucherCode`.** Nếu server trả lỗi voucher (409/400) → bỏ chọn mã,
  hiện lý do, **giữ nguyên các lựa chọn khác**, để Member đặt lại mà không mất giỏ.
- ⚠️ **Mobile không tự tính số tiền giảm.** Luôn lấy `discountAmount` từ server.
- Sau khi đặt, `POST /orders` trả **nhiều đơn**, mỗi đơn mang `discountAmount` **phần của mình**.
  Màn "Đặt hàng thành công" nên hiện tổng đã trả, không cộng tay lại từ giá gốc.

### M-08. Chi tiết đơn hàng  *(sửa màn cũ)*
- Hiện dòng "Giảm giá (SHIP-7K2M9A)  − 15.000đ" khi `discountAmount > 0`.
- Màn hủy đơn phải ghi rõ: **"Số tiền hoàn: 515.000đ"** (số đã trả thật, không phải giá gốc).

### M-09. Hàng ký gửi của tôi  *(sửa màn cũ)*
- **Không có API mới, không có nút bật/tắt** — xem §8.10.
- Thẻ sản phẩm có `featured = true` → badge **"Nổi bật"** góc ảnh.
- Member đang có gói: banner đầu danh sách
  *"Gói {tên gói} đang giúp {N} món của bạn hiển thị ưu tiên trên sàn, đến {ngày hết hạn}"*.
  Số liệu lấy từ `GET /memberships/me` → `quota.featuredListing`.
- Member đang FREE: banner *"Nâng cấp gói để hàng của bạn được ưu tiên hiển thị"* → M-01.

### M-10. Danh sách sản phẩm trên sàn  *(sửa màn cũ)*
- `ProductSummaryResponse.featured = true` → hiện badge **"Nổi bật"** góc trên ảnh.
- Màu badge trung tính (cam/vàng nhạt), **không** dùng chữ gợi ý chất lượng như "Chính hãng".
- Không cần đổi gì về phân trang — thứ tự do server quyết định.

### M-11. Thông báo  *(sửa màn cũ)*
Xử lý 2 `NotificationType` mới:
- `MEMBERSHIP_EVENT` → mở M-03.
- `VOUCHER_ISSUED` → mở M-04.

### M-12. Gửi form ký gửi  *(sửa màn cũ)*
- **API**: không đổi — dùng lại API danh sách/tạo form ký gửi sẵn có (§8.9).
- **Sửa**: dòng `Đã gửi 3/5 form` hiện đang lấy `maxPending` từ cấu hình chung, giờ con số này
  **thay đổi theo gói** (FREE 5 → DIAMOND 30). Mobile **không hard-code số 5**, luôn đọc
  `maxPending` từ response.
- Khi `pendingCount >= maxPending`: nút "Gửi form" disabled + dòng gợi ý
  *"Bạn đã đạt giới hạn {maxPending} form chờ duyệt. Nâng cấp gói để gửi nhiều hơn"* → M-01.
- Lỗi `TOO_MANY_PENDING_FORMS` 409 (đã có sẵn) → hiện cùng nội dung trên.

---

## 11. MÀN HÌNH WEB (KHU VỰC ADMIN)

### W-01. Cấu hình gói thành viên  *(mới)* — **BẮT BUỘC GIAI ĐOẠN 1**
- **Đường dẫn**: Admin → Cấu hình → Gói thành viên
- **API**: `GET /api/admin/membership-plans`, `PUT /api/admin/membership-plans/{id}`
- **Bảng** (4 dòng, không có nút Thêm/Xóa):

| Hạng | Tên | Giá/tháng | Giá/năm | RT /ngày | Giây /lượt | Phối đồ /ngày | Nổi bật | Form ký gửi | Đang bán | |
|---|---|---|---|---|---|---|---|---|---|---|
| FREE | Thành viên thường | 0 | — | 2 | 10 | 5 | ✗ | 5 | — | ✏️ |
| SILVER | Thành viên Bạc | 29.000 | 290.000 | 5 | 10 | 15 | ✓ | 10 | ✅ | ✏️ |
| GOLD | Thành viên Vàng | 59.000 | 590.000 | 10 | 15 | 40 | ✓ | 15 | ✅ | ✏️ |
| DIAMOND | Thành viên Kim cương | 99.000 | 990.000 | 20 | 20 | 60 | ✓ | 30 | ✅ | ✏️ |

> **Mọi ô trong bảng đều sửa được**, kể cả giá tháng và giá năm. Đây là yêu cầu bắt buộc —
> đừng render bất kỳ con số nào ở dạng chữ cố định.

- Cột phụ: **Đang dùng** (`activeSubscriberCount`) — click sang W-02 đã lọc sẵn hạng đó.
- Số voucher mỗi tháng **không nằm ở màn này** mà ở W-04 (vì gắn với mẫu voucher).
  Nên để một dòng link "Cấu hình voucher của các hạng →" dẫn sang W-04.
- **Form sửa** (modal): các field như §9.1. Ô hạn mức có checkbox "Không giới hạn" → gửi `null`.
- **Cảnh báo hiển thị trong form** khi giảm hạn mức:
  > "Giảm hạn mức chỉ áp dụng từ kỳ gia hạn tiếp theo. Thành viên đang dùng giữ nguyên mức cũ."
- Dòng FREE: ẩn ô giá và ô "Đang bán" (luôn `purchasable = false`).

### W-02. Danh sách thành viên VIP  *(mới)* — **BẮT BUỘC GIAI ĐOẠN 1**
- **API**: `GET /api/admin/membership-subscriptions`
- **Bộ lọc**: Hạng (dropdown) · Trạng thái · Ô tìm email/tên · "Sắp hết hạn trong N ngày".
- **Cột**: Member (tên + email + mã) · Hạng · Trạng thái (chip màu) · Chu kỳ · Giá đã trả ·
  Bắt đầu · Hết hạn · Còn lại (badge đỏ khi ≤ 3 ngày) · Nguồn (Mua / Admin tặng).
- Click dòng → W-03.
- Nút **Xuất Excel** *(tùy chọn)*.

### W-03. Chi tiết thành viên VIP  *(mới)*
- **API**: `GET /api/admin/membership-subscriptions/{id}`
- **3 khối**:
  1. **Thông tin gói**: hạng, trạng thái, chu kỳ, giá, bắt đầu/hết hạn, nguồn.
  2. **Hạn mức đã dùng**: thử đồ real-time (hôm nay / tháng này), phối đồ AI (hôm nay / tháng này),
     trạng thái ưu tiên hiển thị, form ký gửi đang chờ duyệt.
  3. **Lịch sử thanh toán** + **Voucher đã nhận** (mã, trạng thái, đơn đã dùng).
- **Nút (Giai đoạn 2)**: "Gia hạn thủ công" · "Hủy gói" (cả hai bắt buộc nhập lý do).

### W-04. Mẫu voucher  *(mới)* — **BẮT BUỘC nếu làm voucher ở giai đoạn 1**
- **API**: `GET/POST/PUT /api/admin/voucher-templates`
- **Bảng**: Tên · Loại (Ship / Giảm đơn) · Mức giảm (hiện "15.000đ" hoặc "5% tối đa 30.000đ") ·
  Đơn tối thiểu · Hiệu lực (ngày) · Phát cho hạng (chip: SILVER ×1, GOLD ×2, DIAMOND ×4) ·
  Đang bật · Thao tác.
- **Form tạo/sửa**: như §9.3. Chọn `PERCENT` thì bắt buộc nhập "Giảm tối đa".
- **Chọn loại "Giảm giá đơn hàng" thì ô "Kiểu giảm" bị khóa ở PERCENT** và hiện chú thích:
  *"Voucher giảm đơn hàng bắt buộc theo phần trăm; hệ thống tự chặn không cho vượt hoa hồng
  của kỳ ký gửi hiện tại."*
- **Seed khởi tạo** — 2 mẫu, khớp bảng §2.2:

| Mẫu | `type` | `discount_kind` | `discount_value` | `max_discount_amount` | `min_order_amount` | `valid_days` | Rule theo hạng |
|---|---|---|---|---|---|---|---|
| Miễn phí vận chuyển | `SHIPPING` | `FIXED` | 15.000 | — | 0 | 30 | SILVER ×2 · GOLD ×4 · DIAMOND ×6 |
| Giảm giá đơn hàng | `ORDER_DISCOUNT` | `PERCENT` | 0.10 (10%) | 50.000 | 0 | 30 | SILVER ×1 · GOLD ×2 · DIAMOND ×4 |

> Mẫu ship là **số tiền cố định**: giảm đúng 15.000đ, và nếu phí ship thật thấp hơn thì chỉ giảm
> bằng đúng phí ship (không hoàn phần thừa) — §12.1 bước B3.
> Mẫu giảm đơn là **phần trăm**, nhưng trần thật sự thường là hoa hồng của kỳ ký gửi chứ không
> phải `max_discount_amount`: món kỳ 1 chỉ cho hoa hồng 10%, nên 10% của giá hàng gần như luôn
> chạm trần. Đây là chủ ý, không phải lỗi.
- **Khối "Phát cho hạng nào"**: 3 dòng SILVER/GOLD/DIAMOND, mỗi dòng checkbox + ô số lượng/tháng.
- **Ghi chú hiển thị cạnh form**:
  > "Phần giảm giá do nền tảng chịu, không trừ vào tiền của người ký gửi. Hệ thống tự chặn
  > không cho voucher giảm quá hoa hồng thu được trên đơn."
- Không có nút Xóa, chỉ có "Ngừng phát".

### W-05. Thống kê gói & voucher  *(Giai đoạn 2)*
- **API**: `GET /api/admin/membership-stats`, `GET /api/admin/voucher-stats`
- **Nội dung**:
  - Biểu đồ cột: doanh thu bán gói theo tháng, chia màu theo hạng.
  - Thẻ số: VIP đang hoạt động / Mua mới tháng này / Không gia hạn tháng này.
  - Bảng voucher theo mẫu: Đã phát · Đã dùng · Hết hạn · Tỷ lệ dùng · **Tổng tiền đã chi**.
  - Thẻ cảnh báo: so "Doanh thu gói" với "Chi voucher + Chi phí AI ước tính" theo từng hạng.

### W-06. Cấu hình hệ thống  *(sửa màn cũ)*
Màn `GET /api/admin/config` sẵn có tự hiện 2 key mới (`membership_expiry_reminder_days`,
`voucher_max_ratio_of_commission`) nếu đã thêm vào `SystemConfigCatalog`. **Không cần sửa FE.**

---

## 12. QUY TẮC TÍNH VOUCHER (BACKEND BẮT BUỘC)

### 12.1. Thuật toán

```
INPUT:  voucher (MemberVoucher), MỘT order (subtotal, shippingFee, orderItem)
OUTPUT: discountAmount của riêng đơn đó (BigDecimal, scale 2)

        Lần thanh toán nhiều đơn: chạy B2-B4 cho TỪNG đơn rồi gộp theo §12.6

B1. KIỂM TRA ĐIỀU KIỆN  (sai bất kỳ bước nào → ném lỗi tương ứng)
    voucher.member_id == caller.id          sai → VOUCHER_FORBIDDEN      403
    voucher.status   == UNUSED              sai → VOUCHER_ALREADY_USED   409
    voucher.expires_at > now()              sai → VOUCHER_EXPIRED        409
    TỔNG subtotal của cả lần thanh toán >= voucher.min_order_amount
                                            sai → VOUCHER_MIN_ORDER_NOT_MET 400

B2. TÍNH MỨC GIẢM THÔ (raw) CHO ĐƠN NÀY
    base = (type == SHIPPING) ? order.shippingFee : order.subtotal
    raw  = (discount_kind == FIXED)
             ? voucher.discount_value                 // FIXED chỉ dùng cho SHIPPING, xem §9.3
             : base × voucher.discount_value          // PERCENT
    // max_discount_amount KHÔNG áp ở đây mà áp trên TỔNG - xem §12.6 bước C3

B3. ÁP TRẦN THEO LOẠI
    nếu type == SHIPPING:
        discount = min(raw, order.shippingFee)         // không giảm quá phí ship thật
    nếu type == ORDER_DISCOUNT:                        // luôn là PERCENT, xem §9.3
        // commission_rate_applied CHÍNH LÀ tỷ lệ của kỳ ký gửi hiện tại, đã được chốt
        // vào order_items lúc tạo đơn (kỳ 1 = 10%, kỳ 2 = 15%, kỳ 3 = 20%)
        commission = orderItem.price_at_order × orderItem.commission_rate_applied
        cap        = commission × config(voucher_max_ratio_of_commission)
        discount   = min(raw, cap, order.subtotal)     // nền tảng không lỗ trên đơn

B4. LÀM TRÒN
    discount = discount.setScale(2, RoundingMode.HALF_UP)
    nếu discount <= 0 → ném VOUCHER_NOT_APPLICABLE 400
```

### 12.2. Ví dụ kiểm thử

| # | Loại | Mức giảm | Giá hàng | Ship | Kỳ | Hoa hồng | **Kết quả** | Lý do |
|---|---|---|---|---|---|---|---|---|
| 1 | SHIPPING | FIXED 15.000 | 500.000 | 30.000 | 1 | 50.000 | **15.000** | Bình thường |
| 2 | SHIPPING | FIXED 15.000 | 500.000 | 12.000 | 1 | 50.000 | **12.000** | Chặn bởi phí ship thật |
| 3 | ORDER | 5%, max 30.000 | 400.000 | 30.000 | 1 | 40.000 | **20.000** | 5% × 400.000 |
| 4 | ORDER | 5%, max 30.000 | 1.000.000 | 30.000 | 1 | 100.000 | **30.000** | Chặn bởi `max_discount_amount` |
| 5 | ORDER | 20%, max 100.000 | 200.000 | 30.000 | **1** | 20.000 | **20.000** | Chặn bởi hoa hồng kỳ 1 (10%) |
| 6 | ORDER | 20%, max 100.000 | 200.000 | 30.000 | **3** | 40.000 | **40.000** | **Cùng mã, cùng món, kỳ 3 (20%) → giảm được nhiều hơn** |
| 7 | ORDER | 5%, max 30.000, min đơn 200.000 | 150.000 | 30.000 | 1 | 15.000 | **lỗi 400** | `VOUCHER_MIN_ORDER_NOT_MET` |

> Bảng này là các ca **một đơn một món** (tổng của lần thanh toán = chính đơn đó), nên
> `max_discount_amount` áp thẳng. Ca nhiều đơn xem §12.6.

> Ca **5 và 6** là ca quan trọng nhất phải có unit test: cùng một mã voucher áp lên cùng một món
> cho ra số tiền khác nhau tùy món đang ở kỳ ký gửi nào. Đây là hệ quả trực tiếp của luật
> "không vượt quá hoa hồng của kỳ hiện tại" ở §2.2 ghi chú 2.

### 12.3. Đánh dấu mã đã dùng — chống race

Phải nằm **trong cùng transaction** với việc tạo đơn, và **khóa dòng voucher**:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select v from MemberVoucher v where v.code = :code")
Optional<MemberVoucher> findByCodeForUpdate(@Param("code") String code);
```
Khóa xong **đọc lại `status`** trong lock rồi mới set `USED`. Không khóa thì hai request đặt hàng
song song cùng dùng một mã sẽ cùng qua được bước kiểm tra. Đây đúng là lỗi đã tránh ở
`ProductRepository.findByIdForUpdate` và `WalletRepository.findByMemberIdForUpdate`.

### 12.4. Trả mã khi đơn không thành

| Tình huống | Trả mã về `UNUSED`? | Lý do |
|---|---|---|
| Quá hạn thanh toán, đơn tự hủy | ✅ Có (nếu mã còn hạn) | Người mua không có lỗi |
| GHN lỗi / mất hàng, hệ thống hủy | ✅ Có (nếu mã còn hạn) | Người mua không có lỗi |
| Người mua tự hủy đơn `PENDING_PAYMENT` | ✅ Có (nếu mã còn hạn) | Chưa thu tiền |
| Người mua tự hủy đơn `PAID` | ❌ Không | Tránh đặt rồi hủy liên tục để xoay vòng mã |
| Trả hàng sau khi đã giao | ❌ Không | Tránh đặt–trả liên tục để tái dùng mã |

Khi trả mã: `status = UNUSED`, `used_at = null`; trên đơn bị hủy xóa `voucher_id` và set
`discount_amount = 0`.

**Với lần thanh toán nhiều đơn**: chỉ trả mã khi **TẤT CẢ** đơn mang `voucher_id` đó đều đã hủy.
Còn một đơn chưa hủy thì mã vẫn `USED`, và phần giảm của đơn bị hủy **không** được chuyển sang
đơn còn lại — người mua đã chốt số tiền của từng đơn lúc đặt.

Nếu mã **đã hết hạn** tại thời điểm trả → đặt `EXPIRED`, không trả về `UNUSED`.

### 12.5. Hoàn tiền

**Luôn hoàn `order.total_amount`** (số đã trả thật, đã trừ voucher), **không bao giờ** hoàn
`subtotal + shippingFee`. Hoàn sai → người mua lãi tiền mỗi lần đặt rồi hủy.

### 12.6. Một mã cho nhiều đơn trong cùng một lần thanh toán

**Bối cảnh.** Giỏ có 3 món → `POST /orders` tạo **3 đơn riêng**, nhưng người mua chỉ thấy **một**
màn thanh toán và trả **một** lần tiền (MoMo/VNPay cũng chỉ mở một giao dịch cho cả lô — xem
`CLAUDE.md` §Order payment). Voucher vì vậy phải áp cho **cả lần thanh toán**, không áp cho một
đơn lẻ, rồi mới chia ngược về từng đơn.

**Thuật toán:**

```
INPUT:  voucher, danh sách N đơn sắp tạo (mỗi đơn: subtotal_i, shippingFee_i, orderItem_i)
OUTPUT: discount_i cho từng đơn, với SUM(discount_i) = tổng giảm

C1. KIỂM TRA ĐIỀU KIỆN (§12.1 B1), trong đó min_order_amount so với SUM(subtotal_i)

C2. TÍNH PHẦN GIẢM THÔ CỦA TỪNG ĐƠN
    với mỗi đơn i: chạy §12.1 bước B2 + B3  ->  d_i
    (B3 đã chặn riêng từng đơn: không quá shippingFee_i, không quá hoa hồng của đơn i)
    total = SUM(d_i)

C3. ÁP max_discount_amount TRÊN TỔNG
    nếu max_discount_amount != null và total > max_discount_amount:
        hệ số k = max_discount_amount / total
        d_i = d_i × k   cho mọi i
        total = max_discount_amount

C4. LÀM TRÒN VÀ KHỬ LỆCH
    d_i = d_i.setScale(2, HALF_UP)
    chênh = total − SUM(d_i)
    cộng phần chênh (dương hoặc âm) vào đơn có subtotal LỚN NHẤT
    -> đảm bảo SUM(d_i) khớp đúng total, không lệch 1 xu

C5. GHI VÀO ĐƠN
    với mỗi đơn i:  voucher_id = voucher.id
                    discount_amount = d_i
                    total_amount = subtotal_i + shippingFee_i − d_i
    đánh dấu voucher USED MỘT LẦN (§12.3)

    nếu total <= 0 -> ném VOUCHER_NOT_APPLICABLE 400, không tạo đơn nào mang mã
```

**Ví dụ.** Mã giảm 10%, trần 50.000đ. Giỏ 2 món, cùng kho:

| Đơn | Giá hàng | Ship | Kỳ | Hoa hồng | d thô (C2) | Sau trần tổng (C3) | Tổng phải trả |
|---|---|---|---|---|---|---|---|
| A | 400.000 | 20.000 | 1 | 40.000 | 40.000 | 25.000 | 395.000 |
| B | 400.000 | 20.000 | 1 | 40.000 | 40.000 | 25.000 | 395.000 |
| **Cộng** | 800.000 | 40.000 | | 80.000 | **80.000** | **50.000** (trần) | **790.000** |

**Những điểm bắt buộc giữ đúng:**

1. **`price_at_order` của từng món không đổi.** Người ký gửi của đơn A vẫn nhận tiền tính trên
   400.000đ, dù người mua chỉ trả 395.000đ cho đơn đó.
2. **Hủy một đơn trong lô** → hoàn đúng `total_amount` của đơn đó (395.000đ), không phải 420.000đ.
   Các đơn còn lại giữ nguyên phần giảm của mình.
3. **Trả mã về `UNUSED`** chỉ khi toàn bộ đơn trong lô đều bị hủy — §12.4.
4. **Dòng sổ `VOUCHER_DISCOUNT`** (§13.3) ghi theo từng đơn khi đơn đó COMPLETED, số tiền là
   `discount_amount` của chính đơn đó, không phải tổng.

**Đây là phương án đã chốt, không có phương án thay thế.** Lý do chọn áp cho cả lần thanh toán
thay vì bắt người mua chọn áp mã cho từng món:

- Giỏ chỉ có **một** món — tình huống phổ biến nhất của hàng second-hand vì mỗi món là duy nhất —
  thì hai cách cho **kết quả giống hệt nhau**. Phần phân bổ C2–C4 chỉ chạy khi giỏ nhiều món.
- Bắt người mua chọn áp mã cho món nào là thao tác không ai quen, và gây thắc mắc vì **cùng một
  mã cho số tiền khác nhau** tùy món đang ở kỳ ký gửi nào (§12.2 ca 5 và 6).
- Hoàn tiền tự đúng: mỗi đơn đã mang sẵn phần giảm của mình, nên hủy đơn nào hoàn đúng đơn đó,
  không phải tính ngược từ giá gốc.

---

## 13. DÒNG TIỀN VÀ GHI SỔ

### 13.1. Mua gói bằng ví

Trong một transaction, khóa ví bằng `findByMemberIdForUpdate`, chỉ tiêu `balance − lockedBalance`:

| Bên | `wallet_type` | `type` | `amount` | Ghi chú |
|---|---|---|---|---|
| Member | `MEMBER` | `MEMBERSHIP_PURCHASE` | −59.000 | "Mua gói Thành viên Vàng 1 tháng" |
| Nền tảng | `PLATFORM` | `MEMBERSHIP_REVENUE` | +59.000 | Đối ứng, `counterpart_transaction_id` trỏ nhau |

### 13.2. Mua gói bằng MoMo / VNPay

Theo đúng convention hiện tại (thanh toán cổng **không** ghi `wallet_transactions` bên MEMBER):

| Bên | Ghi gì |
|---|---|
| Member | Không ghi — tiền đi từ ngoài vào, chỉ có dòng `membership_payments` |
| Nền tảng | 1 dòng `PLATFORM` / `MEMBERSHIP_REVENUE` / +59.000 |

### 13.3. Voucher — dòng chi phí khuyến mãi

Ghi tại thời điểm **đơn COMPLETED** (cùng lúc ghi `COMMISSION_REVENUE`), không ghi lúc đặt đơn
(đơn có thể bị hủy):

| Bên | `type` | `amount` | Ghi chú |
|---|---|---|---|
| Nền tảng | `VOUCHER_DISCOUNT` | −15.000 | "Khuyến mãi đơn ORD-2026-000987 (SHIP-7K2M9A)" |

> **Dòng này chỉ để thống kê, KHÔNG đổi số dư quỹ** — giống hệt cách `COMMISSION_REVENUE` đang làm
> (xem `ItemWithdrawalRequestServiceImpl` dòng ~580: *"...không đổi số dư quỹ, giống
> COMMISSION_REVENUE ở OrderServiceImpl. Đối soát quỹ bỏ qua loại dòng này"*).
> Phải thêm `VOUCHER_DISCOUNT` vào danh sách loại bị bỏ qua khi đối soát ở `AdminFundServiceImpl`.

### 13.4. Bảng đối chiếu một đơn có voucher

Món 500.000đ, hoa hồng kỳ 1 = 10%, ship 30.000đ, voucher ship 15.000đ:

| Khoản | Số tiền | Ghi ở đâu |
|---|---|---|
| Người mua trả | 515.000 | `orders.total_amount` |
| Phí ship trả GHN | 30.000 | `orders.shipping_fee` |
| Người ký gửi nhận | 450.000 | `ORDER_PAYOUT` (tính từ `price_at_order` × (1 − 10%)) |
| Hoa hồng ghi nhận | 50.000 | `COMMISSION_REVENUE` |
| Chi phí khuyến mãi | −15.000 | `VOUCHER_DISCOUNT` |
| **Nền tảng thực giữ** | **35.000** | |

**Người ký gửi nhận đúng 450.000đ dù có voucher hay không.** Đây là điểm kiểm thử quan trọng nhất
của toàn bộ tính năng.

---

## 14. JOB CHẠY NỀN

Dùng `@Scheduled` như `OrganizationLegalImageCleanupServiceImpl`. Giờ chạy theo `Asia/Ho_Chi_Minh`.

| Job | Lịch | Việc làm |
|---|---|---|
| `MembershipExpiryJob` | 00:05 hằng ngày | Subscription `ACTIVE` có `expires_at < now()` → `EXPIRED`; gửi `MEMBERSHIP_EVENT`. **Đồng thời set `featured_until = null` cho MỌI product của Member đó** (§8.10) |
| `MembershipExpiryReminderJob` | 09:00 hằng ngày | Subscription `ACTIVE` sắp hết hạn trong `membership_expiry_reminder_days` → gửi `MEMBERSHIP_EVENT` nhắc gia hạn. Chỉ gửi 1 lần/subscription |
| `VoucherIssueJob` | 00:15 hằng ngày | Với mỗi subscription `ACTIVE` có `last_voucher_issued_at` cách đây ≥ 1 tháng (hoặc null) → phát voucher theo `voucher_template_tier_rules` của hạng đó; cập nhật `last_voucher_issued_at`; gửi `VOUCHER_ISSUED` |
| `VoucherExpireJob` | 00:20 hằng ngày | `member_vouchers` `UNUSED` có `expires_at < now()` → `EXPIRED` |

> **`VoucherIssueJob` phải idempotent**: chạy lại hai lần trong ngày không được phát đôi.
> Dùng `last_voucher_issued_at` làm chốt, kiểm tra trong cùng transaction với lúc insert voucher.

---

## 15. THỨ TỰ TRIỂN KHAI ĐỀ XUẤT

### Giai đoạn 1 — Gói VIP + hạn mức thử đồ (không voucher)
Phần này độc lập, không đụng vào đặt hàng/thanh toán đơn/hoàn tiền, nên rủi ro thấp.

| # | Việc | Team |
|---|---|---|
| 1 | Entity `MembershipPlan`, `MemberSubscription`, `MembershipPayment` + enum + seed 4 plan | BE |
| 2 | `MembershipQuotaResolver` — trả plan hiệu lực của một account (subscription ACTIVE, else FREE) | BE |
| 3 | Sửa `LiveTryOnQuotaGuard` đọc hạn mức từ resolver thay vì `@Value` | BE |
| 4 | Thêm quota **theo ngày** cho phối đồ AI trong `TryOnServiceImpl` | BE |
| 5 | Sửa `ConsignmentFormServiceImpl.maxPendingForms()` đọc theo gói (3 chỗ gọi) | BE |
| 6 | API §8.1–8.4, 8.7, 8.8 | BE |
| 7 | API Admin §9.1, §9.2 (bỏ grant/cancel) | BE |
| 8 | Màn M-01, M-02, M-03; sửa M-05, M-06, M-12 | Mobile |
| 9 | Màn W-01, W-02, W-03 | Web |

### Giai đoạn 2 — Voucher
Phần này chạm vào đặt hàng, 3 luồng thanh toán, hủy đơn, hoàn tiền và sổ ví → **làm sau, test kỹ**.

| # | Việc | Team |
|---|---|---|
| 1 | Entity `VoucherTemplate`, `VoucherTemplateTierRule`, `MemberVoucher` | BE |
| 2 | Sửa `Order` (+2 cột) + script backfill | BE |
| 3 | `VoucherCalculator` theo §12.1 + unit test theo bảng §12.2 | BE |
| 4 | Tích hợp vào `OrderServiceImpl.createOrder` + 3 luồng thanh toán + hủy/hoàn | BE |
| 5 | `VoucherIssueJob`, `VoucherExpireJob` | BE |
| 6 | API §8.5, 8.6; sửa §8.11 | BE |
| 7 | API Admin §9.3 | BE |
| 8 | Màn M-04; sửa M-07, M-08 | Mobile |
| 9 | Màn W-04 | Web |

### Giai đoạn 3 — Ưu tiên hiển thị + thống kê

| # | Việc | Team |
|---|---|---|
| 1 | Thêm `Product.featured_until` + `MembershipPlan.featured_listing` | BE |
| 2 | Sửa sort ở `ProductServiceImpl.getProducts` (nhớ `.nullsLast()`) | BE |
| 3 | Set/clear `featured_until` ở 4 thời điểm của §8.10 | BE |
| 4 | API Admin §9.4 | BE |
| 5 | Màn M-09 (chỉ banner + badge, không có nút); badge ở M-10 | Mobile |
| 6 | Màn W-05 | Web |

> Giai đoạn 3 **không có endpoint mới nào cho Member** — toàn bộ là logic nền và hiển thị.

---

## 16. CHỖ CODE HIỆN TẠI BỊ ẢNH HƯỞNG

Danh sách để BE không bỏ sót. Đường dẫn tương đối từ `src/main/java/com/rehome/rehome/`.

| File | Thay đổi cần làm |
|---|---|
| `component/LiveTryOnQuotaGuard.java` | Bỏ `@Value("${tryon.live.daily-limit:2}")`, đọc hạn mức từ `MembershipQuotaResolver`. Thêm đếm theo tháng bên cạnh đếm theo ngày |
| `serviceImpl/LiveTryOnServiceImpl.java` | `getQuota` trả shape mới (§8.7). Giới hạn `maxSeconds` lấy theo gói thay vì config cứng |
| `serviceImpl/TryOnServiceImpl.java` | Thêm kiểm tra quota **phối đồ theo ngày** trước khi sinh ảnh (đếm số row `try_on_images` của Member tạo trong ngày VN). **Không** đụng tới giới hạn bộ sưu tập |
| `serviceImpl/ConsignmentFormServiceImpl.java` dòng **454–458** | `maxPendingForms()` đang đọc `system_configs.max_pending_forms_per_member`. Đổi thành `maxPendingForms(Account)` đọc từ gói của Member. **Có 3 chỗ gọi, sửa hết cả 3**: dòng **76** (chặn khi tạo form), dòng **134** (`maxPending` trong response danh sách — mobile hiển thị số này), dòng **277** (gửi form hàng loạt) |
| `constant/SystemConfigKeys.java` — `MAX_PENDING_FORMS_PER_MEMBER` | Sau khi đổi, key này **không còn tác dụng**. Giữ lại làm giá trị dự phòng khi không resolve được gói, hoặc gỡ khỏi `SystemConfigCatalog` để Admin khỏi sửa nhầm — **chọn một và ghi rõ trong code comment**, đừng để hai nguồn cùng tồn tại mà không ai biết cái nào thắng |
| `serviceImpl/OrderServiceImpl.java` ~dòng 245–290 | Tính `discountAmount`, set `total_amount = subtotal + shippingFee − discount`, gán `voucher_id`, đánh dấu mã `USED`. **Giữ nguyên `priceAtOrder`** |
| `serviceImpl/OrderServiceImpl.java` — `payOrders` | Ví trừ theo `total_amount` mới (đã gồm giảm giá) — kiểm tra lại logic trừ số dư |
| `serviceImpl/OrderServiceImpl.java` — `applyGatewayPaymentSuccess` | Số tiền đối soát với cổng là `total_amount` sau giảm |
| `serviceImpl/OrderServiceImpl.java` — hủy/hoàn tiền | Hoàn `total_amount`, trả mã về `UNUSED` theo bảng §12.4 |
| `serviceImpl/OrderServiceImpl.java` — hoàn thành đơn | Ghi thêm dòng `VOUCHER_DISCOUNT` khi `discount_amount > 0` |
| `controller/MomoController.java`, `VnPayController.java` | Thêm tiền tố `ORD-` / `SUB-` và route IPN theo tiền tố |
| `serviceImpl/OrderCancelExecutor.java` | Trả voucher về `UNUSED` khi hủy đơn chưa thanh toán |
| `serviceImpl/ProductServiceImpl.java` ~dòng 96 | Đổi sort: `Sort.by(Sort.Order.desc("featuredUntil").nullsLast(), Sort.Order.desc("createdAt"))`. ⚠️ **Postgres mặc định DESC đưa NULL lên đầu — bắt buộc `.nullsLast()`**, nếu không mọi sản phẩm không nổi bật sẽ nằm trên |
| `serviceImpl/AdminFundServiceImpl.java` | Thêm `VOUCHER_DISCOUNT` vào danh sách loại bỏ qua khi đối soát số dư quỹ |
| `serviceImpl/ProductServiceImpl.java` — `create` | Product mới của Member đang có gói `featured_listing` phải được set `featured_until = subscription.expires_at` ngay lúc nhập kho (§8.10 dòng 2) |
| `serviceImpl/ConsignmentReceiptServiceImpl.java` ~dòng 126–132 | Ký gửi lại cũng tạo vòng đời mới cho product — set `featured_until` ở đây như trên |
| `serviceImpl/EndOfTermLifecycleServiceImpl.java`, `OrderServiceImpl` (SOLD/RETURNED) | Không cần làm gì: product rời `ON_SALE` thì không còn lên danh sách sàn, `featured_until` thừa cũng vô hại |
| `constant/SystemConfigCatalog.java` | Đăng ký 2 key mới để màn W-06 tự hiện |
| `exception/ErrorCode.java` | Thêm các code ở §7 |
| `config/SecurityConfig.java` | `GET /memberships/plans` phải vào `PUBLIC_ENDPOINTS` |

**Script SQL chạy tay trên Postgres sau deploy** (`ddl-auto: update` không làm được):
- `scripts/backfill-order-discount-amount.sql` — set 0 cho dòng cũ rồi mới NOT NULL.
- Không cần script cho `products.featured_until` (nullable).

---

## 17. CÁC QUYẾT ĐỊNH ĐÃ CHỐT

Toàn bộ quyết định nghiệp vụ đã chốt. **Không còn mục nào chờ xác nhận** — team có thể code thẳng
từ file này.

| # | Nội dung | Kết quả | Mục |
|---|---|---|---|
| 1 | Giá 3 gói | 29.000 / 59.000 / 99.000 đ mỗi tháng | §2.2 |
| 2 | Gói theo năm | **Có bán**. Seed = 10 tháng (290k / 590k / 990k) | §2.2 |
| 3 | Mọi con số trong bảng quyền lợi | Là **dữ liệu**, Admin sửa được ở W-01 / W-04, **không hard-code** | §2.2 |
| 4 | Chu kỳ hạn mức thử đồ | Theo **NGÀY**, reset 00:00 giờ VN. Không có trần tháng (cột `*_per_month` seed `null`) | §2.2 |
| 5 | Số quyền lợi | **6**: phối đồ AI, thử đồ real-time, voucher giảm đơn, voucher ship, sản phẩm nổi bật, form ký gửi | §2.2 |
| 6 | Voucher miễn phí vận chuyển | **FIXED 15.000đ**. Phí ship thật thấp hơn thì chỉ giảm bằng phí ship, không hoàn phần thừa | §9.3, §12.1 |
| 7 | Voucher giảm giá đơn hàng | **PERCENT 10%, trần 50.000đ**, và không vượt hoa hồng của kỳ ký gửi hiện tại | §9.3, §12.1 |
| 8 | `voucher_max_ratio_of_commission` | **1.00** — sàn hòa vốn trên đơn đó. Admin sửa được | §6 |
| 9 | **Voucher khi giỏ nhiều món** | **Một mã cho cả lần thanh toán** (mô hình Shopee), server phân bổ về từng đơn theo tỷ lệ. Người mua không phải chọn áp cho món nào | §12.6 |
| 10 | Hủy đơn đã thanh toán | **Không** trả lại voucher. Hủy đơn chưa thanh toán / hệ thống hủy thì có trả | §12.4 |
| 11 | **Sản phẩm nổi bật — phạm vi** | **Toàn bộ** hàng ký gửi của Member, **không có slot**, Member không phải bật gì | §8.10 |
| 12 | **Sản phẩm nổi bật — hạng nào có** | **SILVER trở lên** (khác bảng gốc, nơi SILVER ghi "không"). Admin tắt lại được ở W-01 | §2.2 |
| 13 | Sản phẩm nổi bật — thời hạn | Theo hạn gói: bật khi sub `ACTIVE`, tắt khi `EXPIRED`/`CANCELLED`/`REPLACED` | §8.10 |
| 14 | Hạn mức form ký gửi | 5 / 10 / 15 / 30 form chờ duyệt. Đi qua API sẵn có, không thêm endpoint | §8.9 |
| 15 | Admin đổi giá / hạn mức | **Đổi giá**: chỉ áp cho lần mua và gia hạn sau, người đã mua giữ `price_paid` cũ. **Tăng** hạn mức: áp ngay cho sub `ACTIVE`. **Giảm** hạn mức: chỉ áp từ kỳ sau | §9.1 |
| 16 | **Đổi hạng giữa kỳ** | Gói cũ chuyển `REPLACED` và **dừng ngay, không hoàn phần còn lại**; gói mới tính đủ từ đầu. Cùng hạng thì cộng dồn vào `expiresAt`. M-02 bắt buộc cảnh báo trước khi trả tiền | §2.3 |
| 17 | Tổ chức (ORGANIZATION) mua gói | **Không**, chỉ MEMBER | §1.4 |
| 18 | Bộ sưu tập ảnh thử đồ | **Không** gắn vào gói, giữ nguyên hành vi hiện tại | §1.4 |
| 19 | `system_configs.max_pending_forms_per_member` | Giữ lại làm giá trị dự phòng khi không resolve được gói | §16 |

### Việc vận hành, không chặn code

**Lấy chi phí thật mỗi lượt gọi AI** (hóa đơn Decart cho real-time, chi phí chạy mô hình cho phối
đồ) rồi đối chiếu với cảnh báo cuối §2.2. Hạn mức theo ngày của DIAMOND mở trần tới 600 lượt
real-time + 1.800 lượt phối đồ mỗi tháng cho 99.000đ. Nếu chi phí thật vượt khoảng 40đ/lượt thì
hạ hạn mức hoặc tăng giá **trong app**, không phải sửa code.

### Những thứ cố tình KHÔNG làm

| Không làm | Lý do |
|---|---|
| Gói đổi tỷ lệ hoa hồng, phí rút hàng, kỳ ký gửi | Đã snapshot trong biên nhận — §1.3 |
| Voucher trừ vào tiền người ký gửi | `price_at_order` giữ nguyên, nền tảng chịu phần giảm — §13.4 |
| Pro-rata khi đổi hạng | Xem mục 16 |
| Auto-renew bằng thẻ lưu sẵn | §1.4 |
| API bật/tắt nổi bật cho từng sản phẩm | Xem mục 11 |
| Giới hạn số ảnh bộ sưu tập | Xem mục 18 |

---

## 18. GHI CHÚ CHO AI/DEV ĐỌC FILE NÀY ĐỂ SINH CODE

- Đọc `CLAUDE.md` ở thư mục gốc trước — nó mô tả kiến trúc, convention response/error,
  và những cái bẫy đã gặp (unique index cổng thanh toán, `ddl-auto: update` không drop cột...).
- Nghiệp vụ nền (hoa hồng theo kỳ, biên nhận ký gửi, ví và sổ kép) nằm ở `docs/OVERVIEW.md`.
- Mẫu code nên bắt chước:
  - Controller Member: `controller/ShippingAddressController.java`
  - Controller Admin: `controller/SystemConfigAdminController.java`
  - Service có khóa dòng + ghi sổ ví: `serviceImpl/OrderServiceImpl.java` (`payOrders`)
  - Guard hạn mức: `component/LiveTryOnQuotaGuard.java`
  - Unit test: `ShippingAddressServiceImplTest` (một test cho mỗi nhánh validate)
- **Không** tự ý sửa `price_at_order`, tỷ lệ hoa hồng, hay bất kỳ snapshot nào trong
  `consignment_receipts` — đó là hợp đồng, không phải cấu hình.
