# API gợi ý phối đồ bằng ML, song song với Gemini

**Mới:** `GET /tryon/outfit-suggestions/ml` — yêu cầu Bearer access token của Member.
**Giữ nguyên:** `GET /tryon/outfit-suggestions` — tiếp tục dùng Gemini và luồng cũ.

## Request

| Query parameter | Bắt buộc | Ý nghĩa |
|---|---|---|
| `productId` | Có | SKU món đang chọn, phải còn ON_SALE, thuộc TOP hoặc BOTTOM |
| `latitude` | Không | Vĩ độ, phải truyền cùng longitude, trong [-90, 90] |
| `longitude` | Không | Kinh độ, phải truyền cùng latitude, trong [-180, 180] |

Ví dụ: `GET /tryon/outfit-suggestions/ml?productId=MLR1-SP-000361`.
Không truyền memberId/userId; BE lấy tài khoản từ token. Món TOP tìm BOTTOM và ngược lại.
Không hỗ trợ ghép món với DRESS. Không gọi AI gen ảnh và không trừ quota thử đồ.

## Response

Wrapper `ApiResponse` giữ như project. Phần `result` có dạng:

```json
{
  "model": "multinomial-nb-v1",
  "personalized": true,
  "historyItemsUsed": 12,
  "candidateItemsScored": 30,
  "items": [
    {
      "id": "MLR1-SP-000392",
      "name": "Quan tay den",
      "thumbnailUrl": null,
      "brandName": "ML Demo Brand 2",
      "categoryName": "ML Demo - Quan tay",
      "size": "M",
      "condition": "Synthetic item - good condition",
      "originalPrice": 260000,
      "currentPrice": 260000,
      "warehouseId": "UUID-kho",
      "reason": "Xếp hạng theo lịch sử mua và độ phù hợp với món đang chọn"
    }
  ]
}
```

Đây là ví dụ cấu trúc, không phải cam kết SKU/giá/thứ tự cụ thể của một lần chạy.
`items` giữ các trường sản phẩm như API Gemini; FE lấy danh sách qua `result.items`.
Trả tối đa 10 món. Không có ứng viên thì `items=[]`.
`personalized=false` khi chưa có lịch sử hợp lệ: xếp hạng bằng luật phối đồ và các tie-breaker,
không giả vờ model đã học sở thích người mới.

## Mô hình và giới hạn

`OutfitPreferenceModel` được fit trong Java trên mỗi request, không gọi Gemini hay service ML bên ngoài.
Không cần tải model, API key, Python hoặc GPU để sử dụng endpoint này.

- Lấy tối đa 200 order_items gần nhất của chính người dùng: đơn COMPLETED, completedAt có giá trị,
  không có ReturnRequest VALID. Đơn hủy/chưa hoàn thành/đã trả không được dùng.
- Feature gồm từ trong tên món đã chuẩn hóa tiếng Việt, danh mục, thương hiệu, size,
  khoảng giá logarit. Giá lịch sử dùng `price_at_order`; không học từ email/ID/nhãn nhóm seed.
- Ước lượng phân bố token từ lịch sử người dùng và phân bố nền từ catalogue ứng viên,
  làm trơn Laplace. Điểm sở thích là trung bình log likelihood ratio của hai phân bố,
  theo giả định độc lập đặc trưng của Naive Bayes. Đây là tham số học từ dữ liệu,
  không phải một bảng luật gán sẵn phong cách cho từng tài khoản.
- Chấm tối đa 500 ứng viên mới nhất, ON_SALE, đúng slot đối ứng, khác món đang chọn,
  không thuộc người gọi. Candidate pool có giới hạn nên không đảm bảo tìm tối ưu toàn bộ catalogue lớn.
- Tổng điểm: `0.35 * colorScore/3 + 0.25 * styleScore/2 + 0.40 * tanh(preferenceScore)`.
  Các trọng số phối hợp là cấu hình nghiệp vụ cố định trong code, chưa được tối ưu bằng tập validation.
  Điểm này không phải xác suất mua hoặc độ chính xác của model.
- Khi bằng điểm: cùng kho trước, khoảng cách gần hơn nếu có tọa độ, món mới hơn, rồi SKU.
- Ảnh thumbnail được lấy theo batch cho các kết quả cuối; các quan hệ cần dùng được fetch cùng query.
  Không giữ transaction chờ HTTP/GPU vì endpoint này không có lời gọi bên ngoài.

Model chỉ **học sở thích từ mua hàng**. Độ hợp giữa hai món hiện vẫn được đánh giá bằng
`OutfitMatchingRules`; chưa có mô hình được huấn luyện riêng trên dữ liệu cặp outfit.
Chưa có đánh giá Precision@K/NDCG hoặc chất lượng trên người dùng thật; test dữ liệu mô phỏng
chỉ kiểm tra tính đúng của luồng và sự khác nhau khi lịch sử thay đổi.

## Test/demo

Chạy seed `scripts/seed_ml_purchase_history.sql` trên local/test. Login các tài khoản
`ml.office.01@example.test`, `ml.sport.01@example.test` (mật khẩu 123456), rồi gọi API với cùng SKU TOP.
Kiểm tra metadata và thứ tự; mức khác nhau còn phụ thuộc độ hợp với món được chọn.
Login `ml.new.01@example.test` để kiểm tra `personalized=false`.

Không cần migration riêng cho API ML; chỉ đọc các bảng hiện có.
Các test model/service kiểm tra lịch sử khác nhau cho ranking khác nhau, cold start và validation;
test SQL kiểm tra phạm vi người mua, loại đơn hủy/trả và loại hàng tự sở hữu;
test HTTP kiểm tra route mới, token identity và route Gemini vẫn hoạt động riêng.

Kiểm chứng ngày 21/11/2026: toàn bộ Maven test có **2.048 test pass**, không lỗi hoặc bỏ qua.
Chưa deploy/chạy API trên database thật của người dùng, chưa công bố số liệu chất lượng recommendation.
