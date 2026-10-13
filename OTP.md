# Xác thực OTP qua email

Đăng ký và đăng nhập chỉ dùng `email` và `password`.
Entity tài khoản là `Account`, lưu trong bảng `accounts` với ID UUID. Nếu đang dùng
database của bản JWT demo cũ (`users.id` dạng số), dừng ứng dụng, sao lưu database
và chạy [scripts/migrate-users-to-accounts.sql](scripts/migrate-users-to-accounts.sql)
trước khi khởi động bản mới. Script giữ bảng `users` cũ, chuyển tài khoản và cập nhật
liên kết refresh token/OTP sang UUID; chạy một lần khi chưa có bảng `accounts`.
`ddl-auto: update` không tự chuyển dữ liệu từ `users` sang `accounts`.
Với database mới không cần migration. JWT cũ có subject là username cần được thay
bằng token mới qua đăng nhập email.

OTP gồm 6 chữ số, hết hạn sau 5 phút, dùng một lần. Chờ 60 giây giữa các lần gửi;
nhập sai tối đa 5 lần cho mỗi mã. Gửi lại sẽ thay mã cũ và vô hiệu hóa resetToken cũ.
Mã và resetToken được băm trước khi lưu vào PostgreSQL, không trả OTP qua API.
Không cần Redis. `ddl-auto: update` tạo bảng `email_otps` và `accounts`.
Tài khoản đã có được giữ trạng thái xác minh; tài khoản đăng ký mới phải xác minh email.

## Cấu hình email

Máy local đã có `config/mail-local.yml` lấy phần SMTP và địa chỉ gửi từ
`PRM/src/main/resources/application-dev.yaml`. Ứng dụng tự đọc file này khi chạy
từ thư mục gốc project. File chứa thông tin đăng nhập mail và đã được thêm vào `.gitignore`.
Cấu hình local này ưu tiên hơn các giá trị mail trong `application.yml`;
muốn dùng các biến môi trường bên dưới, hãy bỏ file local hoặc đổi giá trị trong file
thành placeholder biến môi trường. Khởi động lại ứng dụng sau khi đổi cấu hình.

Đặt biến môi trường cho tiến trình chạy ứng dụng (hoặc trong Run Configuration của IDE):

```powershell
$env:MAIL_HOST="smtp.gmail.com"
$env:MAIL_PORT="587"
$env:MAIL_USERNAME="your-email@gmail.com"
$env:MAIL_PASSWORD="your-app-password"
$env:MAIL_FROM="your-email@gmail.com"
mvn spring-boot:run
```

Dùng thông tin SMTP của bạn; không đưa mật khẩu thật vào source code.
Có thể đổi `MAIL_SMTP_AUTH` và `MAIL_STARTTLS` khi dùng SMTP thử nghiệm.
Điều chỉnh thời hạn, thời gian chờ và số lần thử tại `app.otp` trong `application.yml`.

## Đăng ký và xác minh

Các request sau đều dùng `POST`, `Content-Type: application/json`.
Có thể thử tại http://localhost:8080/swagger-ui.html.

1. `/api/auth/register`

```json
{"email":"your-email@gmail.com","password":"secret123"}
```

Trả HTTP 201 với `message`, **không còn trả accessToken/refreshToken khi đăng ký**.
Frontend cần chuyển sang màn hình nhập OTP.

2. `/api/auth/verify-otp`

```json
{"email":"your-email@gmail.com","purpose":"REGISTER","otp":"123456"}
```

Thay `123456` bằng mã trong email. Thành công thì gọi `/api/auth/login` với email/password
để nhận JWT như trước. Đăng nhập trước xác minh trả HTTP 403.

3. Gửi lại mã: `/api/auth/resend-otp`

```json
{"email":"your-email@gmail.com","purpose":"REGISTER"}
```

## Quên mật khẩu

1. Gửi mã bằng `/api/auth/resend-otp`:

```json
{"email":"your-email@gmail.com","purpose":"FORGOT_PASSWORD"}
```

2. Xác minh bằng `/api/auth/verify-otp`:

```json
{"email":"your-email@gmail.com","purpose":"FORGOT_PASSWORD","otp":"123456"}
```

Kết quả gồm `message` và `resetToken`. Token có hiệu lực 5 phút, chỉ dùng cho đặt lại mật khẩu.

3. `/api/auth/reset-password`:

```json
{"email":"your-email@gmail.com","resetToken":"token-from-step-2","newPassword":"newSecret123","confirmPassword":"newSecret123"}
```

Thành công sẽ vô hiệu hóa resetToken và xóa refresh token của tài khoản.
Access token đã cấp vẫn còn hiệu lực tới thời điểm hết hạn (mặc định 15 phút).

## Lỗi và kiểm thử

- 400: dữ liệu/mã/token không hợp lệ, hết hạn, đã dùng, hoặc vượt số lần nhập sai.
- 429: gửi lại quá sớm.
- 503: gửi email thất bại; thay đổi trong lần gửi đó được rollback.
- Gửi mã cho email không phù hợp vẫn trả thông báo chung, không cấp mã/token.

`mvn test` chạy kiểm thử tích hợp với H2 và mail sender giả lập. Cần cấu hình SMTP thực
để kiểm tra email đến hộp thư; các bài kiểm thử không gửi email thật.
