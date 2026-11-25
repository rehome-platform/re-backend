# Seed lịch sử mua hàng mô phỏng cho ML

File chạy: `scripts/seed_ml_purchase_history.sql`.
File xuất dữ liệu: `scripts/export_ml_purchase_history.sql`.
Generator có thể chỉnh số lượng: `scripts/generate_ml_purchase_seed.py` (Python chuẩn, không kết nối DB).

## Nội dung

| Dữ liệu | Số lượng |
|---|---:|
| Người mua có lịch sử | 30: mỗi nhóm thể thao, công sở, thoải mái có 10 người |
| Người mua mới, chưa có lịch sử | 3 |
| Người ký gửi / Moderator / kho | 3 / 1 / 1 |
| Đơn COMPLETED và order_items | 360 đơn, mỗi đơn một món riêng |
| Sản phẩm SOLD / ON_SALE | 360 / 90 |
| Form / biên nhận CONFIRMED | 450 / 450 |
| Danh mục / thương hiệu mô phỏng | 9 / 3 |

Mỗi người mua có 12 đơn: 9 món thuộc phong cách chính, 3 món thuộc hai phong cách khác.
Tên món có màu khác nhau, có size, danh mục, thương hiệu và biến thiên giá.
Project chưa có cột style/color riêng: không tạo cột giả. Màu/phong cách chỉ là tín hiệu
trong tên món và danh mục, cần xử lý đặc trưng ở bước ML.

## Chạy trên PostgreSQL local/test

1. Khởi tạo schema bằng backend/migration hiện tại; không cần chạy `seed_demo_full.sql`.
2. Tạm dừng backend trong lúc seed để tránh job và cache đọc dữ liệu giữa chừng.
3. Trong DBeaver/pgAdmin, kết nối đúng database test, mở file SQL và chạy **toàn bộ script**.
4. Script có transaction; nếu lỗi, rollback transaction trước khi chạy lại. Không chạy tiếp riêng đoạn lỗi.
5. Kiểm tra các SELECT cuối file: 360 COMPLETED, 360 SOLD, 90 ON_SALE; mỗi người mua cũ 12 đơn, người mới 0 đơn.
6. Khởi động lại backend. Cache danh sách sản phẩm cũ có thể còn đến hết TTL vì SQL trực tiếp không kích hoạt invalidation của service.

File chỉ INSERT, không TRUNCATE/DELETE/UPDATE, không thay CHECK constraint, không đụng sequence
của mã đơn/SKU thật. ID UUID cố định; mã sản phẩm/đơn/form/biên nhận bắt đầu `MLR1-`.
Chạy lại không nhân đôi các dòng đã có; không khôi phục dữ liệu seed đã bị bạn sửa qua API.
Không chạy trên database thật. Không chạy `seed_demo_full.sql` sau đó vì file đó xóa dữ liệu toàn hệ thống.

Đăng nhập với mật khẩu **123456**:

| Nhóm | Email |
|---|---|
| Thể thao | `ml.sport.01@example.test` đến `ml.sport.10@example.test` |
| Công sở | `ml.office.01@example.test` đến `ml.office.10@example.test` |
| Thoải mái | `ml.casual.01@example.test` đến `ml.casual.10@example.test` |
| Chưa mua | `ml.new.01@example.test` đến `ml.new.03@example.test` |
| Người ký gửi | `ml.seller.sport@example.test`, `ml.seller.office@example.test`, `ml.seller.casual@example.test` |
| Moderator | `ml.moderator@example.test` |

Địa chỉ, người dùng, kho và hàng hóa đều là giả lập; không tạo vận đơn từ dataset này.
Không seed ảnh sản phẩm; thumbnail có thể null. Không dùng dataset này để demo thử đồ AI.

## Phạm vi tài chính

Đây là fixture lịch sử cho recommender, không phải dữ liệu kiểm thử hạch toán end-to-end.
Các đơn có tổng tiền đúng với order_items và phí ship, có trạng thái COMPLETED và payout_at
để job không thực hiện payout tiếp. Không tạo giao dịch cổng thanh toán, wallet_transactions,
sổ quỹ, thông báo hoặc hàng đợi. Wallet được tạo với số dư 0. Không dùng dữ liệu này để
kiểm chứng doanh thu, số dư hay chức năng hoàn tiền/rút tiền.

## Dùng dữ liệu để làm ML

API Java mới `GET /tryon/outfit-suggestions/ml` đọc lịch sử trực tiếp từ database và fit mô hình
theo request, nên để thử API này chỉ cần chạy seed rồi đăng nhập, không cần xuất CSV.
Chi tiết: `docs/ml-outfit-suggestions-api.md`. Các hướng dẫn export/tách tập bên dưới dành
cho nghiên cứu, huấn luyện và đánh giá offline về sau.

- Chạy từng SELECT của file export, xuất lịch sử và catalogue ra hai CSV riêng.
- Không dùng email, full_name, prefix fixture hoặc nhãn nhóm giả lập làm feature;
  chỉ dùng user_id làm định danh và đặc điểm sản phẩm/lịch sử.
- Mỗi món ký gửi chỉ có một người mua, nên collaborative filtering chỉ dựa trên item ID
  rất thưa. Cần khai thác đặc điểm món đồ hoặc mô hình lai.
- Chia theo thời gian: ví dụ 10 lượt đầu để train, 2 lượt cuối để test cho mỗi người;
  mọi bước học feature/profile phải chỉ dùng tập train.
- Khi đánh giá ranking trong quá khứ, phải tái dựng danh sách món còn mua được tại thời điểm đó.
  Không dùng catalogue ON_SALE hiện tại làm candidate pool rồi yêu cầu tìm lại các món test đã SOLD.
- Ba người mới dành cho kiểm tra cold start/fallback, không giả vờ rằng model đã học được sở thích của họ.

Đây là **dữ liệu mô phỏng có quy luật do mình thiết kế**, không phải bằng chứng mô hình
hiểu phong cách người dùng thật. Script chỉ chuẩn bị dữ liệu, chưa huấn luyện hay triển khai model.

## Kiểm tra script

`MlPurchaseSeedTest` chạy phần INSERT hai lần trên schema thật do Hibernate tạo trong H2
ở chế độ tương thích PostgreSQL; kiểm tra số lượng, khóa ngoại, tổng tiền, thời gian,
mỗi món chỉ bán một lần, mật khẩu đăng nhập và hai truy vấn export.
Chưa chạy script trên PostgreSQL của người dùng; transaction/session controls của PostgreSQL
không được kiểm chứng bởi H2.
