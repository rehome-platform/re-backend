# Đo ngưỡng 2.200 và 2.500 user

## Bật log pool DB trước khi chạy

Trong IntelliJ Run Configuration, thêm biến môi trường sau vào tiến trình BE:

```text
APP_PERFORMANCE_DIAGNOSTICS_ENABLED=true
```

Stop rồi Run lại BE một lần để nạp `HikariPoolDiagnostics`. Mặc định chức năng tắt.
Sau khi chạy khoảng 5 giây, console BE có dòng:

```text
PERF_POOL timestamp=... active=... idle=... total=... waiting=... max=...
```

Sampler đọc bộ đếm Hikari trong bộ nhớ, không lấy thêm kết nối DB, không chạy SQL.
Không thay đổi kích thước pool, timeout, cache hoặc cơ chế xác thực. Không thêm API.
Số liệu tự lưu vào `performance/results/backend-pool-<PID Java>.csv` mỗi khoảng 5 giây.
Script test tự đọc file này, lọc theo đúng PID Java và thời gian bài test; không cần chép console.
Có thể đổi đường dẫn bằng `app.performance.diagnostics.output`, rồi truyền cùng đường dẫn qua
`--pool-csv` nếu không dùng mặc định.
Nếu BE chạy từ PowerShell thì phải đặt biến trước khi khởi động BE:

```powershell
$env:APP_PERFORMANCE_DIAGNOSTICS_ENABLED = "true"
```

Đặt biến trong terminal Locust không bật được sampler trên một BE đã chạy từ trước.
Khi đo xong, bỏ biến hoặc đặt `false` rồi restart BE để tắt.

Với `.env` đang được Spring Boot nạp dưới dạng properties, có thể đặt trực tiếp:

```properties
app.performance.diagnostics.enabled=true
spring.jpa.show-sql=false
logging.level.org.hibernate.SQL=OFF
```

Sau khi cập nhật code ghi CSV, Stop/Run lại BE và chờ ít nhất 5 giây để có mẫu mới.

## Chạy bài test có xác thực và ghi tài nguyên

Trong terminal tại thư mục repo, dùng token mới ngay trước khi chạy:

```powershell
$env:LOAD_TEST_TOKEN = Read-Host "Dan access token MOI, khong kem Bearer"
.\performance\.venv\Scripts\python.exe performance/diagnose_capacity.py --users 2200 2500
```

Mỗi bài chạy 1.000 → 2.000 → mức mục tiêu, 90 giây mỗi nấc, rồi 60 giây phục hồi.
Hai bài mất khoảng 11 phút. Script kiểm tra token còn đủ hạn cho mỗi bài và kiểm tra
HTTP 200 cùng payload trước khi tạo tải; không tự chuyển sang bài không đăng nhập.
Nếu token sắp hết, terminal tương tác sẽ yêu cầu token mới và ẩn nội dung khi nhập.
Nếu một bài có lỗi, script ghi kết quả rồi dừng trước mức cao hơn.

Các file trong `performance/results/diagnostic-<ngày-giờ>-<số-user>*` gồm:

- `.html`: báo cáo Locust.
- `.log`: log Locust và cảnh báo CPU/transport.
- `_stats_history.csv`: số user, RPS, lỗi và percentile theo thời gian.
- `_resources.csv`: CPU/RAM hệ thống, Java và Locust, lấy mẫu khoảng 2 giây.
- `_summary.json`: số request/lỗi/trung bình theo từng nấc và 20 giây cuối.

Script giám sát tài nguyên chạy ngoài tiến trình Locust. Tự tìm Java PID đang lắng nghe
cổng BE; nếu hệ điều hành không cho đọc, trường Java để trống, không giả thành 0.
Có thể chỉ định PID Java của BE từ Task Manager nếu cần:

```powershell
.\performance\.venv\Scripts\python.exe performance/diagnose_capacity.py --users 2200 2500 --java-pid 12345
```

Thay `12345` bằng PID thật. Nếu cần cài dependency mới:

```powershell
.\performance\.venv\Scripts\python.exe -m pip install --only-binary=:all: -r performance/requirements.txt
```

Script kiểm tra có mẫu pool trong 20 giây gần nhất trước khi tạo tải. Thiếu file/mẫu mới thì dừng
để kiểm tra BE thay vì chạy một bài dài không thu được dữ liệu DB.
Nếu PID không tự tìm được, dùng `--java-pid` hoặc chỉ rõ file qua `--pool-csv`.
Kết quả có thêm `_pool.csv` của riêng lượt đo và `database_pool` trong `_summary.json`.
Mỗi nấc có số mẫu, `active_max`, `idle_min`, `waiting_max`, số mẫu có luồng đang chờ.
Không có mẫu ở một nấc được ghi là `null`, không giả thành không có hàng chờ.

## Đọc kết quả

- `cpu_pct_one_core_100`: 100% tương đương một logical core dùng hết; có thể lớn hơn 100%.
- `cpu_pct_machine`: tỷ lệ CPU tiến trình trên toàn bộ số logical core.
- Locust chiếm gần một core và có cảnh báo CPU: cần kiểm tra giới hạn máy phát tải.
- Java CPU cao hoặc RAM tăng mạnh: kiểm tra xử lý/GC; chưa đủ bằng chứng kết luận riêng SQL.
- `PERF_POOL waiting` tăng kéo dài, `active` gần `max`: có hàng chờ lấy kết nối DB.
  Cần kiểm tra thời gian giữ kết nối và SQL trước khi tự tăng pool.
- Pool ít chờ nhưng request chậm: tiếp tục đo Redis, luồng xác thực và servlet threads.
- Sau giảm tải, xem lỗi và độ trễ 20 giây cuối để đánh giá phục hồi.

Các bộ đếm pool được đọc liên tiếp nên không phải snapshot nguyên tử. Scheduler bận có thể
làm khoảng cách giữa các dòng pool dài hơn 5 giây. RPS toàn bài không phải RPS riêng mức cao nhất.
Ranh giới nấc trong JSON xấp xỉ từ timestamp CSV; request hoàn tất ở một nấc có thể bắt đầu ở nấc trước.
Không cộng/trung bình các percentile của từng cửa sổ để suy ra percentile toàn giai đoạn.

Đọc lại CSV cũ mà không tạo tải hoặc cần token:

```powershell
.\performance\.venv\Scripts\python.exe performance/diagnose_capacity.py --summarize performance/results/capacity-3000 --users 3000
```
