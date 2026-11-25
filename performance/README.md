# Kiểm thử tải catalogue trên local

Additional profiles: [10,000 users](README-capacity.md), [2,200/2,500 diagnostics](README-diagnostics.md).

Các kịch bản chỉ gọi `GET /products`, không tạo đơn, thanh toán hoặc gọi GPU.
Chạy backend bằng database test có dữ liệu đại diện, Redis test riêng và tắt SQL debug khi đo.
Không dùng kết quả H2/unit test để công bố khả năng chịu tải production.

## Locust: baseline → spike → recovery

```powershell
python -m venv performance/.venv
performance/.venv/Scripts/python -m pip install -r performance/requirements.txt
New-Item -ItemType Directory -Force performance/results | Out-Null
# Nếu endpoint yêu cầu đăng nhập, đặt LOAD_TEST_TOKEN trong môi trường của terminal.
performance/.venv/Scripts/python -m locust -f performance/locustfile.py --host http://127.0.0.1:8080 --headless --csv performance/results/catalogue --html performance/results/catalogue.html
```

Shape cố định: 3 người dùng trong 15 giây, 20 trong 10 giây, về 3 trong 20 giây rồi dừng.
Mỗi người nghỉ 0,5–1,5 giây, timeout kết nối 2 giây/đọc 5 giây.
Chỉ chấp nhận HTTP loopback. Script kiểm tra cả HTTP 200 và cấu trúc JSON.
Token lấy từ biến môi trường, không ghi token vào file hoặc source.

## Stress test tăng đến 1.000 user

Giữ nguyên smoke test mặc định. Chọn stress bằng `--load-profile stress --max-users 1000`:

```powershell
$env:LOAD_TEST_TOKEN = Read-Host "Dan access token moi, khong kem Bearer"
New-Item -ItemType Directory -Force performance/results | Out-Null
.\performance\.venv\Scripts\python.exe -m locust -f performance/locustfile.py --host http://127.0.0.1:8080 --headless --load-profile stress --max-users 1000 --csv-full-history --csv performance/results/stress-1000 --html performance/results/stress-1000.html
```

| Khoảng thời gian | User mục tiêu |
|---|---:|
| 0–90 giây | 20 |
| 90–180 giây | 50 |
| 180–270 giây | 100 |
| 270–360 giây | 200 |
| 360–450 giây | 500 |
| 450–540 giây | 1.000 |
| 540–600 giây | 3 — kiểm tra phục hồi |

Tăng tối đa 20 user/giây; thời gian mỗi nấc bao gồm giai đoạn tăng user, không phải 90 giây
giữ nguyên số user. Giảm về 3 với tốc độ 100 user/giây. Sau 10 phút tự dừng; Ctrl+C để dừng sớm.
Có thể thử trước `--max-users 100` hoặc `200`: profile bỏ các nấc lớn hơn giới hạn rồi phục hồi.
Nếu giới hạn không trùng các nấc có sẵn, script thêm nấc đó. Giới hạn cho phép là 1–1.000.
Không dùng `-u/--users` để thay đổi user trong load shape này.

Lấy token mới trước bài test 10 phút vì access token hiện có hạn 15 phút, script chưa tự refresh.
HTTP 401 do token hết hạn không phải bằng chứng BE hết khả năng chịu tải.
Các user dùng chung token và chỉ chạy ba kiểu GET catalogue; không phải 1.000 tài khoản đăng nhập
hoặc 1.000 request đang xử lý đồng thời. Có nghỉ 0,5–1,5 giây giữa các request.

Để tìm ngưỡng chịu tải, đặt tiêu chí trước (ví dụ p95 dưới 1 giây, lỗi dưới 1%), xem dữ liệu
từng giai đoạn qua chart/`stress-1000_stats_history.csv`, không chỉ dựa vào p95 chung cả bài.
Nếu lỗi/độ trễ tăng mạnh từ một nấc, chạy lại quanh nấc đó với thời gian giữ tải dài hơn trước
khi kết luận. Nếu vẫn ổn ở 1.000, chỉ kết luận đạt bài test này, chưa tìm thấy mức tối đa.

Theo dõi CPU/RAM cả Java lẫn Python, connection pool PostgreSQL, Redis và log timeout.
BE và Locust chung máy có thể tranh CPU/RAM; máy phát tải cũng có thể là nút thắt.
Không suy ra giới hạn production từ bài test loopback. Chưa chạy chế độ 1.000 user tự động.
Các endpoint ghi dữ liệu, thanh toán, ML và gen ảnh chưa nằm trong bài test này.

Kiểm tra cấu hình shape mà không gửi HTTP:

```powershell
.\performance\.venv\Scripts\python.exe -m unittest discover -s performance -p test_load_profiles.py -v
```

## JMeter: spike nhỏ có giới hạn

Cài JMeter trước, tạo thư mục kết quả, chạy chế độ CLI:

```powershell
New-Item -ItemType Directory -Force performance/results | Out-Null
jmeter -n -t performance/catalogue-spike.jmx -Jport=8080 -Jusers=10 -Jduration=30 -l performance/results/spike.jtl -e -o performance/results/jmeter-report
```

Host được cố định `127.0.0.1`. Giới hạn 20 threads, 60 giây, nghỉ 500 ms,
timeout 2/5 giây; mặc định 10 threads/30 giây. Assertion yêu cầu HTTP 200.
Thư mục `jmeter-report` phải rỗng/chưa tồn tại khi xuất report mới.
Plan JMeter dành cho catalogue công khai; dùng Locust với `LOAD_TEST_TOKEN` nếu cấu hình local chặn truy cập ẩn danh.

Đây là kiểm thử tải/spike có giới hạn, **không phải kiểm thử DDoS phân tán**.
Không suy ra khả năng chống DDoS từ một máy test. Backend hiện chưa bổ sung rate limit
catalogue trong thay đổi này; nếu có giới hạn ở gateway thì ghi riêng số HTTP 429.
Không tính 429/401/5xx thành request thành công.

## Cách thu kết quả cho báo cáo

1. Ghi commit, CPU/RAM, phiên bản Java/PostgreSQL/Redis, số sản phẩm/ảnh/người bán, cấu hình pool.
2. Chạy cùng dataset với `PRODUCTS_CACHE_ENABLED=false`, khởi động lại; chạy Locust, lưu báo cáo riêng.
3. Bật cache, khởi động lại, gọi trước cùng bộ filter để warm, chạy lại cùng shape.
4. Ghi requests/giây, p50/p95/p99, lỗi HTTP/timeout, CPU/RAM, số connection DB và số query.
5. Quan sát giai đoạn recovery: độ trễ/lỗi có trở về baseline không. Chạy lại nếu cần để kiểm tra tính lặp lại.
6. Đổi giá/trạng thái sản phẩm trên test và gọi lại: kiểm tra cache invalidation, không chỉ đo tốc độ.

Mục tiêu đề xuất cho buổi demo local: không 5xx/timeout, p95 dưới 1 giây sau warm-up.
Đây là tiêu chí cần đo, chưa phải số liệu đã đạt.

| Lần đo | Cache | Dataset | RPS | p95 | p99 | Lỗi | Recovery |
|---|---|---|---|---|---|---|---|
| 25/11 smoke 20 user, code trước tối ưu transaction | Chưa xác nhận hit rate | Local của người dùng | 4,57 | 770 ms | 2.000 ms | 0/206 | Độ trễ về gần baseline |
| 25/11 stress đến 1.000 user, code trước tối ưu transaction | Chưa xác nhận hit rate | Local của người dùng | 66,67 tổng | 5.000 ms | 6.100 ms | 34.821/39.968 | Chưa có request thành công trong phần recovery ghi nhận |

Đây là số liệu từ báo cáo/CSV người dùng đã chạy. RPS tổng bao gồm lỗi;
stress test chỉ có 5.147 request thành công, khoảng 8,6 request thành công/giây.
Nhãn HTTP 0 trong lần đó đã che mất request exception gốc; lần đo tiếp cho thấy ReadTimeout 5 giây.
Chưa có kết quả tải sau tối ưu transaction/coalescing bên dưới; chưa chạy JMeter.

## Tối ưu sau khi phát hiện ReadTimeout

- `ProductServiceImpl.getProducts` không mở/giữ transaction quanh Redis nữa.
  `ProductCatalogReader.load` mở transaction read-only riêng, chỉ cho query và map DTO.
  Cache hit không cần transaction DB từ service catalogue (xác thực JWT vẫn có lookup tài khoản riêng).
- Các request trùng cache key/generation đang xử lý được gộp trong một JVM: một request đọc page/load DB,
  các request khác chờ cùng kết quả. Không giữ kết quả trong memory cache sau khi nhóm đó hoàn thành.
- Invalidation sau commit vẫn đổi generation; request mới không nhập nhóm đang đọc generation cũ.
  Không tự tăng connection pool, không kéo dài timeout, không cache trạng thái tài khoản/bỏ kiểm tra ban.
- `ProductCatalogTransactionTest` dùng Spring transaction thật + JDBC H2 để kiểm tra Redis không giữ
  JDBC resource, còn phần reader có transaction. Test concurrency kiểm tra gộp request và invalidation.

Khởi động lại BE để nạp thay đổi; thử Swagger/45 giây smoke trước, rồi đo lại đến 200 user với token mới:

```powershell
$env:LOAD_TEST_TOKEN = Read-Host "Dan access token moi, khong kem Bearer"
.\performance\.venv\Scripts\python.exe -m locust -f performance/locustfile.py --host http://127.0.0.1:8080 --headless --load-profile stress --max-users 200 --csv-full-history --csv performance/results/after-catalog-fix-200 --html performance/results/after-catalog-fix-200.html
```

Giữ dataset/cấu hình cache/máy như lần trước. So sánh p95 và lỗi từng nấc, không so RPS tổng gồm lỗi
với throughput thành công. Theo dõi CPU/RAM Java và Python; nếu vẫn timeout, thu log BE lúc test.
Có thể bật tạm `logging.level.com.zaxxer.hikari.pool.HikariPool=DEBUG` để xem pool active/idle/waiting;
tránh bật log toàn bộ SQL khi đo. Các thay đổi code không đủ để khẳng định nguyên nhân duy nhất
hoặc cam kết 1.000 user; cần kết quả đo lại và kiểm tra DB/Redis/máy phát tải.
