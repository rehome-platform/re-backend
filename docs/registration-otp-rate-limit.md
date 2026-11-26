# Sửa đăng ký và giới hạn OTP

## API bị ảnh hưởng

Không thêm endpoint, không thay đổi request/response DTO.

| API | Thay đổi |
| --- | --- |
| `POST /api/auth/register` | Kiểm duyệt họ tên, kiểm tra email/SĐT trùng trước khi tính lượt OTP. |
| `POST /api/organizations/register/account` | Cùng thứ tự kiểm tra cho đăng ký tài khoản tổ chức. |
| `POST /api/auth/resend-otp` | Xử lý IP qua proxy; lỗi giới hạn/cooldown kèm thời gian chờ. |
| `POST /api/auth/password/forgot` | Xử lý IP qua proxy; lỗi giới hạn kèm thời gian chờ. Giữ phản hồi không tiết lộ email có tồn tại hay không. |

## Thứ tự đăng ký

1. Validate DTO tại controller.
2. `moderation.evaluate(request.getFullName())`: từ có action `AUTO_REJECT` trả `400 CONTENT_VIOLATION`, không ghi audit vì chưa có accountId.
3. Kiểm tra email tồn tại: trả `409 EMAIL_ALREADY_EXISTS`.
4. Chuẩn hóa SĐT theo logic hiện có rồi kiểm tra trùng: trả `409 PHONE_TAKEN`.
5. Kiểm tra/tính lượt OTP theo IP và email.
6. Tạo tài khoản, hồ sơ/ví Member nếu cần, gửi OTP như trước.

Các request bị từ chối ở bước 2–4 không gọi limiter, không trừ quota IP/email và không tạo tài khoản. Controller chỉ chuyển IP vào service, không tính lượt hai lần. Giữ overload service một tham số để tương thích các caller hiện có; khi không có IP vẫn áp dụng quota email.

## Thông báo thời gian chờ

`OtpException` dùng `retryAfterSeconds` để thêm thời gian vào `message`:

```text
Bạn đã yêu cầu gửi email xác thực quá nhiều lần. Vui lòng thử lại sau 12 phút.
```

Status vẫn `429`, header vẫn là số giây `Retry-After: 720`. Nếu còn 65 giây thì thông báo là `1 phút 5 giây`; dưới một phút hiển thị số giây. Cooldown gửi lại OTP cũng trả thời gian còn lại, làm tròn lên giây.

## IP qua Render

Đã thêm trong `src/main/resources/application.yml`:

```yaml
server:
  forward-headers-strategy: framework
```

Spring xử lý forwarded headers trước controller; các API vẫn dùng `getRemoteAddr()` thay vì tự tách header tại từng nơi. Cấu hình này dành cho ứng dụng phía sau reverse proxy tin cậy có xử lý forwarded headers. Cần deploy lại BE để áp dụng trên Render.

## Dễ kiểm tra trên môi trường test

Production mặc định giữ 3 lượt/email, 30 lượt/IP trong 900 giây. Profile mới `otp-test` dùng 10 lượt/email trong 300 giây, IP vẫn 30 lượt trong cùng cửa sổ.

Chọn một trong hai cách trên môi trường test:

- Bật `SPRING_PROFILES_ACTIVE=otp-test`; nếu đang dùng profile khác thì thêm `otp-test` vào danh sách profile.
- Hoặc dùng biến môi trường hiện có trên Render/IntelliJ hoặc trong `.env`:

```dotenv
OTP_RATE_LIMIT_EMAIL=10
OTP_RATE_LIMIT_WINDOW_SECONDS=300
```

Biến môi trường trên vẫn ưu tiên hơn mặc định của profile. Khởi động lại BE sau khi đổi cấu hình. Bucket Redis đã tồn tại giữ TTL cũ đến khi hết hạn; không cần xóa toàn bộ Redis.

## Kiểm tra hồi quy trên Windows

```powershell
mvn '-Dtest=AuthServiceImplTest,OtpServiceImplTest,OtpRateLimiterTest,OtpExceptionTest,RegistrationAndOtpHttpTest,UnitWorkbookValidationTest' test
```

Các trường hợp được kiểm tra: cả hai loại tài khoản trùng email/SĐT không tiêu tốn quota, kiểm duyệt họ tên trước tạo tài khoản, quota chỉ gọi một lần sau khi hợp lệ, mã lỗi cho mobile, định dạng thời gian chờ và `Retry-After`, IP client khác nhau qua `X-Forwarded-For`, request local không có proxy và lỗi Redis.

Unit test mock repository/Redis/mail; test HTTP chạy Tomcat cục bộ với service mock để xác minh controller, forwarded-header filter và exception handler. Không gửi email thật hoặc gọi deployment Render.

Kết quả tại repo Windows ngày 26/11/2026: **127 test pass, 0 failure, 0 error, 0 skipped; BUILD SUCCESS**.
