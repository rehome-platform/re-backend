# Bảng thông số kiểm thử tải GET /products — 25/11/2026

Đích kiểm thử: `http://127.0.0.1:8080`. Công cụ: Locust. Các tác vụ gồm lấy trang catalogue,
tìm kiếm `ao`, và phân trang; thời gian nghỉ giữa request 0,5–1,5 giây, timeout kết nối 2 giây,
timeout đọc 5 giây. Các user mô phỏng dùng chung token. Không kiểm thử đặt hàng, thanh toán hoặc tạo ảnh AI.

## Thông số toàn bài test

Mỗi dòng là một bài tăng dần đến số user tối đa tương ứng, gồm tăng tải và phục hồi.
Đây không phải thông số riêng lúc giữ số user cao nhất. Với 2.500 user, chọn lượt mới nhất
`diagnostic-20261125-135758-2500`; các lượt 2.500 trước đó được ghi riêng bên dưới.
Nguồn: dòng `Aggregated` trong `_stats.csv`. RPS tổng gồm cả request lỗi.

| User tối đa | Tổng request | Số lỗi | Tỷ lệ lỗi | Trung bình (ms) | P95 (ms) | P99 (ms) | RPS tổng | RPS thành công |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 200 | 24.254 | 0 | 0% | 374,65 | 470 | 500 | 57,68 | 57,68 |
| 500 | 56.032 | 0 | 0% | 371,27 | 470 | 490 | 109,90 | 109,90 |
| 1.000 | 97.898 | 0 | 0% | 676,43 | 1.400 | 1.500 | 163,19 | 163,19 |
| 2.000 | 77.452 | 0 | 0% | 2.519,67 | 3.900 | 4.100 | 323,25 | 323,25 |
| 2.200 | 119.165 | 0 | 0% | 2.952,38 | 4.200 | 4.400 | 361,65 | 361,65 |
| 2.500 | 120.083 | 4.230 | 3,52% | 3.142,12 | 5.000 | 5.000 | 363,95 | 351,13 |
| 3.000 | 121.810 | 43.521 | 35,73% | 3.440,48 | 5.100 | 5.200 | 369,63 | 237,57 |

Các profile có lịch tăng tải, thời lượng và thời điểm chạy khác nhau; SQL logging/công cụ đo cũng được
điều chỉnh giữa các lượt. Không dùng bảng này như thí nghiệm kiểm soát để quy cải thiện cho riêng một thay đổi.
RPS và percentile là của kịch bản/dataset hiện tại, không đại diện cho mọi API hay hạ tầng production.

## Từng nấc trong lượt 2.500 user mới nhất

Nguồn: `_summary.json` tính từ `_stats_history.csv`. Đây là số request hoàn tất trong từng khoảng;
một số có thể bắt đầu từ khoảng trước. Ranh giới nấc xấp xỉ theo mẫu CSV đầu tiên.

| Nấc tải | Request hoàn tất | Số lỗi | Tỷ lệ lỗi | Trung bình (ms) | RPS hoàn tất | RPS thành công |
|---|---:|---:|---:|---:|---:|---:|
| 1.000 user | 39.028 | 0 | 0% | 1.218,69 | 438,52 | 438,52 |
| 2.000 user | 37.525 | 0 | 0% | 3.585,46 | 416,94 | 416,94 |
| 2.500 user | 40.392 | 3.586 | 8,88% | 4.474,14 | 448,80 | 408,96 |
| Giảm tải/phục hồi | 3.137 | 644 | 20,53% | 4.618,41 | 52,28 | 41,55 |

Trong 20 giây cuối: 42 request hoàn tất, 0 lỗi; P95 của cửa sổ cuối khoảng 610 ms.
Lỗi giai đoạn phục hồi có thể gồm request tồn từ giai đoạn tải cao. Không suy từ tỷ lệ lỗi toàn giai đoạn
phục hồi rằng hệ thống vẫn lỗi đến cuối bài. Cả 4.230 lỗi của lượt này là `ReadTimeout`.

Tổng từ history/JSON là 120.082, còn `_stats.csv` là 120.083; các bộ đếm được ghi ở thời điểm khác nhau
khi đang kết thúc lượt. Không trộn hai nguồn để tính lại một tỷ lệ.

## Pool DB theo từng nấc

Nguồn: `database_pool` trong JSON của cùng lượt, 66 mẫu cách nhau khoảng 5 giây.

| Nấc tải | Số mẫu | Active lớn nhất | Idle nhỏ nhất | Waiting lớn nhất | Số mẫu có waiting > 0 |
|---|---:|---:|---:|---:|---:|
| 1.000 user | 18 | 1 | 9 | 0 | 0 |
| 2.000 user | 18 | 10 | 0 | 3 | 1 |
| 2.500 user | 18 | 2 | 8 | 0 | 0 |
| Phục hồi | 12 | 0 | 10 | 0 | 0 |

Pool tối đa 10 kết nối. Chưa thấy hàng chờ pool DB kéo dài ở nấc 2.500. Mẫu 5 giây có thể bỏ lỡ
đột biến ngắn; kết quả này không loại trừ mọi độ trễ liên quan DB. Thời gian Redis và luồng HTTP chưa được đo riêng.

## CPU và RAM khi giữ nấc 2.500 user

Nguồn: `_resources.csv`, các mẫu có `elapsed_seconds` từ 180 đến dưới 270.

| Chỉ số | Trung bình | Lớn nhất |
|---|---:|---:|
| CPU toàn máy | 18,83% | 33,20% |
| CPU Java, tỷ lệ trên toàn máy | 2,69% | 4,31% |
| CPU Locust, tỷ lệ trên toàn máy | 2,79% | 4,86% |
| RAM RSS Java | 733,44 MiB | 736,64 MiB |
| RAM RSS Locust | 249,91 MiB | 254,57 MiB |

RAM còn khả dụng trung bình: 11.200,28 MiB; thấp nhất 10.448,30 MiB.
Các số liệu lấy mẫu chưa cho thấy CPU/RAM bị dùng hết kéo dài.

## Biến động giữa các lượt 2.500 user

Không chọn riêng lượt tốt nhất để đại diện cho mức 2.500.

| Mã lượt | Lỗi riêng nấc 2.500 | Trung bình riêng nấc (ms) | RPS thành công riêng nấc |
|---|---:|---:|---:|
| diagnostic-20261125-115938-2500 | 1,05% | 4.643,79 | 432,90 |
| diagnostic-20261125-123617-2500 | 52,98% | 4.767,32 | 201,90 |
| diagnostic-20261125-125658-2500 | 0,21% | 4.540,66 | 441,28 |
| diagnostic-20261125-135758-2500 | 8,88% | 4.474,14 | 408,96 |

## Nguồn và giới hạn kết luận

- 200, 500, 1.000: `performance/results/restarted-<user>_stats.csv`.
- 2.000, 3.000: `performance/results/capacity-<user>_stats.csv`.
- 2.200: `performance/results/diagnostic-20261125-115938-2200_stats.csv`.
- 2.500: `performance/results/diagnostic-20261125-135758-2500_*`.
- Dùng token chung có thể làm khả năng dùng chung cache cao hơn trường hợp nhiều tài khoản riêng.
- Tên tác vụ `[repeat/cache]` không phải phép đo cache hit rate.
- Bài tăng đến 2.200 chưa có lỗi trong lượt ghi nhận, nhưng độ trễ đã cao. Không gọi đây là mức tải tốt cho mọi tiêu chí.
- Nấc 2.500 có lỗi và biến động giữa các lượt; 3.000 có lỗi cao. Chưa xác nhận nguyên nhân chính là Redis hay hàng đợi HTTP.
- Chưa có bài 10.000 user hoàn thành để đưa vào bảng.

CSV xuất kèm: `load-test-summary-2026-11-25.csv`, `load-test-latest-2500-stages-2026-11-25.csv`,
`load-test-latest-2500-pool-2026-11-25.csv`.
