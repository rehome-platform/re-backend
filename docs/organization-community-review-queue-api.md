# Xác minh tổ chức, kiểm duyệt cộng đồng, chat và công việc của tôi

Các thay đổi dùng endpoint hiện có. Không thêm cột DB hoặc giá trị enum.

## API tương ứng từng task

| Task | API | Hành vi sau cập nhật |
| --- | --- | --- |
| Hồ sơ pháp lý gộp | `POST /api/organizations/{id}/verify` | Chấp nhận ít nhất 1 tài liệu có `fileUrl` không trống và người đại diện có đủ ảnh CCCD mặt trước/mặt sau. Không đếm số tài liệu theo loại tổ chức. |
| Bài viết chứa từ vi phạm | `POST /posts`, `PUT /posts/{id}` | Mọi action khớp rule đều đặt `status=PENDING_REVIEW`, `reviewStatus=PENDING_REVIEW`. Không ném CONTENT_VIOLATION riêng do AUTO_REJECT trong luồng này. |
| Bình luận chứa từ vi phạm | `POST /posts/{id}/comments` | Mọi action khớp rule đều chuyển cả hai trạng thái sang PENDING_REVIEW; không gửi POST_COMMENTED hoặc COMMENT_REPLIED cho comment đang ẩn. |
| Nội dung bình luận ẩn | `GET /posts/{id}/comments`, payload tạo bình luận | Chỉ tác giả thấy nội dung và reviewReason của comment chờ duyệt. Nội dung REMOVED luôn là null. |
| Admin duyệt nội dung | `GET /content-reviews/{kind}`, `POST /content-reviews/{kind}/{id}/decision` | Cả ADMIN và MODERATOR được truy cập. COMMENT đang PENDING_REVIEW hoặc VISIBLE với reviewStatus=PENDING_REVIEW đều xử lý được. |
| Chat chỉ che từ và ghi log | `POST /conversations/{id}/messages`, `GET /conversations/{id}/messages` | Giữ cơ chế mask và payload originalContent hiện có. Ghi log CHAT ở lúc gửi, không gửi thông báo CONTENT_REVIEW tới Admin. |
| Việc Moderator đang giữ | `GET /queues?type=PACKING&mine=true` | Chỉ trả việc được chính Moderator nhận, trong phạm vi kho hoặc việc dùng chung không gắn kho. |

## 1. Điều kiện xác minh tổ chức

`OrganizationServiceImpl.requireCompleteDossier` yêu cầu:

- Có ít nhất một `OrganizationDocument.fileUrl` thỏa `StringUtils.hasText`.
- Có `OrganizationRepresentative`, `cccdFrontUrl` và `cccdBackUrl` đều có nội dung.

Một file PDF gộp vẫn được chấp nhận cho mọi loại tổ chức. Danh sách document có
nhiều dòng nhưng toàn `fileUrl=null`, rỗng hoặc khoảng trắng không được xem là đã nộp tệp.
Helper `OrganizationVerificationResponse.requiredDocumentCount` đã được bỏ; checklist
trong response tiếp tục phục vụ Moderator đối chiếu hồ sơ.

Cả ba nhóm lỗi đều giữ mã `CHECKLIST_INCOMPLETE`:

| Thiếu gì | Message |
| --- | --- |
| Tệp pháp lý và chưa đủ ảnh CCCD | `CHECKLIST_INCOMPLETE: cần có ít nhất 1 tệp giấy tờ pháp lý và đủ ảnh CCCD 2 mặt của người đại diện.` |
| Chỉ tệp pháp lý | `CHECKLIST_INCOMPLETE: thiếu tệp giấy tờ pháp lý.` |
| Chỉ một hoặc cả hai ảnh CCCD | `CHECKLIST_INCOMPLETE: thiếu ảnh CCCD mặt trước hoặc mặt sau của người đại diện.` |

## 2. Kiểm duyệt bài viết và bình luận

`evaluateForReview(String... texts)` trả về action nghiêm trọng nhất trong các rule
khớp: `AUTO_REJECT` > `AUTO_FLAG` > `NOTIFY_ADMIN`; không khớp trả `null`.
Luồng bài viết/bình luận dùng hàm này. `evaluate` và `check` tiếp tục chặn AUTO_REJECT
ở những chức năng khác như chiến dịch, hồ sơ.

Với `POST` hoặc `POST_COMMENT`, `recordMatch` luôn ghi `CONTENT_AUTO_FLAG`,
`targetType=CONTENT_REVIEW`, kèm context, moderationAction và contentId trong detail.
Mỗi tài khoản ADMIN nhận một thông báo `NotificationType.CONTENT_REVIEW`.
Bài viết mới đang chờ duyệt không gửi thông báo bài mới cho người theo dõi.

Với bình luận, API danh sách giữ dòng bình luận để FE dựng cây trả lời:

| status | content với tác giả | content với người khác/khách | reviewReason |
| --- | --- | --- | --- |
| VISIBLE | Giữ nội dung | Giữ nội dung | Chỉ tác giả |
| PENDING_REVIEW | Giữ nội dung | null | Chỉ tác giả |
| REMOVED | null | null | Chỉ tác giả |

Payload trả về khi chính tác giả tạo comment chờ duyệt vẫn có content và
`isMine=true`. FE dùng status để hiển thị trạng thái chờ duyệt, không tự coi một
request tạo thành công là nội dung đã được công khai.

Admin/Moderator dùng `kind=POST` hoặc `kind=COMMENT`. Request quyết định giữ nguyên:

```json
{
  "approved": false,
  "version": 4,
  "reason": "Nội dung không phù hợp"
}
```

Khi duyệt: `VISIBLE` / `APPROVED`. Khi gỡ: `REMOVED` / `REJECTED`.
Vẫn kiểm tra version, reviewStatus và không cho duyệt comment thuộc bài đã bị gỡ.
Dữ liệu comment cũ có status VISIBLE nhưng đang chờ duyệt vẫn được xử lý.

## 3. Chat

Chat tiếp tục dùng `mask` để che từ, lưu và trả originalContent theo cơ chế hiện có.
`recordMatch(..., "CHAT", ...)` ghi `actionType=CONTENT_CHAT_MASKED`,
`targetType=CONTENT_REVIEW` rồi kết thúc; không truy vấn danh sách Admin và không
tạo thông báo chờ duyệt. Detail audit chỉ có metadata, không chứa nội dung chat.

## 4. Lọc công việc theo mine

| Request | Kết quả |
| --- | --- |
| `GET /queues?type=PACKING&mine=true` | Chỉ việc PACKING có claimedBy là Moderator đang đăng nhập. |
| `GET /queues?mine=true` | Mọi loại việc mà Moderator đang giữ, trong phạm vi kho/việc chung được phép thấy. |
| `GET /queues?type=PACKING` | Giữ danh sách đầy đủ như trước. |
| `GET /queues?type=PACKING&mine=false` | Giống bỏ trống mine. |

`mine=true` không chạy `syncPendingReturnRequests`, kể cả khi `type=RETURN`.
Luồng cũ null/false tiếp tục bù việc RETURN còn thiếu như trước.
Việc chưa ai nhận và việc do Moderator khác nhận không nằm trong mine=true.
Response QueueItemResponse và thứ tự theo createdAt được giữ nguyên.

## Kiểm thử

- Service tests kiểm tra ba mức rule, audit và thông báo từng Admin; giữ hành vi
  chặn của evaluate và mask của chat.
- Comment tests kiểm tra payload tác giả và người xem khác, cùng việc không phát
  thông báo bình luận/phản hồi khi đang ẩn.
- Method-security tests chạy proxy thật để xác nhận ADMIN/MODERATOR được phép và
  MEMBER/ORGANIZATION/khách không được vào luồng kiểm duyệt.
- Queue controller tests kiểm tra binding mine null/true/false và giá trị không hợp lệ.
- H2 repository tests chạy JPQL thực tế để kiểm tra claimedBy, phạm vi kho, việc chung,
  và hành vi danh sách cũ.
