# Lịch sử kho và chỉnh danh mục/thương hiệu khi nhập kho

Hai API hiện có được bổ sung field; không tạo endpoint mới và không cần migration DB.

## 1. GET /moderator/warehouse-history

Giữ nguyên query `type=PACKING|INSPECTION|INVENTORY`, `page`, `size` và quyền Moderator.
Mỗi phần tử trong `result.content` trả thêm hai chuỗi lấy nguyên từ AuditLog:

| Field | Ý nghĩa |
| --- | --- |
| `target` | Chuỗi hiển thị cũ, vẫn dùng mã dễ đọc khi audit có `detail.label`. |
| `targetType` | Loại bản ghi: ví dụ `ORDER`, `CONSIGNMENT_FORM`, `PRODUCT`. |
| `targetId` | ID gốc: UUID với order/form; SKU với product. |

Ví dụ một phần tử:

```json
{
  "target": "ORDER OD-2026-000019",
  "targetType": "ORDER",
  "targetId": "11111111-1111-4111-8111-111111111111",
  "action": "SCAN_SKU",
  "actor": "Nguyễn Văn A",
  "timestamp": "2026-11-25T08:00:00Z",
  "result": "SUCCESS"
}
```

FE tiếp tục hiển thị `target`, chọn màn hình theo `targetType` và truyền `targetId`
vào URL/API chi tiết. Ví dụ order phía trên dùng
`/moderator/packing?orderId=11111111-1111-4111-8111-111111111111`
và `GET /orders/11111111-1111-4111-8111-111111111111`.
Không tách `OD-2026-000019` từ `target` để làm orderId.

DTO vẫn không có `detail`; JSON audit nội bộ không được gửi cho FE. Khi không có
label hợp lệ, `target` vẫn có dạng `<targetType> <targetId>` như trước.

## 2. POST /products

Moderator nhập kho từ biên nhận đã xác nhận, đúng kho và chưa có sản phẩm.
Request nhận thêm hai UUID tùy chọn `categoryId` và `brandId`:

```json
{
  "receiptId": "22222222-2222-4222-8222-222222222222",
  "name": "Quần jean xanh",
  "categoryId": "33333333-3333-4333-8333-333333333333",
  "brandId": "44444444-4444-4444-8444-444444444444",
  "size": "M",
  "photoUrls": ["https://example.com/product.jpg"]
}
```

Các UUID và URL trong ví dụ là dữ liệu minh họa; khi thử cần thay bằng dữ liệu đang tồn tại.

| Giá trị gửi lên | Xử lý |
| --- | --- |
| Bỏ field hoặc gửi `null` | Dùng danh mục/thương hiệu trên ConsignmentForm. |
| Trùng ID trên ConsignmentForm | Dùng lại entity trên form, không query CategoryRepository/BrandRepository. |
| ID khác, tồn tại trong DB | Gán entity được chọn vào Product. |
| Category ID khác, không tồn tại | `CATEGORY_NOT_FOUND`; chưa cấp SKU hoặc lưu sản phẩm/ảnh. |
| Brand ID khác, không tồn tại | `BRAND_NOT_FOUND`; chưa cấp SKU hoặc lưu sản phẩm/ảnh. |

Hai lựa chọn hoạt động độc lập: có thể chỉ sửa danh mục hoặc chỉ sửa thương hiệu.
SKU được sinh bằng danh mục cuối cùng; ví dụ chọn danh mục có code `QN` ở kho `Q7`
thì SKU mang tiền tố `Q7-QN-`. `tryonSlot` trong API chi tiết sản phẩm cũng lấy từ
danh mục cuối cùng này. Form ký gửi gốc được giữ nguyên.

Response nhập kho vẫn trả HTTP 201 với ProductResponse hiện có (`id`, `name`, `status`).
FE mở khóa hai trường lựa chọn và gửi các ID tương ứng khi bấm nhập kho.

## Kiểm tra hồi quy

- WarehouseHistoryServiceImplTest: UUID order/form, SKU product, giữ chuỗi hiển thị,
  fallback khi không có label, kiểm tra JSON không lộ detail.
- ProductServiceImplTest: sửa cả hai hoặc từng field, SKU và tryonSlot theo danh mục mới,
  request cũ bỏ field, ID trùng không query thêm, ID không tồn tại không lưu dữ liệu.
- ProductIntakeGuardTest và ProductCatalogTransactionTest: kiểm tra các điều kiện nhập kho
  và phạm vi transaction đọc danh sách sản phẩm hiện có.
