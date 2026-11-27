# Rút hàng giữa kỳ: giữ hàng trong kho đến khi bàn giao

## Trạng thái sản phẩm

| Sự kiện | Trạng thái yêu cầu rút | Trạng thái sản phẩm |
|---|---|---|
| Member tạo yêu cầu | `PENDING_APPROVAL` | `WAREHOUSE` |
| Moderator duyệt | `PENDING_PICKUP` | Giữ `WAREHOUSE` |
| Moderator bàn giao tại quầy | `COMPLETED` | `WITHDRAWN` |
| Moderator từ chối | `REJECTED` | `ON_SALE` |
| Quá hạn trả phí hoặc lấy hàng | `EXPIRED` | `ON_SALE` |
| Member đổi yêu cầu đã duyệt sang quyên góp | `CONVERTED_TO_DONATION` | `DONATED` |

Sản phẩm `WAREHOUSE` không còn được bán trên sàn nhưng vẫn tính là hàng trong kho. Dữ liệu cũ có yêu cầu đang chờ duyệt/chờ lấy với sản phẩm `WITHDRAWN` tiếp tục được hỗ trợ; không cần cập nhật hàng loạt hoặc migration DB.

## Các API thay đổi

- `POST /item-withdrawal-requests`: ngừng bán bằng `WAREHOUSE`.
- `POST /item-withdrawal-requests/{id}/approve`: logic duyệt và các deadline giữ nguyên; không đổi trạng thái sản phẩm.
- `POST /item-withdrawal-requests/{id}/reject` và job quá hạn: cho cả `WAREHOUSE` và `WITHDRAWN` về `ON_SALE`.
- `POST /counter/handover`, context `ITEM_WITHDRAWAL`: giữ kiểm tra danh tính, chủ yêu cầu, trạng thái và phí đã trả; khoá sản phẩm bằng `findByIdForUpdate`, chuyển sang `WITHDRAWN` trong cùng transaction với hoàn tất yêu cầu. Chưa trả phí vẫn trả 409 `WITHDRAWAL_FEE_NOT_PAID`, không bàn giao/dequeue.
- `PUT /products/{id}`: chặn sửa hàng `WAREHOUSE` nếu có yêu cầu `PENDING_APPROVAL` hoặc `PENDING_PICKUP`; giữ các kiểm tra kho, đơn hàng và danh mục.
- `POST /products/{id}/donate`: với hàng `WAREHOUSE`, ưu tiên kiểm tra yêu cầu rút. Chờ duyệt trả `WITHDRAWAL_PENDING_APPROVAL`; chờ lấy dùng case `C1`, chuyển yêu cầu sang `CONVERTED_TO_DONATION`, gọi luồng hoàn phí hiện có và dequeue. Không có yêu cầu rút thì giữ nhánh cuối kỳ `B`. Nhánh `WITHDRAWN` cũ giữ nguyên.

## Field để FE hiển thị badge

Ba response được bổ sung:

| API | DTO |
|---|---|
| `GET /counter/members/{accountId}/tasks`, từng `pendingForms` | `MemberTasksResponse.PendingFormItem` |
| `GET /products/{id}` dành cho Moderator | `ProductSkuLookupResponse` |
| `GET /inventory` | `InventoryItemResponse` |

```json
{
  "activeWithdrawalId": "11111111-1111-4111-8111-111111111111",
  "activeWithdrawalStatus": "PENDING_APPROVAL"
}
```

`activeWithdrawalStatus` có thể là `PENDING_APPROVAL` hoặc `PENDING_PICKUP`. Không có yêu cầu rút đang sống thì cả hai field là null; hàng cuối kỳ `WAREHOUSE` vẫn có thể trả null. Chỉ tra yêu cầu cho hàng `WAREHOUSE` hoặc `WITHDRAWN`.

Màn quầy và danh sách tồn kho sử dụng một query `findByProductIdsAndStatusIn` cho toàn bộ sản phẩm phù hợp trong lô/trang. Tra SKU sử dụng `findFirstByProduct_IdAndStatusIn` cho một sản phẩm. Lô rỗng không gọi thêm query.

## Thông báo hàng đợi

Nội dung thông báo chỉ có tên việc, ví dụ `Đóng gói đơn hàng` hoặc `Xử lý yêu cầu trả hàng`. Bỏ phần ` · UUID`; title, target type, target ID và warehouse ID giữ nguyên để client điều hướng. Áp dụng chung cho thông báo được lưu và phát qua WebSocket.

## Kịch bản kiểm tra

1. Tạo yêu cầu rút: sản phẩm mất khỏi sàn, tồn kho vẫn có hàng và badge chờ duyệt.
2. Sửa sản phẩm đang chờ duyệt/chờ lấy: 409 `PRODUCT_UPDATE_CONFLICT`.
3. Duyệt: sản phẩm vẫn `WAREHOUSE`, hạn trả phí vẫn 15 phút.
4. Chưa trả phí: bàn giao bị chặn, trạng thái và hàng đợi không đổi.
5. Đã trả phí/miễn phí rồi bàn giao: yêu cầu `COMPLETED`, sản phẩm `WITHDRAWN`, dequeue thành công.
6. Từ chối hoặc quá hạn: sản phẩm về `ON_SALE`, áp dụng cho cả dữ liệu mới và cũ.
7. Đổi sang quyên góp: chờ duyệt bị chặn; chờ lấy chuyển case `C1` và hoàn phí theo luồng hiện có.
8. Ba màn quầy/SKU/tồn kho trả hai field tương ứng; sản phẩm không có yêu cầu rút trả null.
