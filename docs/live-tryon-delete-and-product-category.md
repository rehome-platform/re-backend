# Xoá video thử đồ và cập nhật danh mục sản phẩm

## DELETE /tryon/live/sessions/{id}

- Cần JWT role `MEMBER`; `id` là UUID phiên thử đồ.
- Chỉ tìm phiên bằng `findByIdAndMember_Id(id, accountId)`.
- Không có phiên hoặc phiên thuộc Member khác: HTTP 404, code `TRYON_LIVE_SESSION_NOT_FOUND`.
- Thành công: HTTP 200:

```json
{
  "result": null,
  "isSuccess": true,
  "statusCode": 200,
  "message": "Đã xoá video thử đồ"
}
```

BE đặt `videoUrl = null`, giữ nguyên dòng `live_tryon_sessions`, `firstFrameAt`, trạng thái và các mốc thời gian. Số lượt đã dùng trong ngày/tháng không giảm. Lịch sử `GET /tryon/live/sessions` loại phiên đã xoá vì query yêu cầu video URL khác null. Gọi xoá lại một phiên của chính mình đã không còn video vẫn trả 200.

Sau khi transaction commit thành công, BE thử xoá file bằng URL đã lưu thông qua `FileStorageService.deleteByUrl` (hỗ trợ MP4 và WebM của storage hiện tại). File thiếu hoặc lỗi storage chỉ được ghi log; lịch sử vẫn được xoá. Transaction rollback thì không xoá file. Không cần migration database.

## GET /inventory — MODERATOR

Mỗi `InventoryItemResponse` trả thêm:

| Field | Kiểu | Giá trị |
|---|---|---|
| `thumbnailUrl` | String, nullable | Ảnh đầu tiên theo thứ tự hiển thị; null nếu không có ảnh |
| `photos` | List of String | Tất cả ảnh theo `displayOrder`, rồi `id`; không có ảnh trả `[]` |
| `categoryId` | UUID | Danh mục hiện tại của sản phẩm |
| `categoryName` | String | Tên danh mục hiện tại |
| `tryonSlot` | TOP / BOTTOM / DRESS | Slot từ danh mục hiện tại |

Ảnh được lấy bằng một query `findByProduct_IdInOrderByDisplayOrderAscIdAsc` cho cả trang; trang trống không query ảnh. Query tồn kho nạp sẵn `category` và `consignmentReceipt` để không phát sinh truy vấn lazy theo từng dòng khi map response. Các field cũ được giữ nguyên.

## GET /products/{id} — MODERATOR

`id` là SKU. `ProductSkuLookupResponse` thêm `categoryId` (UUID) và `tryonSlot` (TOP / BOTTOM / DRESS). Các field chi tiết và kiểm tra kho giữ nguyên.

## PUT /products/{id} — MODERATOR

```json
{
  "name": "Áo sơ mi",
  "photoUrls": ["https://example.com/front.jpg", "https://example.com/back.jpg"],
  "categoryId": "11111111-1111-4111-8111-111111111111"
}
```

- `categoryId` không bắt buộc; bỏ qua/null giữ nguyên danh mục.
- Trùng ID danh mục hiện tại: không query danh mục thêm.
- ID khác: tra danh mục, không tồn tại trả `CATEGORY_NOT_FOUND`; tồn tại thì đổi `Product.category`.
- Vẫn yêu cầu Moderator cùng kho, trạng thái `ON_SALE`/`WAREHOUSE`, không có đơn đang hoạt động.
- SKU giữ nguyên khi đổi danh mục. `tryonSlot` được lấy từ danh mục mới trong response và các luồng thử đồ.
- Constructor Java hai tham số và payload JSON cũ chỉ có `name`/`photoUrls` vẫn dùng được.
- `productRepository.save` tiếp tục kích hoạt `ProductListCacheInvalidationConfig`, invalidation sau commit.

## GET /products — Public/Member

Mỗi `ProductSummaryResponse` thêm `categoryId` (UUID) và `tryonSlot` (TOP / BOTTOM / DRESS), được map tại `ProductCatalogReader.load`. Member dùng trực tiếp hai field này để chọn slot thử đồ. Các field giá, ảnh, `isMine`, `featured` và bộ lọc giữ nguyên.
