# BRIEF THIẾT KẾ UI — GÓI THÀNH VIÊN VIP (ReHome)

Phiên bản 1.0 — 2026-11-13
Dùng để nhờ AI sinh thiết kế giao diện. **Không phải** tài liệu kỹ thuật — phần API, entity,
công thức tính nằm ở `VIP-MEMBERSHIP-SPEC.md`.

---

## 0. CÁCH DÙNG FILE NÀY

**Đừng dán cả file vào một lần.** Mỗi màn hình ở phần 4 và 5 là một khối độc lập, đã có đủ bối
cảnh để sinh riêng. Cách làm hiệu quả nhất:

1. Dán **Phần 1 + 2 + 3** (bối cảnh, design token, quy ước) vào tin nhắn đầu tiên.
2. Sau đó mỗi tin nhắn dán **một khối màn hình** và yêu cầu: *"Thiết kế màn hình này theo design
   token ở trên."*
3. Muốn nhiều màn cùng lúc thì gom tối đa 3 màn liên quan (ví dụ M-01 + M-02 + M-03).

**Câu lệnh gợi ý cho tin nhắn đầu:**

> Tôi đang làm app bán quần áo second-hand ReHome. Dưới đây là bối cảnh sản phẩm và design token.
> Hãy ghi nhớ, tôi sẽ gửi từng màn hình một để bạn thiết kế. Màn mobile dựng ở khung 390×844,
> màn web dựng ở 1440×900. Dùng dữ liệu mẫu tiếng Việt tôi cung cấp, đừng tự bịa số liệu khác.

---

## 1. BỐI CẢNH SẢN PHẨM

**ReHome** là nền tảng ký gửi và mua bán quần áo đã qua sử dụng, có kiểm định tại kho. Người dùng
vừa là **người mua**, vừa là **người ký gửi** (mang đồ cũ đến kho để nền tảng bán hộ).

Điểm khác biệt: có **thử đồ bằng AI** — chụp ảnh ghép đồ lên người mẫu (gọi là *phối đồ AI*), và
thử trực tiếp qua camera theo thời gian thực (*thử đồ real-time*).

**Tính năng đang thiết kế**: bán gói thành viên trả phí theo tháng hoặc năm, 3 hạng.

| Hạng | Giá/tháng | Định vị |
|---|---|---|
| **Bạc** | 29.000đ | Gói nhập môn, dùng thử đồ thoải mái hơn |
| **Vàng** | 59.000đ | Gói phổ thông, có voucher đều đặn |
| **Kim cương** | 99.000đ | Gói cao cấp, gần như không giới hạn |

Người không mua gói gọi là **Thành viên thường** (miễn phí, hạn mức thấp).

### Quyền lợi của gói

1. **Thử đồ AI real-time** — số lượt mỗi ngày và thời lượng mỗi lượt
2. **Phối đồ AI** — số lượt sinh ảnh mỗi ngày
3. **Voucher giảm giá đơn hàng** — số mã nhận mỗi tháng
4. **Voucher miễn phí vận chuyển** — số mã nhận mỗi tháng
5. **Sản phẩm nổi bật** — toàn bộ hàng ký gửi được xếp lên đầu sàn, có nhãn "Nổi bật"
6. **Form ký gửi** — số đơn xin ký gửi được gửi chờ duyệt cùng lúc

### Bảng quyền lợi đầy đủ (dùng làm dữ liệu thật khi vẽ)

| Quyền lợi | Thường | Bạc | Vàng | Kim cương |
|---|---|---|---|---|
| Giá / tháng | 0đ | 29.000đ | 59.000đ | 99.000đ |
| Giá / năm | — | 290.000đ | 590.000đ | 990.000đ |
| Thử đồ real-time (lượt/ngày) | 2 | 5 | 10 | 20 |
| Thời lượng mỗi lượt | 10 giây | 10 giây | 15 giây | 20 giây |
| Phối đồ AI (lượt/ngày) | 5 | 15 | 40 | 60 |
| Voucher giảm giá (mã/tháng) | 0 | 1 | 2 | 4 |
| Voucher free ship (mã/tháng) | 0 | 2 | 4 | 6 |
| Sản phẩm nổi bật | Không | Có | Có | Có |
| Form ký gửi tối đa | 5 | 10 | 15 | 30 |

---

## 2. DESIGN TOKEN

### 2.1. Màu

```
--brand          #2E7D5B   xanh lá đậm (màu chủ đạo ReHome, gợi tái sử dụng)
--brand-soft     #E8F3EE   nền nhạt của brand
--bg             #FFFFFF
--surface        #F7F8F7   nền thẻ, nền section
--border         #E3E6E4
--text           #1A1D1B
--text-muted     #6B726E
--success        #2E7D5B
--warning        #B8860B
--danger         #C0392B
--discount       #C0392B   màu số tiền được giảm
```

**Màu riêng của từng hạng** — dùng cho viền thẻ gói, chip hạng, badge:

```
Thường      #9AA0A6  xám trung tính
Bạc         #8E9BA8  xám ánh thép      — gradient #B8C2CC → #8E9BA8
Vàng        #C8921A  vàng đồng         — gradient #E8B339 → #C8921A
Kim cương   #3E8FB0  xanh ngọc         — gradient #6FC3DF → #3E8FB0
```

> Dùng gradient **nhẹ**, không bóng loáng kiểu game. Tông tổng thể là mộc mạc, thân thiện,
> hợp với đồ second-hand — **không** sang trọng kiểu hàng hiệu.

### 2.2. Chữ

- Font: **Be Vietnam Pro** (hỗ trợ dấu tiếng Việt đầy đủ). Thay thế: Inter, Plus Jakarta Sans.
- Mobile: tiêu đề màn 20/28 semibold · tiêu đề mục 16/24 semibold · nội dung 14/20 · phụ 12/16
- Web: tiêu đề trang 24/32 semibold · tiêu đề bảng 14/20 medium · nội dung 14/20 · phụ 12/16

### 2.3. Hình khối

- Bo góc: thẻ 16px · nút 12px · chip 999px · ô nhập 10px
- Đổ bóng: rất nhẹ `0 1px 3px rgba(0,0,0,.06)`. Không bóng đậm.
- Khoảng cách: bội số của 4. Lề ngang mobile 16px.
- Icon: nét mảnh (outline), độ dày 1.5px. Bộ Lucide hoặc tương đương.

### 2.4. Quy ước hiển thị

| Loại | Cách viết | Ví dụ |
|---|---|---|
| Tiền | Dấu chấm ngăn nghìn, "đ" liền sau | `29.000đ` · `1.250.000đ` |
| Tiền được giảm | Có dấu trừ, màu `--discount` | `− 50.000đ` |
| Ngày | dd/MM/yyyy | `13/12/2026` |
| Hạn mức | đã dùng / tổng | `2/10 lượt` |
| Không giới hạn | Chữ, không phải số | `Không giới hạn` |

### 2.5. Ba thứ tuyệt đối tránh

1. **Không** dùng tên hay logo của Shopee, Lazada, Tiki, hay bất kỳ thương hiệu có thật nào.
2. Nhãn "Nổi bật" **không được** gợi ý chất lượng — không dùng chữ "Chính hãng", "Uy tín",
   "Đã xác thực". Mọi món trên sàn đều đã qua kiểm định như nhau; nhãn này chỉ nói người bán có
   gói trả phí.
3. **Không** dùng hiệu ứng khan hiếm giả — không đếm ngược "còn 2 suất", không "chỉ hôm nay".

---

## 3. DANH SÁCH MÀN HÌNH

### Mobile (khung 390×844, iOS/Android)

| Mã | Màn hình | Mới / Sửa |
|---|---|---|
| M-01 | Giới thiệu & chọn gói | Mới |
| M-02 | Xác nhận mua gói | Mới |
| M-03 | Gói của tôi | Mới |
| M-04 | Voucher của tôi | Mới |
| M-05 | Thử đồ real-time — khối hạn mức | Sửa |
| M-06 | Phối đồ AI — khối hạn mức | Sửa |
| M-07 | Thanh toán + chọn voucher | Sửa |
| M-08 | Chi tiết đơn hàng — dòng giảm giá | Sửa |
| M-09 | Hàng ký gửi của tôi — banner nổi bật | Sửa |
| M-10 | Danh sách sản phẩm — nhãn Nổi bật | Sửa |
| M-11 | Gửi form ký gửi — giới hạn theo gói | Sửa |

### Web Admin (khung 1440×900)

| Mã | Màn hình | Mới / Sửa |
|---|---|---|
| W-01 | Cấu hình gói thành viên | Mới |
| W-02 | Danh sách thành viên VIP | Mới |
| W-03 | Chi tiết thành viên VIP | Mới |
| W-04 | Mẫu voucher | Mới |
| W-05 | Thống kê gói & voucher | Mới |

---

## 4. MÀN HÌNH MOBILE

---

### ■ M-01 — Giới thiệu & chọn gói

**Mục đích**: thuyết phục người dùng mua gói, cho so sánh 3 hạng và chọn một.

**Vào từ**: Trang cá nhân → "Gói thành viên"; hoặc nút "Nâng cấp" khi hết lượt thử đồ.

**Bố cục dọc, cuộn được:**

```
┌─────────────────────────────┐
│ ←   Gói thành viên          │  header
├─────────────────────────────┤
│ ┌─────────────────────────┐ │
│ │ Bạn đang dùng gói       │ │  thẻ trạng thái
│ │ Thành viên thường       │ │  nền --brand-soft
│ │ Nâng cấp để thử đồ      │ │
│ │ thoải mái hơn           │ │
│ └─────────────────────────┘ │
│                             │
│   ( Theo tháng | Theo năm ) │  toggle 2 lựa chọn
│         Tiết kiệm 17%       │  nhãn nhỏ cạnh "Theo năm"
│                             │
│ ┌───────┐┌───────┐┌───────┐ │  3 thẻ gói, cuộn ngang
│ │ Bạc   ││ Vàng  ││Kim c. │ │  thẻ giữa (Vàng) nổi hơn
│ │29.000đ││59.000đ││99.000đ│ │
│ │ /tháng││ /tháng││ /tháng│ │
│ │       ││PHỔ BIẾN│      │ │
│ │ ✓ ... ││ ✓ ... ││ ✓ ... │ │
│ │ ✓ ... ││ ✓ ... ││ ✓ ... │ │
│ │[Chọn] ││[Chọn] ││[Chọn] │ │
│ └───────┘└───────┘└───────┘ │
│                             │
│  So sánh chi tiết      ▾    │  bảng so sánh mở rộng
│                             │
│  Câu hỏi thường gặp    ▾    │
└─────────────────────────────┘
```

**Chi tiết thẻ gói:**
- Viền 1.5px màu của hạng, nền trắng. Thẻ "Vàng" có badge `PHỔ BIẾN` và viền dày hơn.
- Danh sách quyền lợi: 6 dòng, mỗi dòng icon check nhỏ màu hạng + chữ 14px.
- Nội dung quyền lợi cho từng hạng lấy đúng bảng ở §1.

**Bảng so sánh chi tiết** (phần mở rộng): bảng 4 cột — Quyền lợi / Bạc / Vàng / Kim cương, dùng
dấu `✓` `✗` cho dòng Sản phẩm nổi bật, số cho các dòng còn lại.

**Trạng thái cần vẽ:**
1. Chưa có gói (như hình trên)
2. Đang có gói Vàng — thẻ Vàng đổi nút thành `Gói hiện tại` (xám, không bấm được) và có nút
   `Gia hạn`; thẻ trạng thái trên cùng hiện `Thành viên Vàng · Còn 19 ngày`

**Lưu ý**: toàn bộ con số quyền lợi do server trả về, nên đừng vẽ chữ cứng kiểu "10 lượt" thành
hình ảnh — phải là text.

---

### ■ M-02 — Xác nhận mua gói

**Mục đích**: chốt gói, chọn cách trả tiền.

**Dạng**: màn hình đầy đủ (không phải bottom sheet, vì có cảnh báo quan trọng).

```
┌─────────────────────────────┐
│ ←   Xác nhận đăng ký        │
├─────────────────────────────┤
│ ┌─────────────────────────┐ │
│ │ 🏅 Thành viên Vàng      │ │  thẻ tóm tắt
│ │ 1 tháng                 │ │  viền màu Vàng
│ │             59.000đ     │ │
│ └─────────────────────────┘ │
│                             │
│ Bạn sẽ nhận được            │
│  • 10 lượt thử đồ/ngày      │
│  • 40 lượt phối đồ/ngày     │
│  • 2 voucher giảm giá/tháng │
│  • 4 voucher free ship/tháng│
│  • Hàng ký gửi được nổi bật │
│  • Gửi tối đa 15 form       │
│                             │
│ Chọn cách thanh toán        │
│ ┌─────────────────────────┐ │
│ │ ◉ Ví ReHome             │ │  radio
│ │   Số dư: 320.000đ       │ │
│ ├─────────────────────────┤ │
│ │ ○ MoMo                  │ │
│ ├─────────────────────────┤ │
│ │ ○ VNPay                 │ │
│ └─────────────────────────┘ │
│                             │
│ ⚠ Gói hiện tại của bạn sẽ  │  khối cảnh báo
│   dừng ngay khi gói mới có  │  nền vàng nhạt
│   hiệu lực và không được    │  chỉ hiện khi đang
│   hoàn phần còn lại.        │  có gói khác hạng
│                             │
│ ☐ Tôi đồng ý với điều khoản │
├─────────────────────────────┤
│ Tổng cộng         59.000đ   │  thanh cố định đáy
│ [     Thanh toán        ]   │
└─────────────────────────────┘
```

**Trạng thái cần vẽ:**
1. Mặc định (ví đủ tiền)
2. **Ví không đủ tiền** — dòng "Số dư: 12.000đ" màu đỏ, radio ví bị khóa, có chữ nhỏ
   *"Không đủ số dư, chọn MoMo hoặc VNPay"*
3. **Đang xử lý** — nút chuyển thành spinner + chữ "Đang xử lý..."
4. **Thành công** — màn kết quả: icon check tròn màu brand, "Đăng ký thành công",
   "Gói Thành viên Vàng có hiệu lực đến 13/12/2026", "Bạn đã nhận 6 voucher mới",
   2 nút: `Xem gói của tôi` (chính) và `Xem voucher` (phụ)
5. **Thất bại** — icon cảnh báo, "Thanh toán không thành công", nút `Thử lại`

---

### ■ M-03 — Gói của tôi

**Mục đích**: xem gói đang dùng và hạn mức còn lại.

```
┌─────────────────────────────┐
│ ←   Gói của tôi             │
├─────────────────────────────┤
│ ┌─────────────────────────┐ │
│ │  🏅 THÀNH VIÊN VÀNG     │ │  thẻ lớn, gradient
│ │  Hiệu lực đến 13/12/2026│ │  màu Vàng, chữ trắng
│ │  ▓▓▓▓▓▓▓▓▓▓░░░░ còn 19  │ │  thanh tiến trình
│ │                   ngày  │ │
│ └─────────────────────────┘ │
│                             │
│ Hạn mức hôm nay             │
│ ┌───────────┐ ┌───────────┐ │  lưới 2 cột
│ │ Thử đồ    │ │ Phối đồ   │ │
│ │ real-time │ │ AI        │ │
│ │  2 / 10   │ │  7 / 40   │ │
│ │ ▓▓░░░░░░  │ │ ▓▓▓░░░░░  │ │
│ └───────────┘ └───────────┘ │
│ ┌───────────┐ ┌───────────┐ │
│ │ Form ký   │ │ Thời lượng│ │
│ │ gửi       │ │ mỗi lượt  │ │
│ │  3 / 15   │ │  15 giây  │ │
│ │ ▓▓░░░░░░  │ │           │ │
│ └───────────┘ └───────────┘ │
│  ⟳ Làm mới lúc 00:00 mỗi    │
│    ngày                     │
│                             │
│ ┌─────────────────────────┐ │
│ │ ⭐ Ưu tiên hiển thị      │ │  dòng trạng thái
│ │ Đang bật cho 7 món       │ │  không phải ô hạn mức
│ └─────────────────────────┘ │
│                             │
│ 🎟 Voucher của tôi      5 › │  dòng điều hướng
│ 🧾 Lịch sử mua gói        › │
│                             │
│ [       Gia hạn gói       ] │
└─────────────────────────────┘
```

**Trạng thái cần vẽ:**
1. Gói Vàng đang hoạt động (như trên)
2. **Thành viên thường** — thẻ trên cùng màu xám, không có ngày hết hạn, chữ
   *"Bạn đang dùng gói miễn phí"*; các ô hạn mức hiện số của hạng thường (2/2, 5/5, 3/5);
   dòng ưu tiên hiển thị hiện *"Chưa bật — nâng cấp để hàng của bạn nổi bật"*;
   nút đáy đổi thành `Nâng cấp gói`
3. **Sắp hết hạn (≤ 3 ngày)** — thanh tiến trình đỏ, thêm dòng cảnh báo
   *"Gói sắp hết hạn, gia hạn để không mất quyền lợi"*
4. **Hạn mức không giới hạn** — ô hiện chữ `Không giới hạn`, ẩn thanh tiến trình

---

### ■ M-04 — Voucher của tôi

**Mục đích**: xem các mã đang có.

```
┌─────────────────────────────┐
│ ←   Voucher của tôi         │
├─────────────────────────────┤
│ ( Chưa dùng ) Đã dùng  Hết  │  3 tab
│                        hạn  │
├─────────────────────────────┤
│ ┌──┬──────────────────────┐ │  thẻ voucher
│ │🚚│ Miễn phí vận chuyển  │ │  cạnh trái răng cưa
│ │  │ Giảm tối đa 15.000đ  │ │  như vé xé
│ │  │ Đơn tối thiểu: không │ │
│ │  │ HSD 13/12/2026       │ │
│ │  │ SHIP-7K2M9A      [⧉] │ │  nút sao chép mã
│ └──┴──────────────────────┘ │
│ ┌──┬──────────────────────┐ │
│ │🏷│ Giảm giá đơn hàng    │ │
│ │  │ Giảm 10%, tối đa     │ │
│ │  │ 50.000đ              │ │
│ │  │ ⏰ Còn 2 ngày         │ │  badge đỏ khi ≤3 ngày
│ │  │ SALE-3B8Q1Z      [⧉] │ │
│ └──┴──────────────────────┘ │
│            ...              │
└─────────────────────────────┘
```

**Phân biệt 2 loại bằng màu dải trái**: free ship dùng màu `--brand`, giảm giá dùng `--discount`.

**Trạng thái cần vẽ:**
1. Tab "Chưa dùng" có 5 mã
2. Tab "Đã dùng" — thẻ xám hơn, thêm dòng *"Đã dùng cho đơn DH-2026-000987"*, bấm được
3. Tab "Hết hạn" — thẻ xám, có dấu mờ `HẾT HẠN` chéo góc
4. **Rỗng với người có gói** — minh họa + *"Voucher tháng này sẽ được phát vào ngày 01/12"*
5. **Rỗng với thành viên thường** — minh họa + *"Nâng cấp gói để nhận voucher mỗi tháng"* +
   nút `Xem các gói`

---

### ■ M-05 — Thử đồ real-time (sửa khối hạn mức)

Màn đã có sẵn, chỉ thiết kế lại **khối hiển thị hạn mức** và **trạng thái hết lượt**.

```
Trạng thái còn lượt:
┌─────────────────────────────┐
│ ⚡ Còn 8/10 lượt hôm nay     │  chip nhỏ, nền --brand-soft
└─────────────────────────────┘
       [  Bắt đầu thử đồ  ]      nút chính

Trạng thái hết lượt:
┌─────────────────────────────┐
│ Bạn đã dùng hết 10 lượt     │
│ hôm nay                     │
│ Làm mới lúc 00:00           │
│                             │
│ [ Nâng cấp Kim cương để     │  nút phụ, viền
│   thử 20 lượt/ngày      ]   │
└─────────────────────────────┘
       [  Bắt đầu thử đồ  ]      nút bị vô hiệu hóa
```

**Lưu ý**: nút nâng cấp chỉ hiện khi còn hạng cao hơn. Người đã ở Kim cương thì ẩn, chỉ còn
dòng "Làm mới lúc 00:00".

---

### ■ M-06 — Phối đồ AI (sửa khối hạn mức)

Giống M-05 nhưng đổi chữ:
- Chip: `🎨 Còn 33/40 lượt hôm nay`
- Hết lượt: *"Bạn đã dùng hết 40 lượt phối đồ hôm nay"*
- Thêm một dòng nhỏ ở dialog xác nhận xóa ảnh:
  *"Xóa ảnh không hoàn lại lượt đã dùng"* — màu `--text-muted`, 12px

---

### ■ M-07 — Thanh toán + chọn voucher

**Phần cần thiết kế**: dòng chọn mã trong khối tổng tiền, và bottom sheet chọn mã.

```
Khối tổng tiền (trong màn thanh toán):
┌─────────────────────────────┐
│ 🎟 Mã giảm giá              │
│    Miễn phí vận chuyển    › │  đã chọn: hiện tên mã
├─────────────────────────────┤
│ Tạm tính (2 món) 800.000đ   │
│ Phí vận chuyển    40.000đ   │
│ Giảm giá        − 50.000đ   │  màu --discount
│ ─────────────────────────   │
│ Tổng cộng       790.000đ    │  18px semibold
└─────────────────────────────┘

Bottom sheet chọn mã:
┌─────────────────────────────┐
│ ──                          │  tay cầm
│ Chọn mã giảm giá            │
├─────────────────────────────┤
│ DÙNG ĐƯỢC                   │  tiêu đề nhóm
│ ┌─────────────────────────┐ │
│ │ ○ Miễn phí vận chuyển   │ │
│ │   Giảm 40.000đ          │ │  số tiền giảm THẬT
│ │   HSD 13/12/2026        │ │
│ ├─────────────────────────┤ │
│ │ ◉ Giảm giá đơn hàng     │ │  đang chọn
│ │   Giảm 50.000đ          │ │
│ │   HSD 20/12/2026        │ │
│ └─────────────────────────┘ │
│                             │
│ CHƯA ĐỦ ĐIỀU KIỆN           │
│ ┌─────────────────────────┐ │
│ │ ○ Giảm giá đơn hàng     │ │  xám, không bấm được
│ │   Đơn chưa đạt 1.000.000đ│ │
│ └─────────────────────────┘ │
├─────────────────────────────┤
│ [        Áp dụng         ]  │
└─────────────────────────────┘
```

**Quy tắc quan trọng**: một mã dùng cho **cả lần thanh toán**, kể cả khi giỏ có nhiều món.
Người mua **không** phải chọn áp cho món nào. Chỉ chọn được **một** mã.

**Trạng thái cần vẽ:**
1. Chưa chọn mã — dòng hiện `Chọn mã giảm giá ›` màu `--text-muted`, không có dòng "Giảm giá"
2. Đã chọn (như trên)
3. Không có mã nào — bottom sheet hiện minh họa rỗng + *"Bạn chưa có mã nào. Nâng cấp gói để
   nhận voucher mỗi tháng"*
4. **Mã lỗi khi đặt hàng** — toast đỏ *"Mã đã được sử dụng"*, dòng mã tự bỏ chọn, tổng tiền
   tính lại, giỏ hàng giữ nguyên

---

### ■ M-08 — Chi tiết đơn hàng (sửa)

Chỉ thêm 1 dòng vào khối thanh toán của đơn:

```
Tạm tính          400.000đ
Phí vận chuyển     20.000đ
Giảm giá (SHIP-7K2M9A)
                 − 25.000đ    màu --discount
───────────────────────────
Đã thanh toán     395.000đ
```

**Màn hủy đơn**: phải ghi rõ số hoàn là số đã trả thật.

```
┌─────────────────────────────┐
│ Hủy đơn hàng                │
│                             │
│ Số tiền hoàn: 395.000đ      │  nổi bật
│ Hoàn vào Ví ReHome          │
│                             │
│ ℹ Mã giảm giá đã dùng cho   │  chữ nhỏ, muted
│   đơn này sẽ không được     │
│   hoàn lại.                 │
└─────────────────────────────┘
```

---

### ■ M-09 — Hàng ký gửi của tôi (sửa)

**Không có nút bật/tắt gì cả.** Chỉ thêm banner và nhãn.

```
┌─────────────────────────────┐
│ ←   Hàng ký gửi của tôi     │
├─────────────────────────────┤
│ ┌─────────────────────────┐ │
│ │ ⭐ Gói Thành viên Vàng   │ │  banner, nền --brand-soft
│ │ đang giúp 7 món của bạn │ │  viền trái 3px màu Vàng
│ │ hiển thị ưu tiên trên   │ │
│ │ sàn, đến 13/12/2026     │ │
│ └─────────────────────────┘ │
│                             │
│ ┌───┬─────────────────────┐ │
│ │[⭐]│ Áo khoác denim      │ │  nhãn Nổi bật
│ │ảnh│ Size M · Levi's     │ │  góc trên-trái ảnh
│ │   │ 450.000đ            │ │
│ │   │ ● Đang bán          │ │
│ └───┴─────────────────────┘ │
│            ...              │
└─────────────────────────────┘
```

**Nhãn "Nổi bật"**: chip nhỏ nền gradient màu hạng, chữ trắng 10px, icon ngôi sao, bo góc 6px,
đặt đè lên góc trên-trái ảnh sản phẩm, cách mép 6px.

**Trạng thái với thành viên thường**: banner đổi thành nền xám nhạt,
*"Nâng cấp gói để hàng của bạn được ưu tiên hiển thị"* + nút chữ `Xem gói ›`. Sản phẩm không có nhãn.

---

### ■ M-10 — Danh sách sản phẩm trên sàn (sửa)

Chỉ thêm nhãn "Nổi bật" lên thẻ sản phẩm, dùng đúng kiểu nhãn ở M-09.

Vẽ lưới 2 cột, 6 sản phẩm, trong đó 2 sản phẩm có nhãn và nằm ở đầu danh sách.

**Dữ liệu mẫu:**

| Tên | Giá | Size | Nổi bật |
|---|---|---|---|
| Áo khoác denim xanh | 450.000đ | M | ✓ |
| Váy hoa nhí vintage | 280.000đ | S | ✓ |
| Áo thun cotton trắng | 120.000đ | L | |
| Quần jeans ống suông | 350.000đ | 29 | |
| Áo sơ mi linen be | 195.000đ | M | |
| Chân váy xếp ly | 165.000đ | S | |

> Nhãn không được che mất giá hay tên. Nếu thẻ quá nhỏ thì thu nhãn còn mỗi icon ngôi sao.

---

### ■ M-11 — Gửi form ký gửi (sửa)

Màn đã có, chỉ sửa dòng đếm và trạng thái đầy.

```
Còn chỗ:
┌─────────────────────────────┐
│ Đã gửi 3/15 form chờ duyệt  │  chữ 13px, muted
└─────────────────────────────┘
       [   Gửi form mới   ]

Đã đầy:
┌─────────────────────────────┐
│ Đã gửi 15/15 form chờ duyệt │
│                             │
│ Bạn đã đạt giới hạn form    │
│ chờ duyệt. Chờ kho duyệt    │
│ bớt, hoặc nâng cấp gói để   │
│ gửi nhiều hơn.              │
│                             │
│ [ Xem các gói ]             │
└─────────────────────────────┘
       [   Gửi form mới   ]      vô hiệu hóa
```

**Lưu ý**: con số tổng (15) thay đổi theo gói — 5 với thành viên thường, 30 với Kim cương.
Đừng vẽ cứng số 5.

---

## 5. MÀN HÌNH WEB ADMIN

Khung 1440×900. Bố cục chung: sidebar trái 240px + vùng nội dung. Giả định sidebar đã có sẵn,
chỉ cần vẽ vùng nội dung và chỉ rõ mục đang chọn.

---

### ■ W-01 — Cấu hình gói thành viên

**Đường dẫn**: Cấu hình → Gói thành viên

```
┌────────────────────────────────────────────────────────────┐
│ Gói thành viên                                             │
│ Sửa giá và hạn mức của từng hạng. Thay đổi có hiệu lực     │
│ từ lần mua tiếp theo.                                      │
│                              [Cấu hình voucher của hạng →] │
├────────────────────────────────────────────────────────────┤
│ Hạng │Tên       │Giá/th │Giá/năm│RT │Giây│Phối│Nổi │Form│Bán│ │
│      │          │       │       │/ng│/lượt│/ng │bật │    │   │ │
├──────┼──────────┼───────┼───────┼───┼────┼────┼────┼────┼───┼─┤
│●Thường│Thành viên│     0 │   —   │ 2 │ 10 │  5 │ ✗  │  5 │ — │✏│
│●Bạc  │Thành v.Bạc│29.000│290.000│ 5 │ 10 │ 15 │ ✓  │ 10 │ ✓ │✏│
│●Vàng │Thành v.Vàng│59.000│590.000│10 │ 15 │ 40 │ ✓  │ 15 │ ✓ │✏│
│●K.cương│Thành v.KC│99.000│990.000│20 │ 20 │ 60 │ ✓  │ 30 │ ✓ │✏│
├────────────────────────────────────────────────────────────┤
│ Đang dùng:  Bạc 142 · Vàng 318 · Kim cương 57              │
└────────────────────────────────────────────────────────────┘
```

- Cột `Hạng` có chấm tròn màu của hạng.
- **Không có** nút Thêm hay Xóa — chỉ đúng 4 dòng cố định.
- Cột `Đang bán` là công tắc bật/tắt. Dòng Thường không có công tắc.

**Modal sửa gói** (640px):

```
┌──────────────────────────────────────┐
│ Sửa gói Thành viên Vàng          ✕  │
├──────────────────────────────────────┤
│ THÔNG TIN                            │
│ Tên hiển thị  [Thành viên Vàng     ] │
│ Mô tả         [                    ] │
│                                      │
│ GIÁ                                  │
│ Theo tháng    [59.000        ] đ      │
│ Theo năm      [590.000       ] đ      │
│               ☐ Không bán gói năm     │
│                                      │
│ HẠN MỨC THỬ ĐỒ                       │
│ Real-time/ngày[10    ] ☐ Không g.hạn  │
│ Giây mỗi lượt [15    ]                │
│ Phối đồ/ngày  [40    ] ☐ Không g.hạn  │
│                                      │
│ QUYỀN LỢI KHÁC                       │
│ Sản phẩm nổi bật      [công tắc ✓]   │
│ Form ký gửi tối đa    [15    ]        │
│                                      │
│ ⚠ Giảm hạn mức chỉ áp dụng từ kỳ    │  cảnh báo nền vàng
│   gia hạn tiếp theo. Thành viên      │  chỉ hiện khi người
│   đang dùng giữ nguyên mức cũ.       │  dùng giảm số
├──────────────────────────────────────┤
│                   [Hủy]  [Lưu thay đổi]│
└──────────────────────────────────────┘
```

---

### ■ W-02 — Danh sách thành viên VIP

```
┌────────────────────────────────────────────────────────────┐
│ Thành viên VIP                                             │
├────────────────────────────────────────────────────────────┤
│ [🔍 Tìm email hoặc tên] [Hạng ▾] [Trạng thái ▾]           │
│ [Sắp hết hạn trong 7 ngày ▾]              [⬇ Xuất Excel]  │
├────────────────────────────────────────────────────────────┤
│ Thành viên    │Hạng │Trạng thái│Chu kỳ│Đã trả │Hết hạn│Còn │
├───────────────┼─────┼──────────┼──────┼───────┼───────┼────┤
│ Nguyễn Văn An │●Vàng│ Hoạt động│ Tháng│59.000 │13/12  │19ng│
│ an@email.com  │     │          │      │       │/2026  │    │
│ MB-000123     │     │          │      │       │       │    │
├───────────────┼─────┼──────────┼──────┼───────┼───────┼────┤
│ Trần Thị Bình │●K.c │ Hoạt động│  Năm │990.000│02/09  │293 │
│ binh@email.com│     │          │      │       │/2027  │ ng │
├───────────────┼─────┼──────────┼──────┼───────┼───────┼────┤
│ Lê Minh Cường │●Bạc │ Hoạt động│ Tháng│29.000 │16/11  │ 3ng│
│ cuong@email..│     │          │      │       │/2026  │ 🔴 │
├───────────────┼─────┼──────────┼──────┼───────┼───────┼────┤
│ Phạm Thu Dung │●Vàng│ Hết hạn  │ Tháng│59.000 │01/11  │  — │
│               │     │   (xám)  │      │       │/2026  │    │
└────────────────────────────────────────────────────────────┘
          ‹ 1 2 3 ... 12 ›        Hiển thị 20/517
```

- Ô "Thành viên" gồm 3 dòng: tên, email, mã thành viên.
- Badge đỏ ở cột "Còn" khi ≤ 3 ngày.
- Trạng thái dạng chip: Hoạt động (xanh), Hết hạn (xám), Đã hủy (đỏ nhạt), Đã thay thế (xám nhạt).
- Click dòng → W-03.

---

### ■ W-03 — Chi tiết thành viên VIP

Bố cục 2 cột: trái 65% (thông tin + bảng), phải 35% (hạn mức).

```
┌────────────────────────────────────────────────────────────┐
│ ← Nguyễn Văn An                 [Gia hạn thủ công] [Hủy gói]│
├──────────────────────────────────┬─────────────────────────┤
│ THÔNG TIN GÓI                    │ HẠN MỨC ĐÃ DÙNG         │
│ Hạng        ● Thành viên Vàng    │                         │
│ Trạng thái  Hoạt động            │ Thử đồ real-time        │
│ Chu kỳ      Theo tháng           │  Hôm nay      2 / 10    │
│ Đã trả      59.000đ              │  Tháng này      31      │
│ Bắt đầu     13/11/2026           │                         │
│ Hết hạn     13/12/2026 (còn 19ng)│ Phối đồ AI              │
│ Nguồn       Tự mua               │  Hôm nay      7 / 40    │
│                                  │  Tháng này      96      │
│ LỊCH SỬ THANH TOÁN               │                         │
│ 13/11/2026  59.000đ  MoMo  ✓     │ Ưu tiên hiển thị        │
│ 13/10/2026  59.000đ  Ví    ✓     │  Đang bật · 7 món       │
│                                  │                         │
│ VOUCHER ĐÃ NHẬN                  │ Form ký gửi             │
│ SHIP-7K2M9A  Free ship  Đã dùng  │  Chờ duyệt    3 / 15    │
│              → DH-2026-000987    │                         │
│ SALE-3B8Q1Z  Giảm giá   Chưa dùng│                         │
│ SHIP-2M8K1A  Free ship  Hết hạn  │                         │
└──────────────────────────────────┴─────────────────────────┘
```

**Modal "Hủy gói"**: bắt buộc nhập lý do, có cảnh báo *"Thành viên sẽ mất toàn bộ quyền lợi ngay
lập tức. Voucher đã phát vẫn dùng được đến khi hết hạn."*

---

### ■ W-04 — Mẫu voucher

```
┌────────────────────────────────────────────────────────────┐
│ Mẫu voucher                              [+ Tạo mẫu mới]   │
│ Phần giảm giá do nền tảng chịu, không trừ vào tiền của     │
│ người ký gửi.                                              │
├────────────────────────────────────────────────────────────┤
│ Tên          │Loại    │Mức giảm      │Đơn tối│HSD │Phát cho│
│              │        │              │thiểu  │    │hạng    │
├──────────────┼────────┼──────────────┼───────┼────┼────────┤
│ Miễn phí vận │🚚 Ship │15.000đ       │   —   │30ng│Bạc ×2  │
│ chuyển       │        │              │       │    │Vàng ×4 │
│              │        │              │       │    │K.c ×6  │
├──────────────┼────────┼──────────────┼───────┼────┼────────┤
│ Giảm giá đơn │🏷 Giảm │10%, tối đa   │   —   │30ng│Bạc ×1  │
│ hàng         │  giá   │50.000đ       │       │    │Vàng ×2 │
│              │        │              │       │    │K.c ×4  │
└────────────────────────────────────────────────────────────┘
```

**Modal tạo/sửa mẫu** (640px):

```
┌──────────────────────────────────────┐
│ Sửa mẫu voucher                  ✕  │
├──────────────────────────────────────┤
│ Tên      [Giảm giá đơn hàng        ] │
│ Mô tả    [                         ] │
│                                      │
│ Loại     (○ Miễn phí ship           │
│           ◉ Giảm giá đơn hàng)       │
│                                      │
│ Kiểu giảm [Phần trăm ▾]  🔒          │  khóa khi chọn
│ ℹ Voucher giảm đơn hàng bắt buộc    │  "giảm giá đơn hàng"
│   theo phần trăm. Hệ thống tự chặn   │
│   không cho vượt hoa hồng của kỳ     │
│   ký gửi hiện tại.                   │
│                                      │
│ Mức giảm   [10      ] %              │
│ Giảm tối đa[50.000  ] đ              │
│ Đơn tối thiểu [0    ] đ              │
│ Hiệu lực   [30      ] ngày           │
│                                      │
│ PHÁT CHO HẠNG NÀO                    │
│ ☐ Bạc        [1  ] mã/tháng          │
│ ☑ Vàng       [2  ] mã/tháng          │
│ ☑ Kim cương  [4  ] mã/tháng          │
├──────────────────────────────────────┤
│              [Hủy]  [Lưu thay đổi]   │
└──────────────────────────────────────┘
```

---

### ■ W-05 — Thống kê gói & voucher

```
┌────────────────────────────────────────────────────────────┐
│ Thống kê gói thành viên        [Tháng này ▾] [01/11-13/11] │
├────────────────────────────────────────────────────────────┤
│ ┌────────┐┌────────┐┌────────┐┌────────┐                  │
│ │Doanh   ││VIP đang││Mua mới ││Không   │  4 thẻ số         │
│ │thu gói ││hoạt    ││tháng   ││gia hạn │                   │
│ │28,4 tr ││ 517    ││  86    ││  23    │                   │
│ │▲ 12%   ││▲ 8%    ││        ││▼ 4%    │                   │
│ └────────┘└────────┘└────────┘└────────┘                  │
├────────────────────────────────────────────────────────────┤
│ Doanh thu theo tháng, chia theo hạng                       │
│  ▁▃▅▆█  cột chồng 3 màu (Bạc/Vàng/Kim cương)              │
│  T6 T7 T8 T9 T10 T11                                       │
├─────────────────────────┬──────────────────────────────────┤
│ Cơ cấu thành viên       │ Voucher                          │
│  ◔ bánh donut           │ Mẫu      │Phát│Dùng│H.hạn│Đã chi│
│  Bạc 142 · Vàng 318     │ Free ship│1.2k│ 840│ 312 │9,8tr │
│  Kim cương 57           │ Giảm giá │ 684│ 402│ 198 │12,4tr│
└─────────────────────────┴──────────────────────────────────┘
┌────────────────────────────────────────────────────────────┐
│ ⚠ Đối chiếu lãi/lỗ theo hạng                              │
│ Hạng     │Doanh thu│Chi voucher│Lượt AI đã dùng│Còn lại   │
│ Bạc      │ 4,1 tr  │   1,8 tr  │   12.400      │ 2,3 tr   │
│ Vàng     │18,7 tr  │   8,2 tr  │   78.900      │10,5 tr   │
│ Kim cương│ 5,6 tr  │   3,4 tr  │   41.200      │ 2,2 tr   │
└────────────────────────────────────────────────────────────┘
```

> Bảng cuối là thứ quan trọng nhất của màn này: cho biết từng hạng đang lãi hay lỗ. Cột
> "Còn lại" tô đỏ nếu âm.

---

## 6. DỮ LIỆU MẪU DÙNG CHUNG

Dùng đúng các giá trị này khi vẽ, đừng bịa số khác — để các màn khớp nhau.

**Người dùng đang đăng nhập (mobile):**
- Tên: Nguyễn Văn An · Mã: MB-000123 · Email: an@email.com
- Gói: Thành viên Vàng, theo tháng, 59.000đ
- Bắt đầu 13/11/2026 · Hết hạn 13/12/2026 · Còn 19 ngày
- Số dư ví: 320.000đ
- Hạn mức hôm nay: thử đồ 2/10 · phối đồ 7/40 · form 3/15
- Đang có 7 món ký gửi nổi bật · 5 voucher chưa dùng

**Mã voucher mẫu:**
- `SHIP-7K2M9A` — Miễn phí vận chuyển, giảm tối đa 15.000đ, HSD 13/12/2026
- `SALE-3B8Q1Z` — Giảm giá đơn hàng 10% tối đa 50.000đ, HSD 20/12/2026
- `SHIP-2M8K1A` — Miễn phí vận chuyển, đã hết hạn 01/11/2026

**Đơn hàng mẫu:**
- Mã đơn `DH-2026-000987` · Tạm tính 400.000đ · Ship 20.000đ · Giảm 25.000đ · Trả 395.000đ

**Giỏ 2 món (cho M-07):**
- Áo khoác denim xanh 400.000đ + Váy hoa nhí vintage 400.000đ
- Tạm tính 800.000đ · Ship 40.000đ · Giảm 50.000đ · Tổng 790.000đ

**Số liệu Admin:**
- Tổng VIP: 517 (Bạc 142 · Vàng 318 · Kim cương 57)
- Doanh thu tháng 11: 28,4 triệu đồng

---

## 7. CHECKLIST TRƯỚC KHI NHẬN THIẾT KẾ

- [ ] Mọi con số hạn mức là **text**, không nung vào hình — vì Admin sửa được
- [ ] Mỗi màn có đủ trạng thái: mặc định, rỗng, lỗi, đang tải
- [ ] Trạng thái "Thành viên thường" được vẽ cho M-03, M-04, M-09, M-11
- [ ] Nhãn "Nổi bật" không che giá và tên sản phẩm
- [ ] Số tiền luôn có dấu chấm ngăn nghìn và chữ "đ"
- [ ] Số tiền giảm luôn có dấu trừ và màu riêng
- [ ] Màn hủy đơn hiện số hoàn **đã trừ voucher**
- [ ] Không có thương hiệu có thật nào xuất hiện trong thiết kế
- [ ] Không có chữ nào gợi ý "Nổi bật" = chất lượng tốt hơn
- [ ] Chữ tiếng Việt có dấu hiển thị đúng, không bị cắt chân chữ
