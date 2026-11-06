# Hướng dẫn tích hợp Chat & Notification real-time (Web / Mobile)

Backend dùng **WebSocket (STOMP)** để báo tin nhắn mới và thông báo real-time khi app đang mở, và
**Firebase Cloud Messaging (FCM)** để push khi app đã tắt/background.

---

## 1. Kết nối WebSocket (STOMP)

| Nền tảng | Endpoint | Thư viện gợi ý |
|---|---|---|
| Web (browser) | `wss://<domain>/ws-sockjs` (có SockJS fallback) | `@stomp/stompjs` + `sockjs-client` |
| Mobile (Android/iOS/Flutter) | `wss://<domain>/ws` (STOMP thuần) | `@stomp/stompjs` (RN), `stomp_dart_client` (Flutter), hoặc client STOMP native |

### Xác thực

JWT **không** gửi ở header HTTP lúc mở socket — phải gửi trong STOMP header `Authorization` của
frame `CONNECT`:

```js
import { Client } from '@stomp/stompjs';

const client = new Client({
  brokerURL: 'wss://api.rehome.vn/ws', // hoặc /ws-sockjs cho web qua SockJS
  connectHeaders: {
    Authorization: `Bearer ${accessToken}`, // access token JWT, giống header REST
  },
  onConnect: () => { /* subscribe ở đây */ },
});
client.activate();
```

Nếu token sai/hết hạn hoặc account bị khoá → server trả frame `ERROR` và đóng kết nối. Khi access
token hết hạn (REST trả 401 `ACCESS_TOKEN_EXPIRED`), phải refresh token rồi **mở lại** kết nối
WebSocket với token mới — không có cơ chế refresh giữa phiên.

---

## 2. Chat — nhận tin nhắn mới

**Subscribe sau khi đã mở hội thoại** (gọi `GET /conversations/{id}` hoặc `GET
/conversations/{id}/messages` qua REST trước để chắc chắn mình thuộc hội thoại đó):

```js
client.subscribe(`/topic/conversations.${conversationId}`, (message) => {
  const msg = JSON.parse(message.body); // MessageResponse
  // { id, senderId, content, imageUrls, createdAt, isMine }
});
```

- Server **chặn subscribe** nếu bạn không phải Member/Organization/Moderator thuộc hội thoại đó
  (frame `ERROR`, không có data rò ra).
- `isMine` trong message nhận qua WebSocket luôn là `false` (server không biết ai đang nghe) — tự so
  sánh `senderId` với account id của mình để hiển thị đúng bên trái/phải.
- Gửi tin nhắn vẫn qua REST, **không** gửi qua WebSocket:
  `POST /conversations/{id}/messages` `{ content, imageUrls }` — người gửi nhận kết quả qua response
  REST, người nhận (bên còn lại) nhận qua topic trên.
- Ảnh trong `imageUrls`: phải là URL Cloudinary thật (upload qua `POST /uploads` trước), không chấp
  nhận link ngoài — gửi link lạ sẽ bị từ chối 400.

---

## 3. Notification — nhận thông báo mới

```js
client.subscribe('/user/queue/notifications', (message) => {
  const noti = JSON.parse(message.body); // NotificationResponse
  // { id, type, title, message, isRead, targetType, targetId, warehouseId, createdAt }
});
```

- Đường dẫn này **riêng cho từng người** — không cần biết account id, Spring tự định tuyến theo JWT
  đã xác thực ở bước CONNECT.
- Lịch sử thông báo vẫn lấy qua REST: `GET /notifications`, đánh dấu đã đọc qua
  `PATCH/POST /notifications/{id}/read`.
- `targetType` + `targetId` dùng để mở đúng màn hình khi người dùng bấm vào thông báo (deep-link).

---

## 4. Push notification khi app tắt/background (FCM)

WebSocket chỉ nhận được khi app còn giữ kết nối. Khi app bị kill hoặc ở background lâu, cần đăng ký
token FCM để nhận push qua thông báo hệ thống (system notification).

### Bước tích hợp (mobile)
1. Tích hợp Firebase SDK (Android/iOS/Flutter) theo hướng dẫn chính thức của Firebase, lấy
   **registration token** của thiết bị.
2. Sau khi đăng nhập (và mỗi lần Firebase báo token mới/refresh), gọi:

```http
POST /me/device-tokens
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "token": "<fcm_registration_token>",
  "platform": "ANDROID"   // hoặc "IOS", "WEB"
}
```

3. Lúc đăng xuất, gọi gỡ token (để máy đó không nhận push của account vừa đăng xuất):

```http
DELETE /me/device-tokens?token=<fcm_registration_token>
Authorization: Bearer <accessToken>
```

### Nhận và xử lý push
- Payload FCM gồm `notification.title`/`notification.body` (hệ thống tự hiện) và `data`:
  `notificationId`, `type`, `targetType`, `targetId` — dùng để điều hướng khi người dùng bấm vào.
- Push và WebSocket là **2 đường độc lập**, có thể cả hai cùng báo 1 thông báo nếu app đang mở — xử
  lý dedupe theo `notificationId`/`id` nếu cần tránh hiện 2 lần.

Push áp dụng cho **cả chat và notification** — khi có tin nhắn mới hoặc thông báo mới mà người
nhận không mở WebSocket, backend tự gửi qua FCM tới mọi thiết bị đã đăng ký của người đó. Payload
`data` của push tin nhắn chat có thêm `conversationId` (không có `notificationId`), `type` =
`"CHAT_MESSAGE"`, để phân biệt với push của notification thường.

### Web
Web không cần tích hợp FCM (không chạy khi tab đã đóng) — chỉ cần WebSocket ở mục 2/3 là đủ cho
trải nghiệm lúc đang mở trang. Nếu muốn thêm push cho web (Web Push qua FCM) thì làm sau, không nằm
trong phạm vi hiện tại.

---

## 5. Tóm tắt nhanh

| Việc | Giao thức | Địa chỉ |
|---|---|---|
| Mở kết nối | WebSocket | `wss://<domain>/ws` (mobile) hoặc `/ws-sockjs` (web) |
| Xác thực | STOMP CONNECT header | `Authorization: Bearer <token>` |
| Nhận tin nhắn mới | STOMP subscribe | `/topic/conversations.{conversationId}` |
| Gửi tin nhắn | REST | `POST /conversations/{id}/messages` |
| Nhận thông báo mới | STOMP subscribe | `/user/queue/notifications` |
| Đăng ký push (app tắt) | REST | `POST /me/device-tokens` |
| Gỡ push (đăng xuất) | REST | `DELETE /me/device-tokens?token=...` |
