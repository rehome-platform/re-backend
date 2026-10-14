# Entity inventory

Source: https://docs.google.com/document/d/1Ki0BVLH-7-HAVJOLnKfb_VFevidjJkqNc9Hgg8dtmJQ/edit

45 design entities, 338 database columns. Account already existed; 44 entities were added.
EmailOtp (8 columns) and RefreshToken (5 columns) are existing authentication support entities, outside the design: 47 entities / 351 columns total.

## Mapping decisions

- Account remains in its original package to preserve authentication. New entities use entity.auth, entity.marketplace, entity.tryon, entity.chat and entity.audit.
- OrganizationProfile.orgType is temporarily a String: the document does not specify enum values.
- Profile accountId and the @MapsId account association share one database column; count that column only once.
- Real foreign keys use lazy, unidirectional associations; no cascading deletes are introduced.
- One-to-one relations described in section 7 are backed by unique foreign keys.
- WalletTransaction.counterpartTransaction is a nullable self-referencing foreign key.
- Polymorphic walletId, report targetId, conversation counterpartId and audit targetId remain scalar values.
- Monetary values: numeric(19,2). Rates: decimal(10,6). Coordinates: decimal(10,7).
- Strings default to 255 characters; URL fields and website allow 2048. Narrative fields use text.
- Timestamps use Instant (Hibernate maps them to timestamp with time zone on PostgreSQL), consistent with existing authentication.
- JSON uses JsonNode with Hibernate JSON mapping to jsonb.
- Java builder defaults and database defaults follow the design. Creation/update timestamps are initialized by JPA callbacks.
- TryOnImage has a database check requiring exactly an outfit (top AND bottom) OR a dress.
- Other business workflows (payments, renewals, singleton platform fund, role validation, polymorphic references) need service logic; entity creation does not implement these workflows or add CRUD endpoints.

## Columns by entity

### Account — 10 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| id | UUID | PK | Khóa chính của tài khoản |
| email | varchar | UNIQUE, NOT NULL | Email đăng nhập, mỗi email chỉ một tài khoản |
| password_hash | varchar | NULL | Mật khẩu đã băm; null nếu chỉ đăng nhập Google |
| google_id | varchar | UNIQUE, NULL | ID Google trả về khi đăng nhập bằng Google |
| phone | varchar | UNIQUE, NULL | Số điện thoại, dùng tra cứu Member tại quầy |
| role | enum | NOT NULL | Vai của tài khoản: MEMBER / ORGANIZATION / MODERATOR / ADMIN |
| status | enum | NOT NULL, default ACTIVE | ACTIVE / BANNED — BANNED thì bị chặn đăng nhập |
| email_verified | boolean | default false | Đã xác thực email qua link gửi về hay chưa |
| created_at | timestamp | NOT NULL | Thời điểm tạo tài khoản |
| updated_at | timestamp | NOT NULL | Thời điểm sửa gần nhất |

### MemberProfile — 4 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| account_id | UUID | PK, FK → Account.id | Dùng chung khóa với Account, quan hệ 1-1 |
| full_name | varchar | NOT NULL | Họ tên Member, Moderator đối chiếu khi giao nhận tại quầy |
| avatar_url | varchar | NULL | Ảnh đại diện, dùng xác nhận đúng người tại quầy |
| member_code | varchar | UNIQUE, NOT NULL | Mã Member để tra cứu tại quầy |

### OrganizationProfile — 22 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| account_id | UUID | PK, FK → Account.id | Dùng chung khóa với Account, quan hệ 1-1 |
| legal_name | varchar | NOT NULL | Tên đầy đủ theo giấy tờ, để đối chiếu với ảnh giấy tờ |
| display_name | varchar | NOT NULL | Tên người dùng nhìn thấy trên nền tảng |
| org_type | enum | NOT NULL | Loại hình tổ chức, quyết định checklist giấy tờ lúc xác minh |
| tax_code | varchar | NULL | Mã số thuế nếu có; CLB trường học thường không có |
| operation_scope | varchar | NOT NULL | Phạm vi hoạt động: toàn quốc, tỉnh, huyện hay xã |
| address | varchar | NOT NULL | Địa chỉ trụ sở, đối chiếu với địa chỉ trên giấy tờ |
| contact_phone | varchar | NOT NULL | Số liên hệ, Moderator gọi kiểm chứng |
| contact_email | varchar | NOT NULL | Email liên hệ, Moderator gửi mail kiểm chứng |
| website | varchar | NULL | Website hoặc fanpage, dùng tra dấu hiệu bất thường |
| description | text | NOT NULL | Mô tả hoạt động, hiển thị trên hồ sơ công khai |
| logo_url | varchar | NULL | Logo tổ chức |
| verification_status | enum | NOT NULL, default PENDING | PENDING / VERIFIED / REJECTED — quyết định tổ chức được vào màn hình nào |
| rejection_reason | text | NULL | Lý do từ chối, để tổ chức sửa rồi gửi lại |
| verified_by | UUID | FK → Account.id, NULL | Moderator đã kết luận hồ sơ |
| verified_at | timestamp | NULL | Thời điểm kết luận, cũng là mốc tính hạn tự xóa ảnh CCCD |
| donation_point_name | varchar | NOT NULL | Tên điểm nhận đồ (gộp từ OrganizationDonationPoint vì mỗi tổ chức chỉ có 1 điểm nhận) |
| donation_point_address | varchar | NOT NULL | Địa chỉ đầy đủ của điểm nhận đồ |
| donation_point_latitude | decimal | NOT NULL | Vĩ độ điểm nhận đồ, để ghim lên bản đồ |
| donation_point_longitude | decimal | NOT NULL | Kinh độ điểm nhận đồ, để ghim lên bản đồ |
| donation_point_receiving_hours | varchar | NOT NULL | Khung giờ nhận đồ trong tuần |
| donation_point_contact_phone | varchar | NULL | Số điện thoại tại điểm nhận, có thể khác contact_phone chung của tổ chức |

### OrganizationRepresentative — 7 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| organization_id | UUID | FK → OrganizationProfile.account_id | Tổ chức có người đại diện này |
| full_name | varchar | NOT NULL | Họ tên, phải khớp tên ghi trên giấy phép |
| position | varchar | NOT NULL | Chức vụ, xác định thẩm quyền đại diện |
| cccd_number | varchar | NOT NULL | Số CCCD, đối chiếu với ảnh CCCD |
| cccd_front_url | varchar | NULL | Ảnh CCCD mặt trước, tự xóa sau khi hồ sơ được duyệt |
| cccd_back_url | varchar | NULL | Ảnh CCCD mặt sau, tự xóa sau khi hồ sơ được duyệt |

### OrganizationDocument — 7 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| organization_id | UUID | FK → OrganizationProfile.account_id | Tổ chức đã nộp giấy tờ này |
| doc_type | varchar | NOT NULL | Loại giấy tờ, ví dụ giấy phép thành lập, điều lệ |
| doc_number | varchar | NULL | Số giấy tờ; quyết định nội bộ thường không có số |
| issue_date | date | NULL | Ngày cấp ghi trên giấy |
| issuing_authority | varchar | NULL | Cơ quan đã cấp giấy |
| file_url | varchar | NOT NULL | Tệp ảnh hoặc PDF, chỉ Moderator xem được |

### ModeratorProfile — 4 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| account_id | UUID | PK, FK → Account.id | Dùng chung khóa với Account, quan hệ 1-1 |
| full_name | varchar | NOT NULL | Họ tên Moderator, in trên biên nhận ký gửi — mục 12 |
| moderator_code | varchar | UNIQUE, NOT NULL | Mã nhân viên, in trên biên nhận, ví dụ MOD-07-003 — mục 12 |
| warehouse_id | UUID | FK → Warehouse.id | Kho mà Moderator thuộc về |

### OrganizationLegalChangeRequest — 8 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| organization_id | UUID | FK → OrganizationProfile.account_id | Tổ chức xin đổi thông tin pháp lý |
| proposed_changes | jsonb | NOT NULL | Nội dung xin đổi, lưu JSON nên không cần cột riêng cho từng trường |
| status | enum | NOT NULL, default PENDING | PENDING / APPROVED / REJECTED |
| reject_reason | text | NULL | Lý do từ chối yêu cầu |
| reviewed_by | UUID | FK → Account.id, NULL | Moderator đã duyệt hoặc từ chối |
| reviewed_at | timestamp | NULL | Thời điểm duyệt |
| created_at | timestamp | NOT NULL | Thời điểm gửi yêu cầu |

### Warehouse — 8 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| id | UUID | PK | Khóa chính của chi nhánh kho |
| name | varchar | NOT NULL | Tên kho, ví dụ Kho Quận 7 |
| address | varchar | NOT NULL | Địa chỉ kho, in trên biên nhận ký gửi |
| latitude | decimal | NOT NULL | Vĩ độ, dùng gợi ý kho gần nhất — F-015 |
| longitude | decimal | NOT NULL | Kinh độ, dùng gợi ý kho gần nhất — F-015 |
| opening_hours | varchar | NOT NULL | Giờ mở cửa, Member xem trước khi mang đồ đến |
| status | enum | NOT NULL, default ACTIVE | ACTIVE / HIDDEN — HIDDEN thì không chọn được kho này nữa |
| created_at | timestamp | NOT NULL | Thời điểm tạo kho |

### Category — 4 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| id | UUID | PK | Khóa chính danh mục |
| name | varchar | NOT NULL | Tên danh mục, ví dụ Áo khoác |
| tryon_slot | enum | NOT NULL | TOP / BOTTOM / DRESS — quyết định món vào slot Áo, Quần hay Đầm khi thử đồ AI |
| status | enum | NOT NULL, default ACTIVE | ACTIVE / HIDDEN — ẩn thì không nhận ký gửi mới, dữ liệu cũ vẫn giữ |

### Brand — 3 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| id | UUID | PK | Khóa chính thương hiệu |
| name | varchar | NOT NULL | Tên thương hiệu được nhận ký gửi |
| status | enum | NOT NULL, default ACTIVE | ACTIVE / HIDDEN — ẩn thì không nhận ký gửi mới, dữ liệu cũ vẫn giữ |

### StockMovementLog — 9 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| product_id | varchar | FK → Product.id | Sản phẩm có biến động |
| warehouse_id | UUID | FK → Warehouse.id | Kho xảy ra biến động |
| movement_type | enum | NOT NULL | STOCK_IN / SHELF_MOVE / WITHDRAWAL_OUT / RETURN_IN / DONATED_OUT / DESTROYED |
| from_location | varchar | NULL | Vị trí kệ trước khi chuyển |
| to_location | varchar | NULL | Vị trí kệ sau khi chuyển |
| note | text | NULL | Ghi chú thêm của Moderator |
| moderator_id | UUID | FK → Account.id | Moderator thực hiện |
| created_at | timestamp | NOT NULL | Thời điểm ghi nhận — F-126 |

### ConsignmentForm — 13 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| id | UUID | PK | Khóa chính của form ký gửi |
| member_id | UUID | FK → Account.id | Member gửi form |
| warehouse_id | UUID | FK → Warehouse.id | Kho Member chọn mang đồ đến |
| category_id | UUID | FK → Category.id | Danh mục của món đồ |
| brand_id | UUID | FK → Brand.id | Thương hiệu của món đồ |
| product_name | varchar | NOT NULL | Tên món đồ do Member khai |
| description | text | NOT NULL | Mô tả tình trạng do Member tự khai |
| desired_price | numeric | NOT NULL | Giá Member mong muốn; giá chốt thật do Moderator thỏa thuận lại |
| status | enum | NOT NULL, default PENDING | PENDING / APPROVED / REJECTED / EXPIRED / CANCELLED |
| reject_reason | text | NULL | Lý do từ chối, để Member sửa form |
| bring_in_deadline | date | NULL | Hạn mang đồ đến kho sau khi duyệt, mặc định 7 ngày; quá hạn thành EXPIRED |
| created_at | timestamp | NOT NULL | Thời điểm gửi form |
| updated_at | timestamp | NOT NULL | Thời điểm sửa gần nhất |

### ConsignmentFormPhoto — 3 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| form_id | UUID | FK → ConsignmentForm.id | Form chứa ảnh này |
| image_url | varchar | NOT NULL | Ảnh món đồ Member chụp kèm form |

### ConsignmentReceipt — 19 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| id | UUID | PK | Khóa chính của biên nhận |
| receipt_no | varchar | UNIQUE, NOT NULL | Số biên nhận in trên PDF, ví dụ KG-2026-000123 |
| consignment_form_id | UUID | FK → ConsignmentForm.id | Form đã dẫn đến biên nhận này |
| member_id | UUID | FK → Account.id | Bên B, người ký gửi |
| moderator_id | UUID | FK → Account.id | Bên A, Moderator tiếp nhận tại quầy |
| warehouse_id | UUID | FK → Warehouse.id | Kho nhận hàng |
| agreed_price | numeric | NOT NULL | Giá bán hai bên chốt; snapshot, đổi cấu hình sau không ảnh hưởng |
| floor_price | numeric | NOT NULL | Giá sàn = 50% agreed_price; Member không được hạ thấp hơn mức này |
| term_length_days | int | NOT NULL, default 30 | Độ dài một kỳ ký gửi; snapshot |
| max_renewals | int | NOT NULL, default 2 | Số lần tự gia hạn, 2 lần = tổng 90 ngày trên sàn; snapshot |
| free_withdrawal_window_days | int | NOT NULL, default 7 | Số ngày đầu mỗi kỳ được rút hàng miễn phí; snapshot |
| commission_rate_term1 | decimal | NOT NULL, default 0.10 | Hoa hồng kỳ 1 (ngày 0–30); snapshot |
| commission_rate_term2 | decimal | NOT NULL, default 0.15 | Hoa hồng kỳ 2 (ngày 31–60); snapshot |
| commission_rate_term3 | decimal | NOT NULL, default 0.20 | Hoa hồng kỳ 3 (ngày 61–90); snapshot |
| withdrawal_fee | numeric | NOT NULL | Phí rút lại hàng ngoài cửa sổ miễn phí; snapshot |
| degraded_compensation_rate | decimal | NOT NULL, default 0.50 | Tỷ lệ đền khi Member lấy hàng xuống cấp về; snapshot |
| confirmed_at | timestamp | NOT NULL | Lúc Member bấm xác nhận — biên nhận có hiệu lực từ mốc này |
| pdf_url | varchar | NOT NULL | Tệp PDF hệ thống sinh ra — F-022 |
| created_at | timestamp | NOT NULL | Thời điểm tạo bản ghi |

### Product — 18 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| id | varchar | PK | Mã SKU Moderator gán lúc nhập kho, ví dụ Q7-AK-000845 |
| consignment_receipt_id | UUID | UNIQUE, FK → ConsignmentReceipt.id | Biên nhận sinh ra sản phẩm này, quan hệ 1-1 |
| member_id | UUID | FK → Account.id | Người ký gửi, cũng là người nhận tiền khi bán được |
| warehouse_id | UUID | FK → Warehouse.id | Kho đang giữ hàng |
| category_id | UUID | FK → Category.id | Danh mục, cũng quyết định slot thử đồ Áo hay Quần |
| brand_id | UUID | FK → Brand.id | Thương hiệu |
| name | varchar | NOT NULL | Tên sản phẩm hiển thị trên sàn |
| size | varchar | NULL | Kích cỡ, ví dụ M |
| condition_note | text | NOT NULL | Tình trạng theo kết quả kiểm định của Moderator |
| current_price | numeric | NOT NULL | Giá đang bán; Member hạ được nhưng không dưới giá sàn |
| status | enum | NOT NULL, default ON_SALE | ON_SALE / RESERVED / SOLD / WITHDRAWN / UNCLAIMED / DONATED / DESTROYED |
| current_term | smallint | NOT NULL, default 1 | Kỳ ký gửi đang chạy: 1, 2 hay 3 — quyết định tỷ lệ hoa hồng |
| term_started_at | timestamp | NOT NULL | Ngày bắt đầu kỳ hiện tại, mốc tính cửa sổ rút miễn phí 7 ngày |
| end_of_term_deadline | timestamp | NULL | Ngày hết kỳ cuối (ngày 90), lúc này gỡ khỏi sàn |
| unclaimed_decision_deadline | timestamp | NULL | Hạn 30 ngày Member chọn cách xử lý cuối kỳ; quá hạn thành UNCLAIMED |
| shelf_location | varchar | NULL | Vị trí kệ trong kho để lấy hàng nhanh |
| created_at | timestamp | NOT NULL | Thời điểm nhập kho |
| updated_at | timestamp | NOT NULL | Thời điểm cập nhật gần nhất |

### ProductPhoto — 4 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| product_id | varchar | FK → Product.id | Sản phẩm của ảnh này |
| image_url | varchar | NOT NULL | Ảnh sản phẩm hiển thị trên sàn |
| is_background_removed | boolean | default false | Đã tách nền chưa; chỉ ảnh đã tách nền dùng được cho thử đồ AI — F-120 |

### PriceChangeLog — 5 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| product_id | varchar | FK → Product.id | Sản phẩm bị đổi giá |
| old_price | numeric | NOT NULL | Giá trước khi đổi |
| new_price | numeric | NOT NULL | Giá sau khi đổi, luôn thấp hơn giá cũ |
| changed_at | timestamp | NOT NULL | Thời điểm đổi; dùng đếm giới hạn 1 lần đổi giá mỗi ngày |

### Cart — 2 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| member_id | UUID | UNIQUE, FK → Account.id | Mỗi Member đúng một giỏ |

### CartItem — 4 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| cart_id | UUID | FK → Cart.id | Giỏ chứa dòng này |
| product_id | varchar | FK → Product.id | Sản phẩm trong giỏ; UNIQUE cùng cart_id — mỗi món chỉ một dòng |
| added_at | timestamp | NOT NULL | Thời điểm thêm vào giỏ |

### ShippingAddress — 6 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| member_id | UUID | FK → Account.id | Chủ địa chỉ |
| recipient_name | varchar | NOT NULL | Tên người nhận hàng |
| phone | varchar | NOT NULL | Số điện thoại người nhận |
| address_detail | varchar | NOT NULL | Địa chỉ chi tiết để GHN giao |
| is_default | boolean | default false | Địa chỉ được chọn sẵn khi đặt hàng |

### Order — 18 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| id | UUID | PK | Khóa chính của đơn |
| order_no | varchar | UNIQUE, NOT NULL | Mã đơn Member nhìn thấy và dùng để tra cứu |
| buyer_id | UUID | FK → Account.id | Người mua |
| warehouse_id | UUID | FK → Warehouse.id | Mỗi đơn chỉ 1 kho — giỏ nhiều kho bị tách thành nhiều đơn |
| shipping_address_id | UUID | FK → ShippingAddress.id | Địa chỉ giao hàng chọn lúc đặt |
| subtotal | numeric | NOT NULL | Tổng tiền sản phẩm, chưa gồm phí ship |
| shipping_fee | numeric | NOT NULL | Phí vận chuyển, trả hết cho GHN, nền tảng không hưởng phần này |
| total_amount | numeric | NOT NULL | Số tiền người mua trả = subtotal + shipping_fee |
| payment_method | enum | NOT NULL | WALLET / MOMO — chọn một, không trả nửa ví nửa MoMo |
| status | enum | NOT NULL, default PENDING_PAYMENT | PENDING_PAYMENT / PAID / PACKING / SHIPPING / DELIVERED / COMPLETED / CANCELLED |
| ghn_tracking_code | varchar | NULL | Mã vận đơn GHN, có sau khi kho đóng gói |
| payment_deadline | timestamp | NOT NULL | Hạn thanh toán, mặc định 15 phút; quá hạn hủy đơn và nhả hàng về sàn |
| delivered_at | timestamp | NULL | Thời điểm GHN báo đã giao |
| auto_complete_deadline | timestamp | NULL | Hạn tự hoàn thành, mặc định 3 ngày sau khi giao |
| service_rating | smallint | NULL | 1–5 sao — UC-39 / F-066 |
| rated_at | timestamp | NULL | Thời điểm Member đánh giá |
| created_at | timestamp | NOT NULL | Thời điểm đặt đơn — mốc chốt tỷ lệ hoa hồng theo kỳ |
| updated_at | timestamp | NOT NULL | Thời điểm cập nhật gần nhất |

### OrderItem — 6 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| order_id | UUID | FK → Order.id | Đơn chứa dòng này |
| product_id | varchar | FK → Product.id | Sản phẩm được mua |
| price_at_order | numeric | NOT NULL | Giá bán chốt lúc đặt đơn, Member hạ giá sau không ảnh hưởng |
| commission_rate_applied | decimal | NOT NULL | Tỷ lệ hoa hồng của kỳ đang chạy lúc đặt đơn, chốt tại đây |
| tax_rate_applied | decimal | NOT NULL | Tỷ lệ thuế khấu trừ chốt lúc đặt đơn |

### Payment — 7 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| order_id | UUID | FK → Order.id | Đơn được thanh toán |
| amount | numeric | NOT NULL | Số tiền thực nhận qua MoMo |
| method | varchar | NOT NULL, default MOMO | Luôn là MOMO; trả bằng ví không tạo dòng ở bảng này |
| momo_transaction_id | varchar | NULL | Mã giao dịch MoMo trả về qua webhook |
| status | enum | NOT NULL, default PENDING | PENDING / SUCCESS / FAILED |
| paid_at | timestamp | NULL | Thời điểm MoMo xác nhận đã nhận tiền |

### ReturnRequest — 15 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| order_id | UUID | FK → Order.id | Đơn bị trả hàng |
| reason | text | NOT NULL | Lý do trả do Member nhập |
| status | enum | NOT NULL, default PENDING | PENDING / APPROVED / SHIPPED_BACK / INSPECTING / VALID / INVALID / LOST / CANCELLED |
| ghn_return_tracking_code | varchar | NULL | Mã vận đơn chiều trả về kho |
| handover_deadline | timestamp | NOT NULL | Hạn bàn giao cho GHN, mặc định 3 ngày; không bàn giao thì hủy yêu cầu |
| invalid_return_choice | enum | NULL | SHIP_BACK / WAREHOUSE_PICKUP — chỉ có khi kết luận INVALID |
| pickup_deadline | timestamp | NULL | Hạn đến kho nhận lại hàng trả không hợp lệ, mặc định 7 ngày |
| resolution_note | text | NULL | Kết luận xử lý của Moderator sau khi kiểm hàng về |
| counterfeit_suspected | boolean | default false | Moderator A gắn cờ nghi hàng giả — bắt buộc có Moderator thứ hai |
| second_moderator_id | UUID | FK → Account.id, NULL | Moderator B kiểm định lại ca nghi hàng giả — F-134 |
| counterfeit_conclusion | enum | NULL | CONFIRMED / DISMISSED — kết luận cuối về hàng giả — F-135 |
| moderator_id | UUID | FK → Account.id, NULL | Moderator xử lý yêu cầu |
| created_at | timestamp | NOT NULL | Thời điểm Member gửi yêu cầu |
| resolved_at | timestamp | NULL | Thời điểm chốt kết luận |

### ReturnRequestPhoto — 3 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| return_request_id | UUID | FK → ReturnRequest.id | Yêu cầu trả hàng chứa ảnh này |
| image_url | varchar | NOT NULL | Ảnh Member chụp làm bằng chứng |

### ItemWithdrawalRequest — 9 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| product_id | varchar | FK → Product.id | Sản phẩm Member muốn rút về |
| member_id | UUID | FK → Account.id | Người ký gửi |
| fee_charged | numeric | NOT NULL, default 0 | Phí rút hàng; bằng 0 nếu rút trong cửa sổ 7 ngày miễn phí |
| fee_payment_method | enum | NULL | WALLET / MOMO / MIXED — trừ ví trước, thiếu mới qua MoMo |
| status | enum | NOT NULL, default PENDING_PICKUP | PENDING_PICKUP / COMPLETED / EXPIRED |
| pickup_deadline | timestamp | NOT NULL | Hạn đến kho nhận hàng, mặc định 7 ngày; quá hạn hủy và đăng bán lại |
| created_at | timestamp | NOT NULL | Thời điểm tạo yêu cầu |
| completed_at | timestamp | NULL | Thời điểm Moderator bàn giao hàng tại quầy |

### EndOfTermDecision — 6 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| product_id | varchar | FK → Product.id | Sản phẩm đã hết 90 ngày trên sàn |
| decision | enum | NULL | TAKE_BACK / RE_CONSIGN / UNCLAIMED — null là Member chưa chọn |
| new_receipt_id | UUID | FK → ConsignmentReceipt.id, NULL | Biên nhận mới nếu Member chọn ký gửi lại |
| decided_at | timestamp | NULL | Thời điểm Member chọn |
| deadline | timestamp | NOT NULL | Hạn 30 ngày phải chọn; quá hạn thành hàng vô thừa nhận |

### DegradedItemCase — 7 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| product_id | varchar | FK → Product.id | Sản phẩm về kho trong tình trạng xấu hơn |
| return_request_id | UUID | FK → ReturnRequest.id, NULL | Yêu cầu trả hàng dẫn đến ca này, nếu có |
| choice | enum | NOT NULL | FULL_COMPENSATION / PARTIAL_WITH_ITEM_BACK — Member chọn |
| compensation_amount | numeric | NOT NULL | Tiền nền tảng đền; đền đủ hoặc 50% tùy lựa chọn — F-162 |
| moderator_id | UUID | FK → Account.id | Moderator kết luận hàng xuống cấp |
| created_at | timestamp | NOT NULL | Thời điểm ghi nhận ca |

### Wallet — 5 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| member_id | UUID | UNIQUE, FK → Account.id | Mỗi Member đúng một ví, dùng cho cả vai mua và vai bán |
| balance | numeric | NOT NULL, default 0 | Số dư hiện có trong ví |
| locked_balance | numeric | NOT NULL, default 0 | Tiền đang khóa cho yêu cầu rút; số dư khả dụng = balance − locked_balance |
| updated_at | timestamp | NOT NULL | Thời điểm số dư đổi gần nhất |

### PlatformFund — 3 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| id | UUID | PK | Khóa chính; hệ thống chỉ có đúng một dòng quỹ |
| balance | numeric | NOT NULL, default 0 | Tiền đang nằm ở tài khoản nền tảng; không phải ví của ai và không rút được |
| updated_at | timestamp | NOT NULL | Thời điểm số dư đổi gần nhất |

### WalletTransaction — 10 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| id | UUID | PK | Khóa chính của một bút toán |
| wallet_type | enum | NOT NULL | MEMBER / PLATFORM — quyết định wallet_id trỏ vào bảng nào |
| wallet_id | UUID | NOT NULL | Khóa đa hình: Wallet.id nếu MEMBER, PlatformFund.id nếu PLATFORM — không đặt FK được vì trỏ hai bảng |
| type | enum | NOT NULL | ORDER_PAYMENT / ORDER_PAYOUT / REFUND / WITHDRAWAL / WITHDRAWAL_FEE / DEGRADED_COMPENSATION / COMMISSION_REVENUE / WITHDRAWAL_FEE_REVENUE |
| amount | numeric | NOT NULL | Số tiền; dương là tiền vào, âm là tiền ra |
| related_order_id | UUID | FK → Order.id, NULL | Đơn liên quan, để Member xem doanh thu theo từng đơn |
| counterpart_transaction_id | UUID | NULL | Bút toán đối ứng bên kia — ghi sổ kép, hai dòng bằng nhau và ngược dấu |
| status | enum | NOT NULL, default SUCCESS | PENDING / PROCESSING / SUCCESS / FAILED |
| note | varchar | NULL | Diễn giải ngắn hiển thị trong lịch sử ví |
| created_at | timestamp | NOT NULL | Thời điểm ghi bút toán — F-039 |

### PayoutRequest — 7 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| wallet_id | UUID | FK → Wallet.id | Ví bị trừ tiền |
| amount | numeric | NOT NULL | Số tiền rút; phải đạt mức rút tối thiểu Admin cấu hình |
| status | enum | NOT NULL, default PENDING | PENDING / PROCESSING / SUCCESS / FAILED — không có bước người duyệt |
| failure_reason | varchar | NULL | Lý do thất bại, để nhả lại tiền đã khóa cho Member tạo lại |
| created_at | timestamp | NOT NULL | Thời điểm tạo yêu cầu, cũng là lúc khóa số dư |
| processed_at | timestamp | NULL | Thời điểm giả lập chi tiền xong |

### Campaign — 11 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| organization_id | UUID | FK → OrganizationProfile.account_id | Tổ chức tạo chiến dịch |
| description | text | NOT NULL | Mô tả chiến dịch cho người quyên góp đọc |
| start_time | timestamp | NOT NULL | Thời điểm bắt đầu nhận đồ |
| end_time | timestamp | NOT NULL | Thời điểm hết nhận; đến hạn hệ thống tự đóng chiến dịch |
| location_name | varchar | NOT NULL | Tên địa điểm nhận đồ |
| latitude | decimal | NOT NULL | Vĩ độ, để ghim lên bản đồ |
| longitude | decimal | NOT NULL | Kinh độ, để ghim lên bản đồ |
| preferred_item_types | varchar | NULL | Loại đồ tổ chức ưu tiên nhận |
| status | enum | NOT NULL, default OPEN | OPEN / CLOSED |
| created_at | timestamp | NOT NULL | Thời điểm tạo chiến dịch |

### CommunityPost — 6 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| organization_id | UUID | FK → OrganizationProfile.account_id | Tổ chức đăng bài |
| content | text | NOT NULL | Nội dung bài viết |
| status | enum | NOT NULL, default VISIBLE | VISIBLE / REMOVED — Moderator gỡ bài thì chuyển REMOVED, không xóa dữ liệu |
| created_at | timestamp | NOT NULL | Thời điểm đăng |
| updated_at | timestamp | NOT NULL | Thời điểm sửa gần nhất |

### CommunityPostPhoto — 3 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| post_id | UUID | FK → CommunityPost.id | Bài viết chứa ảnh này |
| image_url | varchar | NOT NULL | Ảnh kèm bài viết |

### PostLike — 4 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| post_id | UUID | FK → CommunityPost.id | Bài được thích |
| member_id | UUID | FK → Account.id | Người thích; UNIQUE cùng post_id, mỗi người thích 1 lần |
| created_at | timestamp | NOT NULL | Thời điểm thích |

### PostComment — 6 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| post_id | UUID | FK → CommunityPost.id | Bài được bình luận |
| member_id | UUID | FK → Account.id | Người bình luận |
| content | text | NOT NULL | Nội dung bình luận |
| status | enum | NOT NULL, default VISIBLE | VISIBLE / REMOVED — Moderator xóa bình luận thì chuyển REMOVED |
| created_at | timestamp | NOT NULL | Thời điểm bình luận |

### Follow — 4 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| member_id | UUID | FK → Account.id | Người theo dõi |
| organization_id | UUID | FK → OrganizationProfile.account_id | Tổ chức được theo dõi; UNIQUE cùng member_id |
| created_at | timestamp | NOT NULL | Thời điểm bắt đầu theo dõi |

### ViolationReport — 10 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| reporter_member_id | UUID | FK → Account.id | Member gửi báo cáo |
| target_type | enum | NOT NULL | POST / CAMPAIGN / ORGANIZATION_ACCOUNT — quyết định target_id trỏ bảng nào |
| target_id | UUID | NOT NULL | Khóa đa hình: id của CommunityPost, Campaign hoặc Account bị báo — không đặt FK được vì trỏ ba bảng |
| reason | text | NOT NULL | Lý do báo cáo |
| status | enum | NOT NULL, default PENDING | PENDING / RESOLVED / DISMISSED |
| resolved_by | UUID | FK → Account.id, NULL | Moderator xử lý báo cáo |
| resolution_action | varchar | NULL | Hành động đã làm, ví dụ gỡ bài, ẩn chiến dịch |
| created_at | timestamp | NOT NULL | Thời điểm gửi báo cáo |
| resolved_at | timestamp | NULL | Thời điểm xử lý xong |

### SystemConfig — 5 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| key | varchar | UNIQUE, NOT NULL | Tên tham số, ví dụ commission_rate_term1, min_payout_amount |
| value | varchar | NOT NULL | Giá trị lưu dạng chuỗi, code tự ép kiểu khi đọc |
| updated_by | UUID | FK → Account.id | Admin đã sửa tham số |
| updated_at | timestamp | NOT NULL | Thời điểm sửa; chỉ áp dụng cho giao dịch phát sinh sau đó |

### TryOnImage — 8 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| member_id | UUID | FK → Account.id | Chủ bộ sưu tập ảnh |
| model_type | enum | NOT NULL | MALE / FEMALE — người mẫu Member chọn |
| top_product_id | varchar | FK → Product.id, NULL | Món ở slot Áo. Bắt buộc nếu thử kiểu Áo+Quần; để NULL nếu thử Đầm (dress_product_id) |
| bottom_product_id | varchar | FK → Product.id, NULL | Món ở slot Quần. Bắt buộc nếu thử kiểu Áo+Quần; để NULL nếu thử Đầm (dress_product_id) |
| dress_product_id | varchar | FK → Product.id, NULL | Món ở slot Đầm (thử outfit 1 món). Bắt buộc nếu không dùng Áo+Quần |
| image_url | varchar | NOT NULL | Ảnh thử đồ do mô hình AI sinh ra |
| created_at | timestamp | NOT NULL | Thời điểm sinh ảnh |

### Conversation — 5 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| type | enum | NOT NULL | MEMBER_ORGANIZATION / MEMBER_WAREHOUSE — quyết định counterpart_id là gì |
| member_id | UUID | FK → Account.id | Member trong cuộc hội thoại |
| counterpart_id | UUID | NOT NULL | Khóa đa hình: OrganizationProfile.account_id hoặc Warehouse.id tùy type — không đặt FK được vì trỏ hai bảng |
| created_at | timestamp | NOT NULL | Thời điểm mở hội thoại |

### Message — 6 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| conversation_id | UUID | FK → Conversation.id | Hội thoại chứa tin nhắn |
| sender_account_id | UUID | FK → Account.id | Người gửi |
| content | text | NOT NULL | Nội dung tin nhắn |
| sent_at | timestamp | NOT NULL | Thời điểm gửi |
| read_at | timestamp | NULL | Thời điểm người nhận đọc; null là chưa đọc |

### Notification — 7 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| recipient_account_id | UUID | FK → Account.id | Người nhận thông báo |
| type | enum | NOT NULL | ORDER_UPDATE / WALLET_EVENT / TERM_EXPIRY / VERIFICATION_RESULT / QUEUE_TASK |
| title | varchar | NOT NULL | Tiêu đề ngắn hiện ở đầu thông báo |
| body | text | NOT NULL | Nội dung chi tiết |
| is_read | boolean | default false | Đã đọc chưa, dùng đếm badge |
| created_at | timestamp | NOT NULL | Thời điểm gửi thông báo |

### AuditLog — 7 columns

| Column | Design type | Design constraint | Description |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| actor_account_id | UUID | FK → Account.id | Người thực hiện thao tác |
| action_type | varchar | NOT NULL | Loại thao tác, ví dụ VERIFY_ORGANIZATION, UPDATE_CONFIG |
| target_type | varchar | NOT NULL | Loại đối tượng bị tác động, ví dụ ORDER, PRODUCT, SYSTEM_CONFIG |
| target_id | varchar | NOT NULL | Id đối tượng; để varchar vì SKU không phải UUID |
| detail | jsonb | NULL | Dữ liệu trước và sau khi đổi, lưu JSON |
| created_at | timestamp | NOT NULL | Thời điểm ghi log — F-161 |
