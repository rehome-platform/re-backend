# Đơn đã hoàn trả, hộp thư và phí rút hàng tại quầy

## Đơn hàng cũ đã hoàn trả

`OrderItemRepository.findActiveByProductIds` loại cả `CANCELLED` và `RETURNED`. Các trạng thái khác vẫn được giữ nguyên. Query này dùng chung cho:

- `PUT /products/{id}`: sản phẩm đã đăng bán lại không bị chặn chỉ vì có đơn cũ đã hoàn trả. Service có thêm `blocksProductUpdate` để bỏ qua hai trạng thái trên và vẫn chặn khi có đơn mới đang hoạt động.
- `GET /products/mine` và `GET /products/mine/{id}`: khi gắn đơn cho hàng `RESERVED`/`SOLD`, không lấy đơn cũ `RETURNED`.
- `POST /orders/{id}/pack/scan`: `wrongItem.belongsToOrderNo` chỉ tham chiếu đơn còn được query coi là hoạt động; không lấy mã đơn đã hoàn trả.
- `POST /products/{id}/donate`: bỏ đơn đã hoàn trả ngay từ query; giữ nguyên các điều kiện nghiệp vụ quyên góp còn lại.

Request/response và schema database không thay đổi.

## Hộp thư Moderator

`GET /conversations` và `GET /conversations/{id}` xử lý được Member có `accounts.phone = null`, vẫn trả `phone: null`. Số lượng chưa đọc, ký gửi, giao hàng và trả hàng được chuyển từ `Number` sang `long`; bỏ qua dòng thống kê không có UUID/count hợp lệ. Hội thoại thiếu mốc hoạt động được xếp cuối, và tin cuối thiếu người gửi trả `senderId: null`.

JPQL thống kê trả hàng dùng `join r.order o` tường minh để dùng cùng alias trong SELECT, WHERE và GROUP BY. Các truy vấn thống kê vẫn thực hiện theo lô.

## Bàn giao hàng rút: phí chỉ thanh toán trên app

Khi `feeCharged > 0` và `feePaidAt == null`, BE chặn bàn giao tại quầy:

```json
{
  "result": null,
  "isSuccess": false,
  "statusCode": 409,
  "code": "WITHDRAWAL_FEE_NOT_PAID",
  "message": "Member chưa trả phí rút hàng. Nhắc Member trả trên app ReHome trước khi bàn giao."
}
```

Yêu cầu vẫn `PENDING_PICKUP`, không ghi `completedAt`, không lưu thay đổi bàn giao và không dequeue. Member thanh toán trên app rồi quầy tải lại thông tin để bàn giao. Phí bằng 0 không cần `feePaidAt`; phí đã thanh toán vẫn bàn giao bình thường. Giữ nguyên cấu hình hạn trả phí 15 phút và job tự huỷ yêu cầu quá hạn.

## Log lỗi 500

`GlobalExceptionHandler.handleGeneral` ghi SLF4J ERROR gồm HTTP method, URI, thông báo và stack trace. HTTP 500 vẫn giữ body `ApiError` hiện tại.

## Kiểm tra thủ công sau khi restart BE

1. Sản phẩm đã RELIST, chỉ có đơn cũ `RETURNED`: cập nhật tên/ảnh/danh mục thành công.
2. Cùng sản phẩm có thêm đơn mới `PAID`: cập nhật vẫn trả `PRODUCT_UPDATE_CONFLICT`.
3. Moderator mở hộp thư có Member chưa có SĐT: danh sách và chi tiết trả thành công, `phone` là null.
4. Xác nhận danh tính rồi bàn giao yêu cầu có phí chưa trả: nhận 409 `WITHDRAWAL_FEE_NOT_PAID`, yêu cầu và hàng đợi giữ nguyên.
5. Thanh toán phí thành công trên app, tải lại và bàn giao: yêu cầu chuyển `COMPLETED`, việc được dequeue.

Không cần migration database.
