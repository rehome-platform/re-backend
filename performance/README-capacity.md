# Kiểm thử catalogue đến 10.000 user

Profile `capacity` dùng sau khi đã kiểm thử các mức thấp hơn. Chỉ gọi ba biến thể
`GET /products` trên HTTP loopback; không tạo đơn, thanh toán hoặc ảnh AI.

```powershell
$env:LOAD_TEST_TOKEN = Read-Host "Dan access token moi, khong kem Bearer"
New-Item -ItemType Directory -Force performance/results | Out-Null
.\performance\.venv\Scripts\python.exe -m locust -f performance/locustfile.py --host http://127.0.0.1:8080 --headless --load-profile capacity --max-users 10000 --csv-full-history --csv performance/results/capacity-10000-authenticated --html performance/results/capacity-10000-authenticated.html
```

Lấy token mới ngay trước khi chạy. Token dùng chung cho mọi user, không ghi vào source.
Nếu không đặt token, bài test là catalogue công khai và không đo chi phí xác thực JWT.

| Thời gian kể từ bắt đầu | User mục tiêu | Tốc độ thay đổi |
|---|---:|---:|
| 0–90 giây | 1.000 | 200 user/giây |
| 90–180 giây | 2.000 | 200 user/giây |
| 180–270 giây | 5.000 | 200 user/giây |
| 270–360 giây | 10.000 | 200 user/giây |
| 360–420 giây | 3, kiểm tra phục hồi | 1.000 user/giây |

Thời gian mỗi nấc bao gồm cả tăng tải. Đây không phải 90 giây giữ nguyên toàn bộ số user.
Locust chờ request đang chạy kết thúc khi giảm tải/dừng, nên thực tế có thể lâu hơn.
`capacity --max-users 3000` chạy các mức 1.000, 2.000, 3.000 rồi phục hồi.
Profile `stress` vẫn bắt đầu từ 20; với giới hạn 10.000 sẽ chạy khoảng 14 phút 30 giây.
Giới hạn hợp lệ hiện tại là 1–10.000, smoke mặc định không thay đổi.

User mô phỏng nghỉ 0,5–1,5 giây giữa request. 10.000 user không có nghĩa 10.000 request/giây,
10.000 tài khoản khác nhau hoặc 10.000 request đồng thời.
Cache key có người xem; dùng chung tài khoản có thể cho tỷ lệ dùng chung cache cao hơn thực tế.

Đọc cả số user thực tế, lỗi, p95/p99 và RPS theo từng giai đoạn trong CSV history.
RPS toàn bài gồm cả các giai đoạn nhẹ và request lỗi. Không dùng nó như công suất tại 10.000 user.
Theo dõi CPU/RAM của Java và Locust. Cảnh báo CPU Locust, thiếu socket/port hoặc không đạt
số user mục tiêu có thể cho thấy giới hạn máy phát tải, không đủ để kết luận BE hết công suất.
Giữ nguyên timeout kết nối 2 giây/đọc 5 giây để lỗi không bị che bởi tăng timeout.

Mở báo cáo sau khi chạy:

```powershell
Start-Process .\performance\results\capacity-10000-authenticated.html
```

Kiểm tra lịch tăng tải mà không gửi HTTP:

```powershell
.\performance\.venv\Scripts\python.exe -m unittest discover -s performance -p test_load_profiles.py -v
```
