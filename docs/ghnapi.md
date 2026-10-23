# **Tổng quan API**

GHN API cung cấp các khối chức năng để tạo đơn, tính phí, đồng bộ địa chỉ, nhận webhook và quản lý yêu cầu hỗ trợ. Tất cả phản hồi sử dụng JSON và mã trạng thái nhất quán.

## [Bắt đầu nhanh](https://developer.ghn.dev/vi/docs/getting-started/overview#bat-dau-nhanh)

Thực hiện lần lượt ba bước sau để xác thực, tạo đơn thử và nhận trạng thái đơn.

1\. **Xác thực** — Gửi Token và ShopId trong header của yêu cầu. [Mở hướng dẫn](https://developer.ghn.dev/vi/docs/token/get-token)

2\. **Tạo đơn** — Gửi địa chỉ, kiện hàng và lựa chọn dịch vụ. [Mở endpoint](https://developer.ghn.dev/vi/docs/order/create)

3\. **Nhận webhook** — Đồng bộ trạng thái đơn theo thời gian thực. [Mở hướng dẫn](https://developer.ghn.dev/vi/docs/webhook/callback-order-status)

## [Môi trường API](https://developer.ghn.dev/vi/docs/getting-started/overview#moi-truong-api)

Phát triển và kiểm thử ở môi trường riêng trước khi chuyển sang vận hành thực tế.

| Môi trường | URL |
| ----- | ----- |
|  |  |
| Production | https\://online-gateway.ghn.vn |
| Staging | https\://dev-online-gateway.ghn.vn |

## [Quy ước chung](https://developer.ghn.dev/vi/docs/getting-started/overview#quy-uoc-chung)

* Gửi và nhận dữ liệu dưới định dạng application/json.  
* Thời gian dùng ISO 8601 và múi giờ Việt Nam khi có thể.  
* Số tiền dùng đồng; khối lượng dùng gram; kích thước dùng centimet.  
* Không ghi Token vào log, URL hoặc mã nguồn phía client.

# **Lấy Token**

Mỗi request API được xác thực bằng Token API ở header Token (một số API cần thêm ShopId). Bạn tự lấy token trong vài phút ngay trên Developer Portal, ở mục Quản lý token của tài khoản — không cần nhờ GHN cấp.

## [Đăng nhập ở đâu](https://developer.ghn.dev/vi/docs/token/get-token#dang-nhap-o-dau)

| Môi trường | URL |
| ----- | ----- |
|  |  |
| Production | https\://developer.ghn.vn |
| Staging | https\://developer.ghn.dev |

## [Các bước](https://developer.ghn.dev/vi/docs/token/get-token#cac-buoc)

1\. Mở Developer Portal của môi trường tương ứng, bấm nút **Đăng nhập** ở góc phải header.

![Trang chủ Developer Portal với nút Đăng nhập ở góc phải header][image1]Trang chủ Developer Portal với nút Đăng nhập ở góc phải header

2\. Đăng nhập bằng số điện thoại rồi qua bước xác thực.

![Màn hình đăng nhập: nhập số điện thoại, hoặc chọn Google, Apple, tài khoản nội bộ GHN][image2]Màn hình đăng nhập: nhập số điện thoại, hoặc chọn Google, Apple, tài khoản nội bộ GHN

3\. Sau khi đăng nhập, mở menu tài khoản ở góc phải header rồi chọn [Quản lý token](https://developer.ghn.dev/account).

![Menu tài khoản mở ra với mục Quản lý token, Cấu hình webhook và Đăng xuất][image3]Menu tài khoản mở ra với mục Quản lý token, Cấu hình webhook và Đăng xuất

4\. Ở thẻ **Token API**, bấm **biểu tượng con mắt**. Hệ thống gửi mã OTP 6 chữ số về số điện thoại của tài khoản; nhập mã đó thì token hiện ra, bấm **biểu tượng copy** để sao chép.

![Thẻ Token API sau khi nhập OTP, token đã hiện rõ và nút copy đã bật][image4]Thẻ Token API sau khi nhập OTP, token đã hiện rõ và nút copy đã bật

5\. Tuỳ chọn nhưng nên làm: giới hạn máy chủ được dùng token bằng cách thêm IP vào **Danh sách IP được phép sử dụng** ngay dưới thẻ Token API. Danh sách còn trống thì mọi IP đều gọi được; thêm IP đầu tiên vào là chỉ những IP đó gọi được — nhớ thêm đủ IP của tất cả máy chủ trước khi lên production.

6\. Cần nhận callback trạng thái đơn hoặc ticket? Cũng trong menu tài khoản, chọn [Cấu hình webhook](https://developer.ghn.dev/account/webhook) là bạn tự khai endpoint được — xem [Callback trạng thái đơn](https://developer.ghn.dev/vi/docs/webhook/callback-order-status).

## [Cách dùng](https://developer.ghn.dev/vi/docs/token/get-token#cach-dung)

Gửi token trong mọi request, kèm ShopId với các API yêu cầubashSao chép  
curl \-X GET "https\://online-gateway.ghn.vn/shiip/public-api/master-data/province" \\  
 \-H "Token: \<token-cua-ban\>" \\  
 \-H "ShopId: \<shop-id-cua-ban\>"

## [Lưu ý](https://developer.ghn.dev/vi/docs/token/get-token#luu-y)

* **Giữ token bí mật** — coi như mật khẩu. Đừng commit vào git hay để lộ trong code phía trình duyệt.  
* **Staging và Production là hai tài khoản riêng biệt**, token riêng. Token của developer.ghn.dev không dùng được cho Production.  
* Mặc định token gọi được từ mọi IP. Muốn chắc hơn thì thêm IP công khai của máy chủ vào danh sách IP được phép sử dụng — khi danh sách đã có IP, chỉ những IP đó mới gọi được API.  
* Một số API cần thêm ShopId — bạn xem trong thông tin tài khoản của mình.

# **Tạo đơn**

## [Endpoint](https://developer.ghn.dev/vi/docs/order/create#endpoint)

| Môi trường | URL |
| ----- | ----- |
|  |  |
| Production | https\://online-gateway.ghn.vn/shiip/public-api/v2/shipping-order/create |
| Staging | https\://dev-online-gateway.ghn.vn/shiip/public-api/v2/shipping-order/create |

**Method:POST**

## [Headers](https://developer.ghn.dev/vi/docs/order/create#headers)

| Header | Bắt buộc | Mô tả |
| ----- | ----- | ----- |
|  |  |  |
| Content-Type | Có | application/json |
| Token | Có | Token của bạn (do GHN cấp) — [lấy token](https://developer.ghn.dev/vi/docs/token/get-token) |
| ShopId | Có | Shop ID (số nguyên) |

## [Tham số](https://developer.ghn.dev/vi/docs/order/create#tham-so)

| Tham số | Kiểu | Bắt buộc | Mô tả |
| ----- | ----- | ----- | ----- |
|  |  |  |  |
| to\_name | String | Bắt buộc | Tên người nhận. Tối đa 1024 ký tự |
| to\_phone | String | Bắt buộc | Số điện thoại người nhận |
| to\_address | String | Bắt buộc | Địa chỉ đầy đủ người nhận. Tối đa 1024 ký tự |
| to\_ward\_name | String | Bắt buộc | Tên phường/xã người nhận — hệ mới 2 cấp tra bằng [Lấy Phường/Xã (Mới)](https://developer.ghn.dev/vi/docs/master-data/get-ward-new), hệ cũ tra bằng [Lấy Phường/Xã](https://developer.ghn.dev/vi/docs/master-data/get-ward) (theo district\_id) |
| to\_district\_name | String | Tùy chọn | Tên quận/huyện người nhận. **Bắt buộc khi is\_new\_to\_address \= false**; để trống khi true (đơn vị hành chính mới không còn cấp quận/huyện). Tra danh sách bằng [Lấy Quận/Huyện](https://developer.ghn.dev/vi/docs/master-data/get-district) |
| to\_province\_name | String | Bắt buộc | Tên tỉnh/thành phố người nhận — hệ mới 2 cấp tra bằng [Lấy Tỉnh/Thành (Mới)](https://developer.ghn.dev/vi/docs/master-data/get-province-new), hệ cũ tra bằng [Lấy Tỉnh/Thành](https://developer.ghn.dev/vi/docs/master-data/get-province) |
| is\_new\_to\_address | Bool | Tùy chọn | Cho biết khối địa chỉ người nhận dùng hệ đơn vị hành chính nào. Ngày **01/07/2025** Việt Nam sắp xếp lại đơn vị hành chính: sáp nhập tỉnh và phường/xã, bỏ cấp quận/huyện. Cả hai hệ đều dùng chung các trường địa chỉ — cờ này báo cho GHN biết bạn gửi theo hệ nào:false (mặc định): đơn vị cũ (phường/xã \+ quận/huyện \+ tỉnh)true: đơn vị mới (phường/xã \+ tỉnh) — danh mục ở [Lấy Tỉnh/Thành (Mới)](https://developer.ghn.dev/vi/docs/master-data/get-province-new) và [Lấy Phường/Xã (Mới)](https://developer.ghn.dev/vi/docs/master-data/get-ward-new) |
| from\_name | String | Tùy chọn | Tên người gửi. Mặc định: theo hồ sơ shop của ShopId. Tối đa 1024 ký tự |
| from\_phone | String | Tùy chọn | SĐT người gửi. Mặc định: theo hồ sơ shop |
| from\_hotline | String | Tùy chọn | Hotline người gửi. Mặc định: không có |
| from\_address | String | Tùy chọn | Địa chỉ đầy đủ người gửi. Mặc định: địa chỉ shop. Tối đa 1024 ký tự |
| from\_ward\_name | String | Tùy chọn | Tên phường/xã người gửi. Mặc định: địa chỉ shop |
| from\_district\_name | String | Tùy chọn | Tên quận/huyện người gửi. Mặc định: địa chỉ shop |
| from\_province\_name | String | Tùy chọn | Tên tỉnh/thành người gửi. Mặc định: địa chỉ shop |
| is\_new\_from\_address | Bool | Tùy chọn | Cùng quy tắc như is\_new\_to\_address, áp dụng cho khối địa chỉ người gửi. Mặc định: false |
| return\_name | String | Tùy chọn | Tên người nhận hàng trả. Mặc định: người gửi. Tối đa 1024 ký tự |
| return\_phone | String | Tùy chọn | SĐT liên hệ trả hàng. Mặc định: SĐT người gửi |
| return\_address | String | Tùy chọn | Địa chỉ đầy đủ trả hàng. Mặc định: địa chỉ kho mặc định của shop (trên Dashboard). Tối đa 1024 ký tự |
| return\_ward\_name | String | Tùy chọn | Tên phường/xã trả hàng |
| return\_district\_name | String | Tùy chọn | Tên quận/huyện trả hàng (xem is\_new\_to\_address) |
| return\_province\_name | String | Tùy chọn | Tên tỉnh/thành trả hàng |
| is\_new\_return\_address | Bool | Tùy chọn | Cùng quy tắc như is\_new\_to\_address, áp dụng cho khối địa chỉ trả hàng. Mặc định: false |
| client\_order\_code | String | Tùy chọn | Mã đơn nội bộ của shop (duy nhất theo shop). Tối đa 50\. Mặc định: null. Giúp gọi lại an toàn: gửi lại request với mã đã dùng sẽ trả về order\_code **đã tạo trước đó** thay vì tạo đơn trùng |
| weight | Int | Bắt buộc | Khối lượng (gram). Tối đa: 50,000 |
| length | Int | Bắt buộc | Chiều dài (cm). Tối đa: 200 |
| width | Int | Bắt buộc | Chiều rộng (cm). Tối đa: 200 |
| height | Int | Bắt buộc | Chiều cao (cm). Tối đa: 200 |
| content | String | Tùy chọn | Mô tả hàng hóa. Tối đa 2000\. **Bắt buộc khi không gửi items\[\]** — thiếu cả content lẫn items sẽ bị từ chối. Khi có items\[\], GHN tự sinh nội dung từ tên/số lượng sản phẩm |
| service\_type\_id | Int | Bắt buộc | Loại dịch vụ, chọn theo khối lượng:2: tổng khối lượng **dưới 20 kg**5: tổng khối lượng **từ 20 kg trở lên**, hoặc đơn nhiều kiệnXem gói khả dụng cho từng tuyến bằng [Lấy Dịch vụ](https://developer.ghn.dev/vi/docs/master-data/get-service) |
| payment\_type\_id | Int | Bắt buộc | Bên trả phí vận chuyển:1: Shop/Người bán2: Người mua/Người nhận |
| coupon | String | Tùy chọn | Mã khuyến mãi. Mặc định: không có |
| cod\_amount | Int | Tùy chọn | Số tiền thu hộ (COD) từ người nhận (VND). Tối đa: 50,000,000. Mặc định: 0 |
| cod\_failed\_amount | Int | Tùy chọn | Số tiền thu từ người nhận khi giao thất bại (VND). Mặc định: 0 |
| insurance\_value | Int | Tùy chọn | Giá trị khai giá bảo hiểm (VND). Tối đa: 5,000,000. Mặc định: 0 |
| order\_value | Int | Tùy chọn | Giá trị đơn hàng (VND). Mặc định: 0 |
| required\_note | String | Bắt buộc | Ghi chú giao hàng — một trong các giá trị:KHONGCHOXEMHANG: người nhận KHÔNG được mở/xem hàngCHOXEMHANGKHONGTHU: người nhận được xem hàng nhưng không được thửCHOTHUHANG: người nhận được xem và thử hàng |
| note | String | Tùy chọn | Ghi chú cho tài xế. Tối đa 5000\. Mặc định: không có |
| pick\_station\_id | Int | Tùy chọn | Mã bưu cục shop mang hàng đến gửi, lấy từ [Lấy Bưu cục](https://developer.ghn.dev/vi/docs/master-data/get-station) (trường locationId). Mặc định: 0 → GHN đến lấy hàng tại địa chỉ shop |
| pick\_shift | Int\[\] | Tùy chọn | Danh sách mã ca lấy hàng (dùng [Ca lấy hàng](https://developer.ghn.dev/vi/docs/master-data/pick-shift)). Mặc định: ca sớm nhất |
| items | Object\[\] | Tùy chọn | Danh sách sản phẩm (cấu trúc bên dưới). **Bắt buộc khi service\_type\_id=5** (dùng để tính phí theo từng kiện) **và khi không gửi content**. Với service\_type\_id=2 thì không bắt buộc nhưng nên có |

### [Cấu trúc items\[\]](https://developer.ghn.dev/vi/docs/order/create#cau-truc-items)

| Tham số | Kiểu | Bắt buộc | Mô tả |
| ----- | ----- | ----- | ----- |
|  |  |  |  |
| name | String | Bắt buộc | Tên sản phẩm. Tối đa 512 ký tự |
| code | String | Tùy chọn | Mã sản phẩm. Mặc định: không có |
| quantity | Int | Bắt buộc | Số lượng. Tối thiểu là 1 |
| price | Int | Tùy chọn | Giá sản phẩm (VND). Mặc định: không có |
| length | Int | Tùy chọn | Chiều dài (cm). **Bắt buộc khi service\_type\_id=5** |
| width | Int | Tùy chọn | Chiều rộng (cm). **Bắt buộc khi service\_type\_id=5** |
| height | Int | Tùy chọn | Chiều cao (cm). **Bắt buộc khi service\_type\_id=5** |
| weight | Int | Tùy chọn | Khối lượng (gram). **Bắt buộc khi service\_type\_id=5** |

**Lưu ý**: Với service\_type\_id=5 (tổng khối lượng ≥ 20 kg hoặc nhiều kiện), hệ thống tính phí theo từng kiện từ items\[\], nên mỗi item bắt buộc có length, width, height, weight. Với service\_type\_id=2 (dưới 20 kg), chỉ cần kích thước ở cấp đơn hàng (weight, length, width, height ở gốc).

## [Ví dụ Request](https://developer.ghn.dev/vi/docs/order/create#vi-du-request)

Ví dụ đầy đủ (địa chỉ định dạng cũ, gồm các nhóm trường thường dùng)bashSao chép  
curl \-X POST https\://dev-online-gateway.ghn.vn/shiip/public-api/v2/shipping-order/create \\  
 \-H "Token: 5c1d8a9e-2f4b-11ed-..." \\  
 \-H "ShopId: 92837" \\  
 \-H "Content-Type: application/json" \\  
 \-d '{  
    "payment\_type\_id": 2,  
    "required\_note": "CHOXEMHANGKHONGTHU",  
    "client\_order\_code": "SHOP-2026-00193",  
    "from\_name": "Shop Áo GHN",  
    "from\_phone": "0332190444",  
    "from\_address": "39 Nguyễn Thị Thập, Phường Tân Phú, Quận 7, TP. Hồ Chí Minh",  
    "from\_ward\_name": "Phường Tân Phú",  
    "from\_district\_name": "Quận 7",  
    "from\_province\_name": "TP. Hồ Chí Minh",  
    "return\_name": "Kho Shop Áo GHN",  
    "return\_phone": "0332190444",  
    "return\_address": "39 Nguyễn Thị Thập, Phường Tân Phú, Quận 7, TP. Hồ Chí Minh",  
    "return\_ward\_name": "Phường Tân Phú",  
    "return\_district\_name": "Quận 7",  
    "return\_province\_name": "TP. Hồ Chí Minh",  
    "to\_name": "Trần Minh Anh",  
    "to\_phone": "0987654321",  
    "to\_address": "72 Lê Thánh Tôn, P. Bến Nghé, Quận 1, TP. Hồ Chí Minh",  
    "to\_ward\_name": "Phường Bến Nghé",  
    "to\_district\_name": "Quận 1",  
    "to\_province\_name": "TP. Hồ Chí Minh",  
    "cod\_amount": 285000,  
    "cod\_failed\_amount": 0,  
    "insurance\_value": 285000,  
    "order\_value": 285000,  
    "coupon": null,  
    "service\_type\_id": 2,  
    "content": "Áo thun unisex GHN \- 2 chiếc",  
    "note": "Gọi trước khi giao",  
    "weight": 600,  
    "length": 25,  
    "width": 20,  
    "height": 8,  
    "pick\_shift": \[2\],  
    "items": \[  
      {  
        "name": "Áo thun GHN size M",  
        "code": "GHN-TS-M-BLK",  
        "quantity": 2,  
        "price": 142500,  
        "length": 12,  
        "width": 12,  
        "height": 12,  
        "weight": 300  
      }  
    \]  
  }'  
Địa chỉ định dạng mới (sau 01/07/2025) — cùng tên trường; đặt is\_new\_to\_address: true, không có quận/huyệnbashSao chép  
curl \-X POST https\://dev-online-gateway.ghn.vn/shiip/public-api/v2/shipping-order/create \\  
 \-H "Token: 5c1d8a9e-2f4b-11ed-..." \\  
 \-H "ShopId: 92837" \\  
 \-H "Content-Type: application/json" \\  
 \-d '{  
    "payment\_type\_id": 2,  
    "required\_note": "CHOXEMHANGKHONGTHU",  
    "to\_name": "Trần Minh Anh",  
    "to\_phone": "0987654321",  
    "to\_address": "72 Lê Thánh Tôn, Phường Sài Gòn, TP. Hồ Chí Minh",  
    "to\_ward\_name": "Phường Sài Gòn",  
    "to\_province\_name": "Hồ Chí Minh",  
    "is\_new\_to\_address": true,  
    "content": "Áo thun unisex GHN \- 2 chiếc",  
    "weight": 600,  
    "length": 25,  
    "width": 20,  
    "height": 8,  
    "service\_type\_id": 2,  
    "items": \[  
      { "name": "Áo thun GHN size M", "quantity": 2, "weight": 300 }  
    \]  
  }'

## [Response](https://developer.ghn.dev/vi/docs/order/create#response)

### [Thành công (HTTP 200\)](https://developer.ghn.dev/vi/docs/order/create#thanh-cong-http-200)

*Lấy từ một lần gọi thật trên môi trường Staging.*

jsonSao chép  
{  
  "code": 200,  
  "message": "Success",  
  "message\_display": "Tạo đơn hàng thành công. Mã đơn hàng: LADFYR",  
  "code\_message\_value": "",  
  "data": {  
    "order\_code": "LADFYR",  
    "fee": {  
      "main\_service": 20900,  
      "insurance": 0,  
      "cod\_fee": 0,  
      "station\_do": 0,  
      "station\_pu": 0,  
      "return": 0,  
      "r2s": 0,  
      "return\_again": 0,  
      "coupon": 0,  
      "document\_return": 0,  
      "double\_check": 0,  
      "double\_check\_deliver": 0,  
      "pick\_remote\_areas\_fee": 0,  
      "deliver\_remote\_areas\_fee": 0,  
      "pick\_remote\_areas\_fee\_return": 0,  
      "deliver\_remote\_areas\_fee\_return": 0,  
      "cod\_failed\_fee": 0,  
      "change\_to\_address\_fee": 0,  
      "change\_return\_address\_fee": 0  
    },  
    "total\_fee": 20900,  
    "expected\_delivery\_time": "2026-07-15T16:59:59Z"  
  }  
}

### [Các trường Response](https://developer.ghn.dev/vi/docs/order/create#cac-truong-response)

| Trường | Kiểu | Mô tả |
| ----- | ----- | ----- |
|  |  |  |
| code | Int | Mã HTTP (200 nếu thành công) |
| message | String | Thông báo kết quả (Success) |
| message\_display | String | Thông báo thành công dạng chữ (tiếng Việt) |
| data.order\_code | String | **Mã vận đơn GHN** — dùng cho mọi API sau này (chi tiết, cập nhật, hủy, phí, …). Webhook trạng thái bắt đầu bắn ngay khi có mã này — xem [Callback trạng thái đơn](https://developer.ghn.dev/vi/docs/webhook/callback-order-status) và [Mã trạng thái đơn hàng](https://developer.ghn.dev/vi/docs/master-data/order-status) |
| data.fee | Object | Chi tiết phí (VND): main\_service (phí vận chuyển), insurance (bảo hiểm), cod\_fee, station\_do (giao tại bưu cục), station\_pu (lấy tại bưu cục), return, r2s (trả về người gửi), return\_again, coupon (giảm giá, số âm), document\_return, double\_check, double\_check\_deliver, pick\_remote\_areas\_fee, deliver\_remote\_areas\_fee, pick\_remote\_areas\_fee\_return, deliver\_remote\_areas\_fee\_return, cod\_failed\_fee, change\_to\_address\_fee, change\_return\_address\_fee |
| data.total\_fee | Int | Tổng phí (VND) |
| data.expected\_delivery\_time | String | Thời gian giao dự kiến (ISO 8601\) |

### [Lỗi xác thực (HTTP 400\)](https://developer.ghn.dev/vi/docs/order/create#loi-xac-thuc-http-400)

*Lấy từ một lần gọi thật trên môi trường Staging (sai required\_note).*

jsonSao chép  
{  
  "code": 400,  
  "message": "Sai thông tin Required Note",  
  "data": null,  
  "code\_message": "USER\_ERR\_COMMON",  
  "code\_message\_value": "Sai thông tin đầu vào. Vui lòng thử lại."  
}

### [Không tìm thấy tuyến](https://developer.ghn.dev/vi/docs/order/create#khong-tim-thay-tuyen)

jsonSao chép  
{  
  "code": 400,  
  "message": "calculate fee error: calculate service fee error: route not found service",  
  "data": null  
}

## [Bảng mã lỗi](https://developer.ghn.dev/vi/docs/order/create#bang-ma-loi)

| code\_message | HTTP | Khi nào |
| ----- | ----- | ----- |
|  |  |  |
| USER\_ERR\_COMMON | 400 | Lỗi xác thực / thiếu trường bắt buộc / required\_note không hợp lệ |
| PHONE\_INVALID | 400 | SĐT người gửi hoặc người nhận sai định dạng |
| CLIENT\_NOT\_OWNER\_OF\_SHOP | 400 | Token không sở hữu shop |
| ROUTE\_NOT\_FOUND\_SERVICE | 400 | Không có tuyến cho cặp quận/huyện lấy → giao với service\_type\_id đã chọn |
| SERVICE\_NOT\_FOUND\_CONFIG\_FEE | 400 | Dịch vụ áp dụng cho tuyến này chưa có cấu hình phí |
| SERVER\_ERROR\_COMMON | 500 | Lỗi hệ thống |

# **Cập nhật đơn**

Cập nhật một đơn đã tồn tại. Đây là cập nhật một phần — chỉ những trường bạn gửi trong body mới bị thay đổi; các trường còn lại giữ nguyên. Mọi trường của [Tạo đơn](https://developer.ghn.dev/vi/docs/order/create#tham-so) đều được chấp nhận ở đây; bảng dưới đây liệt kê các trường có hành vi riêng khi cập nhật.

## [Endpoint](https://developer.ghn.dev/vi/docs/order/update#endpoint)

| Môi trường | URL |
| ----- | ----- |
|  |  |
| Production | https\://online-gateway.ghn.vn/shiip/public-api/v2/shipping-order/update |
| Staging | https\://dev-online-gateway.ghn.vn/shiip/public-api/v2/shipping-order/update |

**Method:POST**

## [Headers](https://developer.ghn.dev/vi/docs/order/update#headers)

| Header | Bắt buộc | Mô tả |
| ----- | ----- | ----- |
|  |  |  |
| Content-Type | Có | application/json |
| Token | Có | Token của bạn (do GHN cấp) — [lấy token](https://developer.ghn.dev/vi/docs/token/get-token) |
| ShopId | Có | Shop ID (số nguyên) |

## [Tham số](https://developer.ghn.dev/vi/docs/order/update#tham-so)

Gửi order\_code cùng với chỉ những trường bạn muốn thay đổi. **Mọi trường của [Tạo đơn](https://developer.ghn.dev/vi/docs/order/create#tham-so) đều được chấp nhận với cùng ý nghĩa** — gửi để thay đổi, bỏ qua để giữ nguyên. Chỉ các trường dưới đây là riêng của Cập nhật; các trường còn lại xem ở Tạo đơn.

| Tham số | Kiểu | Bắt buộc | Mô tả |
| ----- | ----- | ----- | ----- |
|  |  |  |  |
| order\_code | String | Bắt buộc | Mã vận đơn GHN của đơn cần cập nhật. Phải thuộc tài khoản của bạn |
| cod\_amount | Int | Tùy chọn | Số tiền COD mới (VND). Chỉ áp dụng khi khác giá trị hiện tại. **Thay đổi giá trị này cần otp** (xem bên dưới) |
| otp | String | Tùy chọn | Mã dùng một lần, **chỉ bắt buộc khi thay đổi cod\_amount**. GHN gửi mã về SĐT đã đăng ký của tài khoản. Mã sai/thiếu sẽ lỗi OTP\_NOT\_VALID |

## [Trường nào được cập nhật theo trạng thái](https://developer.ghn.dev/vi/docs/order/update#truong-nao-duoc-cap-nhat-theo-trang-thai)

Một trường chỉ được thay đổi nếu trạng thái hiện tại của đơn cho phép. ready\_to\_pick cho phép tất cả; tập trường thu hẹp dần khi đơn đi qua mạng lưới; các trạng thái kết thúc như đã giao / đã hoàn / đã hủy không cho phép thay đổi gì. Viết tắt: **thông tin người gửi** gồm from\_\*, **thông tin người nhận** gồm to\_\* (tên/SĐT/địa chỉ), **thông tin hoàn** gồm return\_\*, **dịch vụ** gồm service\_type\_id.

| Trạng thái đơn | Trường được cập nhật |
| ----- | ----- |
|  |  |
| ready\_to\_pick | Tất cả các trường |
| picking | Mọi trường trừ thông tin người gửi, pick\_shift, pickup\_time, pick\_station\_id |
| money\_collect\_picking | order\_value, to\_name/to\_phone, weight, content, required\_note, note, cod\_amount, cod\_failed\_amount, thông tin hoàn, coupon, items |
| picked | dịch vụ, thông tin người nhận, weight, content, required\_note, note, cod\_amount, cod\_failed\_amount, thông tin hoàn, coupon, items, document\_return, double\_check |
| storing, transporting, sorting | dịch vụ, thông tin người nhận, content, required\_note, note, cod\_amount, cod\_failed\_amount, thông tin hoàn, coupon, document\_return, double\_check |
| delivering | dịch vụ, thông tin người nhận, required\_note, note, cod\_amount, cod\_failed\_amount, thông tin hoàn, document\_return, double\_check |
| delivery\_fail | dịch vụ, thông tin người nhận, content, required\_note, note, cod\_amount, thông tin hoàn, document\_return, double\_check |
| waiting\_to\_return | dịch vụ, thông tin người nhận, content, required\_note, note, cod\_amount, cod\_failed\_amount, thông tin hoàn, document\_return, double\_check |
| return, return\_transporting, return\_sorting | chỉ liên hệ hoàn (return\_name, return\_phone) |
| return\_fail | thông tin hoàn (return\_name, return\_phone, return\_address) |
| delivered, money\_collect\_delivering, returning, returned, cancel, exception, lost, damage | Không — đơn không thể chỉnh sửa nữa |

## [Ví dụ Request](https://developer.ghn.dev/vi/docs/order/update#vi-du-request)

Cập nhật các trường ngoài COD (không cần OTP)bashSao chép  
curl \-X POST https\://dev-online-gateway.ghn.vn/shiip/public-api/v2/shipping-order/update \\  
 \-H "Token: 5c1d8a9e-2f4b-11ed-..." \\  
 \-H "ShopId: 92837" \\  
 \-H "Content-Type: application/json" \\  
 \-d '{  
    "order\_code": "LADAK9",  
    "note": "Giao trong giờ hành chính",  
    "weight": 700  
  }'  
Thay đổi COD (cần otp)bashSao chép  
curl \-X POST https\://dev-online-gateway.ghn.vn/shiip/public-api/v2/shipping-order/update \\  
 \-H "Token: 5c1d8a9e-2f4b-11ed-..." \\  
 \-H "ShopId: 92837" \\  
 \-H "Content-Type: application/json" \\  
 \-d '{  
    "order\_code": "LADAK9",  
    "cod\_amount": 300000,  
    "otp": "123456"  
  }'

## [Response](https://developer.ghn.dev/vi/docs/order/update#response)

### [Thành công (HTTP 200\)](https://developer.ghn.dev/vi/docs/order/update#thanh-cong-http-200)

*Lấy từ một lần gọi thật trên môi trường Staging.*

jsonSao chép  
{  
  "code": 200,  
  "message": "Success",  
  "data": null  
}

### [Các trường Response](https://developer.ghn.dev/vi/docs/order/update#cac-truong-response)

| Trường | Kiểu | Mô tả |
| ----- | ----- | ----- |
|  |  |  |
| code | Int | Mã HTTP (200 nếu thành công) |
| message | String | Thông báo kết quả (Success) |
| data | null | Luôn null với API này |

### [Lỗi — thay đổi COD mà không có OTP hợp lệ (HTTP 400\)](https://developer.ghn.dev/vi/docs/order/update#loi-thay-doi-cod-ma-khong-co-otp-hop-le-http-400)

*Lấy từ một lần gọi thật trên môi trường Staging (cod\_amount thay đổi, không có otp).*

jsonSao chép  
{  
  "code": 400,  
  "message": "Check OTP fail: Lỗi gọi API: notify\_check\_sms\_otp \- Key: 'myRequest.OTP' Error:Field validation for 'OTP' failed on the 'required' tag",  
  "data": null,  
  "code\_message": "OTP\_NOT\_VALID",  
  "code\_message\_value": "Mã OTP đã hết hạn hoặc không đúng."  
}

## [Bảng mã lỗi](https://developer.ghn.dev/vi/docs/order/update#bang-ma-loi)

| code\_message | HTTP | Khi nào |
| ----- | ----- | ----- |
|  |  |  |
| USER\_ERR\_COMMON | 400 | Thiếu order\_code hoặc dữ liệu không hợp lệ |
| OTP\_NOT\_VALID | 400 | Thay đổi cod\_amount với otp thiếu/sai/hết hạn |
| PERMISSION\_DENIED\_EDIT\_COD | 400 | Tài khoản nhân viên không có quyền "Sửa COD" cố thay đổi COD |
| PHONE\_INVALID | 400 | SĐT người gửi/nhận/trả sai định dạng |
| PICKUP\_TIME\_ERROR | 400 | Thời gian lấy hàng yêu cầu nằm trong quá khứ |
| CLIENT\_NOT\_OWNER\_OF\_SHOP | 400 | Đơn không thuộc tài khoản của bạn |
| SERVER\_ERROR\_COMMON | 500 | Lỗi hệ thống |

# **Cập nhật COD của đơn**

Thay đổi số tiền thu hộ (COD) cần thu từ người nhận cho một đơn đã tồn tại.

## [Endpoint](https://developer.ghn.dev/vi/docs/order/update-cod#endpoint)

| Môi trường | URL |
| ----- | ----- |
|  |  |
| Production | https\://online-gateway.ghn.vn/shiip/public-api/v2/shipping-order/updateCOD |
| Staging | https\://dev-online-gateway.ghn.vn/shiip/public-api/v2/shipping-order/updateCOD |

**Method:POST**

## [Headers](https://developer.ghn.dev/vi/docs/order/update-cod#headers)

| Header | Bắt buộc | Mô tả |
| ----- | ----- | ----- |
|  |  |  |
| Content-Type | Có | application/json |
| Token | Có | Token của bạn (do GHN cấp) — [lấy token](https://developer.ghn.dev/vi/docs/token/get-token) |
| ShopId | Có | Shop ID (số nguyên) |

## [Tham số](https://developer.ghn.dev/vi/docs/order/update-cod#tham-so)

| Tham số | Kiểu | Bắt buộc | Mô tả |
| ----- | ----- | ----- | ----- |
|  |  |  |  |
| order\_code | String | Bắt buộc | Mã vận đơn GHN. Đơn phải thuộc tài khoản của bạn (bạn là người sở hữu hoặc người tạo), nếu không request sẽ bị từ chối |
| cod\_amount | Int | Bắt buộc | Số tiền COD mới cần thu từ người nhận (VND). Tối đa 5,000,000 (do backend GHN kiểm soát). 0 để xóa COD |

Chỉ gửi hai trường này. Mọi trường thừa sẽ được chuyển thẳng vào hệ thống cập nhật đơn của GHN mà không kiểm tra — hãy giữ payload tối giản.

## [Ví dụ Request](https://developer.ghn.dev/vi/docs/order/update-cod#vi-du-request)

bashSao chép  
curl \-X POST https\://dev-online-gateway.ghn.vn/shiip/public-api/v2/shipping-order/updateCOD \\  
 \-H "Token: 5c1d8a9e-2f4b-11ed-..." \\  
 \-H "ShopId: 92837" \\  
 \-H "Content-Type: application/json" \\  
 \-d '{  
    "order\_code": "LADHFA",  
    "cod\_amount": 350000  
  }'

## [Response](https://developer.ghn.dev/vi/docs/order/update-cod#response)

### [Thành công (HTTP 200\)](https://developer.ghn.dev/vi/docs/order/update-cod#thanh-cong-http-200)

*Lấy từ một lần gọi thật trên môi trường Staging.*

jsonSao chép  
{  
  "code": 200,  
  "message": "Success",  
  "data": null  
}

### [Các trường Response](https://developer.ghn.dev/vi/docs/order/update-cod#cac-truong-response)

| Trường | Kiểu | Mô tả |
| ----- | ----- | ----- |
|  |  |  |
| code | Int | Mã HTTP (200 nếu thành công) |
| message | String | Thông báo kết quả (Success) |
| data | null | Luôn null với API này |

### [Lỗi — đơn không tồn tại / không thuộc về bạn (HTTP 400\)](https://developer.ghn.dev/vi/docs/order/update-cod#loi-don-khong-ton-tai-khong-thuoc-ve-ban-http-400)

*Lấy từ một lần gọi thật trên môi trường Staging (mã đơn không tồn tại).*

jsonSao chép  
{  
  "code": 400,  
  "message": "Lỗi gọi API: corev2\_tenant\_order\_detail \- Đơn hàng không tồn tại",  
  "data": null  
}  
Phần có ý nghĩa của message là đoạn sau dấu \- cuối cùng (ở đây là Đơn hàng không tồn tại). Phần đầu là tham chiếu nội bộ — hãy parse code/code\_message, đừng dựa vào message.

## [Bảng mã lỗi](https://developer.ghn.dev/vi/docs/order/update-cod#bang-ma-loi)

| Điều kiện | HTTP | Ý nghĩa |
| ----- | ----- | ----- |
|  |  |  |
| Đơn không tồn tại (Đơn hàng không tồn tại) | 400 | Mã đơn không tồn tại |
| Không có quyền (Không có quyền thao tác) | 400 | Đơn không thuộc tài khoản của bạn (bạn không phải người sở hữu hay người tạo) |
| USER\_ERR\_COMMON | 400 | Thiếu order\_code hoặc dữ liệu không hợp lệ |
| SERVER\_ERROR\_COMMON | 500 | Lỗi hệ thống |

# **Hủy đơn**

## [Endpoint](https://developer.ghn.dev/vi/docs/order/cancel#endpoint)

| Môi trường | URL |
| ----- | ----- |
|  |  |
| Production | https\://online-gateway.ghn.vn/shiip/public-api/v2/switch-status/cancel |
| Staging | https\://dev-online-gateway.ghn.vn/shiip/public-api/v2/switch-status/cancel |

**Method:POST**

## [Headers](https://developer.ghn.dev/vi/docs/order/cancel#headers)

| Header | Bắt buộc | Mô tả |
| ----- | ----- | ----- |
|  |  |  |
| Content-Type | Có | application/json |
| Token | Có | Token của bạn (do GHN cấp) — [lấy token](https://developer.ghn.dev/vi/docs/token/get-token) |
| ShopId | Có | Shop ID (số nguyên) |

## [Tham số](https://developer.ghn.dev/vi/docs/order/cancel#tham-so)

| Tham số | Kiểu | Bắt buộc | Mô tả |
| ----- | ----- | ----- | ----- |
|  |  |  |  |
| order\_codes | String\[\] | Bắt buộc | Danh sách mã vận đơn GHN cần hủy, gửi theo lô. **Tất-cả-hoặc-không**: nếu có bất kỳ mã nào không tồn tại hoặc không thuộc tài khoản của bạn thì toàn bộ yêu cầu bị từ chối và không đơn nào bị hủy (xem phần Response) |
| reason\_code | String | Tùy chọn | Lý do hủy, áp dụng cho mọi đơn trong lô. Một trong các giá trị:GHN-CO001: thời gian lấy hàng quá lâuGHN-CO002: hết hàng, chưa có hàng để gửiGHN-CO003: người nhận không còn nhu cầu nữaGHN-CANCEL-OTHER: lý do khácDanh mục đầy đủ mọi mã GHN dùng: [Mã lý do](https://developer.ghn.dev/vi/docs/master-data/reason-code) |
| reason | String | Tùy chọn | Ghi chú lý do hủy dạng văn bản tự do, áp dụng cho mọi đơn trong lô. Dùng để bổ sung chi tiết, ví dụ đi kèm GHN-CANCEL-OTHER. Mặc định: không có |

## [Ví dụ Request](https://developer.ghn.dev/vi/docs/order/cancel#vi-du-request)

bashSao chép  
curl \-X POST https\://dev-online-gateway.ghn.vn/shiip/public-api/v2/switch-status/cancel \\  
 \-H "Token: 5c1d8a9e-2f4b-11ed-..." \\  
 \-H "ShopId: 92837" \\  
 \-H "Content-Type: application/json" \\  
 \-d '{  
    "order\_codes": \["LADTC8", "LADFYR"\],  
    "reason\_code": "GHN-CO001",  
    "reason": "Khách đổi ý, không gửi nữa"  
  }'

## [Response](https://developer.ghn.dev/vi/docs/order/cancel#response)

### [Thành công (HTTP 200\)](https://developer.ghn.dev/vi/docs/order/cancel#thanh-cong-http-200)

*Lấy từ một lần gọi thật trên môi trường Staging.*

jsonSao chép  
{  
  "code": 200,  
  "message": "Success",  
  "data": \[  
    {  
      "order\_code": "LADTC8",  
      "result": true,  
      "message": "OK"  
    }  
  \]  
}

### [Các trường Response](https://developer.ghn.dev/vi/docs/order/cancel#cac-truong-response)

| Trường | Kiểu | Mô tả |
| ----- | ----- | ----- |
|  |  |  |
| code | Int | Mã HTTP (200 nếu thành công) |
| message | String | Thông báo kết quả (Success) |
| data | Object\[\] | Mỗi phần tử tương ứng một mã đơn trong request |
| data\[\].order\_code | String | Mã đơn của phần tử này |
| data\[\].result | Bool | true nếu đơn đã được hủy; false nếu GHN từ chối (ví dụ đơn đã ở trạng thái không thể hủy) |
| data\[\].message | String | Thông báo kết quả cho từng đơn (ví dụ OK) |

### [Lỗi — đơn không tồn tại / không thuộc về bạn (HTTP 400\)](https://developer.ghn.dev/vi/docs/order/cancel#loi-don-khong-ton-tai-khong-thuoc-ve-ban-http-400)

\_Lấy từ một lần gọi thật trên môi trường Staging (mã đơn không tồn tại).\_ Vì lô là tất-cả-hoặc-không, chỉ cần một mã sai là toàn bộ yêu cầu bị hủy bỏ với data: null — không đơn nào trong lô được hủy.

jsonSao chép  
{  
  "code": 400,  
  "message": "Lỗi gọi API: corev2\_tenant\_order\_detail \- Đơn hàng không tồn tại",  
  "data": null  
}  
Phần có ý nghĩa của message là đoạn sau dấu \- cuối cùng (ở đây là Đơn hàng không tồn tại). Phần đầu là tham chiếu nội bộ.

## [Bảng mã lỗi](https://developer.ghn.dev/vi/docs/order/cancel#bang-ma-loi)

| Điều kiện | HTTP | Ý nghĩa |
| ----- | ----- | ----- |
|  |  |  |
| Đơn không tồn tại (Đơn hàng không tồn tại) | 400 | Một trong các mã đơn không tồn tại hoặc không thuộc tài khoản của bạn. Toàn bộ lô bị hủy bỏ; không đơn nào được hủy |
| result: false trong data | 200 | Mã đơn hợp lệ nhưng trạng thái hiện tại không còn cho phép hủy |
| CLIENT\_NOT\_OWNER\_OF\_SHOP | 400 | Token không sở hữu shop |
| SERVER\_ERROR\_COMMON | 500 | Lỗi hệ thống |

# **Trả hàng**

Buộc trả một hoặc nhiều đơn về người gửi. Đơn phải đang ở trong mạng lưới GHN tại giai đoạn có thể trả — xem order\_codes bên dưới.

## [Endpoint](https://developer.ghn.dev/vi/docs/order/return#endpoint)

| Môi trường | URL |
| ----- | ----- |
|  |  |
| Production | https\://online-gateway.ghn.vn/shiip/public-api/v2/switch-status/return |
| Staging | https\://dev-online-gateway.ghn.vn/shiip/public-api/v2/switch-status/return |

**Method:POST**

## [Headers](https://developer.ghn.dev/vi/docs/order/return#headers)

| Header | Bắt buộc | Mô tả |
| ----- | ----- | ----- |
|  |  |  |
| Content-Type | Có | application/json |
| Token | Có | Token của bạn (do GHN cấp) — [lấy token](https://developer.ghn.dev/vi/docs/token/get-token) |
| ShopId | Có | Shop ID (số nguyên) |

## [Tham số](https://developer.ghn.dev/vi/docs/order/return#tham-so)

| Tham số | Kiểu | Bắt buộc | Mô tả |
| ----- | ----- | ----- | ----- |
|  |  |  |  |
| order\_codes | String\[\] | Bắt buộc | Danh sách mã vận đơn GHN cần buộc trả, gửi theo lô. Mỗi đơn phải đang ở trạng thái có thể trả — delivery\_fail, storing, waiting\_to\_return, hoặc return; trạng thái khác sẽ trả về result: false (xem Response). Xử lý **theo từng đơn** (best-effort), không phải tất-cả-hoặc-không |

## [Ví dụ Request](https://developer.ghn.dev/vi/docs/order/return#vi-du-request)

bashSao chép  
curl \-X POST https\://dev-online-gateway.ghn.vn/shiip/public-api/v2/switch-status/return \\  
 \-H "Token: 5c1d8a9e-2f4b-11ed-..." \\  
 \-H "ShopId: 92837" \\  
 \-H "Content-Type: application/json" \\  
 \-d '{  
    "order\_codes": \["LADHFT", "LADFYR"\]  
  }'

## [Response](https://developer.ghn.dev/vi/docs/order/return#response)

### [Kết quả (HTTP 200\)](https://developer.ghn.dev/vi/docs/order/return#ket-qua-http-200)

**Best-effort theo từng đơn**: mỗi mã được xử lý độc lập, nên response luôn là HTTP 200 với một phần tử cho mỗi mã — kể cả khi tất cả đều thất bại. **Luôn kiểm tra result của từng phần tử**; HTTP 200 không có nghĩa là mọi đơn đã được trả.

\_Lấy từ một lần gọi thật trên môi trường Staging\_ (một đơn chưa ở trạng thái có thể trả, cộng một mã không tồn tại — cả hai đều thất bại theo từng đơn)jsonSao chép  
{  
  "code": 200,  
  "message": "Success",  
  "data": \[  
    {  
      "order\_code": "LADHFT",  
      "result": false,  
      "message": "Trạng thái đơn hàng không hợp lệ"  
    },  
    {  
      "order\_code": "NOTREAL9",  
      "result": false,  
      "message": "Order code không tồn tại."  
    }  
  \]  
}  
Một phần tử thành công có result: true và message: "OK".

### [Các trường Response](https://developer.ghn.dev/vi/docs/order/return#cac-truong-response)

| Trường | Kiểu | Mô tả |
| ----- | ----- | ----- |
|  |  |  |
| code | Int | Mã HTTP (200 nếu thành công) |
| message | String | Thông báo kết quả tổng (Success) |
| data | Object\[\] | Mỗi phần tử tương ứng một mã đơn trong request |
| data\[\].order\_code | String | Mã đơn của phần tử này |
| data\[\].result | Bool | true nếu đã trả hàng; false nếu đơn bị từ chối |
| data\[\].message | String | Thông báo từng đơn. OK khi thành công; khi thất bại là một trong:Trạng thái đơn hàng không hợp lệ — trạng thái đơn không cho phép trảOrder code không tồn tại. — mã đơn không tồn tạiKhách hàng không có quyền thay đổi trạng thái đơn hàng. — đơn không thuộc tài khoản của bạn |

## [Bảng mã lỗi](https://developer.ghn.dev/vi/docs/order/return#bang-ma-loi)

| Điều kiện | HTTP | Ý nghĩa |
| ----- | ----- | ----- |
|  |  |  |
| result: false trong data | 200 | Thất bại theo từng đơn — kiểm tra message của phần tử (trạng thái không cho trả / mã không tồn tại / không phải đơn của bạn) |
| CLIENT\_NOT\_OWNER\_OF\_SHOP | 400 | Token không sở hữu shop |
| SERVER\_ERROR\_COMMON | 500 | Lỗi hệ thống |

# **n đơn**

## [Endpoint](https://developer.ghn.dev/vi/docs/order/print#endpoint)

| Môi trường | URL |
| ----- | ----- |
|  |  |
| Production | https\://online-gateway.ghn.vn/shiip/public-api/v2/a5/gen-token |
| Staging | https\://dev-online-gateway.ghn.vn/shiip/public-api/v2/a5/gen-token |

**Method:POST**

## [Headers](https://developer.ghn.dev/vi/docs/order/print#headers)

| Header | Bắt buộc | Mô tả |
| ----- | ----- | ----- |
|  |  |  |
| Content-Type | Có | application/json |
| Token | Có | Token của bạn (do GHN cấp) — [lấy token](https://developer.ghn.dev/vi/docs/token/get-token) |
| ShopId | Có | Shop ID (số nguyên) |

## [Tham số](https://developer.ghn.dev/vi/docs/order/print#tham-so)

| Tham số | Kiểu | Bắt buộc | Mô tả |
| ----- | ----- | ----- | ----- |
|  |  |  |  |
| order\_codes | String\[\] | Bắt buộc | Danh sách mã vận đơn GHN đưa vào token in. **Tối đa 10,000** mã mỗi request. Mỗi mã phải là đơn tồn tại và thuộc tài khoản của bạn — **tất-cả-hoặc-không**: chỉ một mã sai hoặc không thuộc về bạn là toàn bộ request thất bại và không token nào được tạo |
| item\_index | Int\[\] | Tùy chọn | Với đơn nhiều sản phẩm: vị trí (đánh số từ 0\) của các sản phẩm cần in. Mặc định: in tất cả |

## [Ví dụ Request](https://developer.ghn.dev/vi/docs/order/print#vi-du-request)

bashSao chép  
curl \-X POST https\://dev-online-gateway.ghn.vn/shiip/public-api/v2/a5/gen-token \\  
 \-H "Token: 5c1d8a9e-2f4b-11ed-..." \\  
 \-H "ShopId: 92837" \\  
 \-H "Content-Type: application/json" \\  
 \-d '{  
    "order\_codes": \["LADT8L", "LADFYR"\]  
  }'

## [Response](https://developer.ghn.dev/vi/docs/order/print#response)

### [Thành công (HTTP 200\)](https://developer.ghn.dev/vi/docs/order/print#thanh-cong-http-200)

*Lấy từ một lần gọi thật trên môi trường Staging.*

jsonSao chép  
{  
  "code": 200,  
  "message": "Success",  
  "data": {  
    "token": "1b744cae-8005-11f1-b50f-ba37616041ec"  
  }  
}

### [Các trường Response](https://developer.ghn.dev/vi/docs/order/print#cac-truong-response)

| Trường | Kiểu | Mô tả |
| ----- | ----- | ----- |
|  |  |  |
| code | Int | Mã HTTP (200 nếu thành công) |
| message | String | Thông báo kết quả (Success) |
| data.token | String | Token in cho các đơn đã yêu cầu. Gắn token vào URL in bên dưới để mở nhãn vận đơn |

### [Cách dùng token](https://developer.ghn.dev/vi/docs/order/print#cach-dung-token)

Mở nhãn in bằng cách gắn token vào một trong các URL sau (trang in do hệ thống in của GHN phục vụ). Chọn khổ giấy bạn cần:

| Khổ | URL Production |
| ----- | ----- |
|  |  |
| A5 | https\://online-gateway.ghn.vn/a5/public-api/printA5?token=\<token\> |
| 80×80 mm | https\://online-gateway.ghn.vn/a5/public-api/print80x80?token=\<token\> |
| 52×70 mm | https\://online-gateway.ghn.vn/a5/public-api/print52x70?token=\<token\> |

Trên môi trường Staging, dùng cùng đường dẫn trên https\://dev-online-gateway.ghn.vn. Token hết hạn sau khoảng **30 phút** — hãy tạo ngay trước khi in.

### [Lỗi — đơn không tồn tại / không thuộc về bạn (HTTP 400\)](https://developer.ghn.dev/vi/docs/order/print#loi-don-khong-ton-tai-khong-thuoc-ve-ban-http-400)

\_Lấy từ một lần gọi thật trên môi trường Staging (mã đơn không tồn tại).\_ Tất-cả-hoặc-không: một mã sai là toàn bộ request bị hủy với data: null — không token nào được tạo.

jsonSao chép  
{  
  "code": 400,  
  "message": "Lỗi gọi API: corev2\_tenant\_order\_detail \- Đơn hàng không tồn tại",  
  "data": null,  
  "code\_message": "ORDER\_NOT\_FOUND",  
  "code\_message\_value": "Không tìm thấy thông tin đơn hàng. Vui lòng thử lại sau."  
}

## [Bảng mã lỗi](https://developer.ghn.dev/vi/docs/order/print#bang-ma-loi)

| code\_message | HTTP | Khi nào |
| ----- | ----- | ----- |
|  |  |  |
| ORDER\_NOT\_FOUND | 400 | Một mã đơn không tồn tại hoặc không thuộc tài khoản của bạn. Toàn bộ lô bị hủy; không token nào được tạo |
| CLIENT\_NOT\_OWNER\_OF\_SHOP | 400 | Một đơn thuộc shop mà tài khoản của bạn không phải thành viên |
| Vượt giới hạn (Not support over 10000 orders) | 400 | Quá 10,000 mã trong một request |
| SERVER\_ERROR\_COMMON | 500 | Lỗi hệ thống |

# **Thông tin đơn**

Lấy chi tiết đầy đủ của một đơn theo mã vận đơn GHN.

## [Endpoint](https://developer.ghn.dev/vi/docs/order/info#endpoint)

| Môi trường | URL |
| ----- | ----- |
|  |  |
| Production | https\://online-gateway.ghn.vn/shiip/public-api/v2/shipping-order/detail |
| Staging | https\://dev-online-gateway.ghn.vn/shiip/public-api/v2/shipping-order/detail |

**Method:GET**

## [Headers](https://developer.ghn.dev/vi/docs/order/info#headers)

| Header | Bắt buộc | Mô tả |
| ----- | ----- | ----- |
|  |  |  |
| Token | Có | Token của bạn (do GHN cấp) — [lấy token](https://developer.ghn.dev/vi/docs/token/get-token) |
| ShopId | Có | Shop ID (số nguyên) |

## [Tham số](https://developer.ghn.dev/vi/docs/order/info#tham-so)

| Tham số | Kiểu | Bắt buộc | Mô tả |
| ----- | ----- | ----- | ----- |
|  |  |  |  |
| order\_code | String | Bắt buộc | Mã vận đơn GHN. Đơn phải thuộc shop mà tài khoản của bạn là thành viên |

## [Ví dụ Request](https://developer.ghn.dev/vi/docs/order/info#vi-du-request)

bashSao chép  
curl \-X GET "https\://dev-online-gateway.ghn.vn/shiip/public-api/v2/shipping-order/detail?order\_code=LADH94" \\  
 \-H "Token: 5c1d8a9e-2f4b-11ed-..." \\  
 \-H "ShopId: 92837"

## [Response](https://developer.ghn.dev/vi/docs/order/info#response)

data là **một object đơn hàng** (không phải mảng). GHN trả về \~120 trường; phần mẫu và bảng dưới đây chỉ liệt kê các trường thường dùng nhất — các trường vận hành khác (định tuyến kho, bằng chứng giao hàng, dịch vụ cộng thêm, trường địa chỉ định dạng mới, log nội bộ) cũng được trả về và có thể bỏ qua nếu không cần.

### [Thành công (HTTP 200\)](https://developer.ghn.dev/vi/docs/order/info#thanh-cong-http-200)

*Lấy từ một lần gọi thật trên môi trường Staging (rút gọn còn các trường thường dùng).*

jsonSao chép  
{  
  "code": 200,  
  "message": "Success",  
  "data": {  
    "order\_code": "LADH94",  
    "client\_order\_code": "FINALDOC-DETAIL-001",  
    "status": "ready\_to\_pick",  
    "shop\_id": 196560,  
    "client\_id": 2509893,  
    "from\_name": "Shop Áo GHN",  
    "from\_phone": "0395297295",  
    "from\_address": "7, 28 Đ. Thành Thái, Phường 10 (Quận 10), Quận 10, Thành phố Hồ Chí Minh, Vietnam",  
    "to\_name": "Trần Minh Anh",  
    "to\_phone": "0987654321",  
    "to\_address": "72 Lê Thánh Tôn, P. Bến Nghé, Quận 1, TP. Hồ Chí Minh",  
    "return\_name": "Shop Áo GHN",  
    "return\_phone": "0395297295",  
    "weight": 600,  
    "length": 25,  
    "width": 20,  
    "height": 8,  
    "converted\_weight": 800,  
    "calculate\_weight": 800,  
    "service\_type\_id": 2,  
    "service\_id": 53320,  
    "payment\_type\_id": 1,  
    "cod\_amount": 285000,  
    "cod\_collect\_date": null,  
    "cod\_transfer\_date": null,  
    "is\_cod\_collected": false,  
    "is\_cod\_transferred": false,  
    "cod\_failed\_amount": 0,  
    "insurance\_value": 285000,  
    "order\_value": 0,  
    "required\_note": "KHONGCHOXEMHANG",  
    "content": "Áo thun GHN size M \[GHN-TS-M-BLK\] \[2 cái\]",  
    "note": "",  
    "pickup\_shift": {},  
    "leadtime": "2026-07-16T16:59:59Z",  
    "created\_date": "2026-07-15T04:49:47.811Z",  
    "updated\_date": "2026-07-15T04:49:47.811Z",  
    "items": \[  
      {  
        "name": "Áo thun GHN size M",  
        "code": "GHN-TS-M-BLK",  
        "quantity": 2,  
        "length": 10,  
        "width": 10,  
        "height": 10,  
        "weight": 300  
      }  
    \]  
  }  
}

### [Các trường Response](https://developer.ghn.dev/vi/docs/order/info#cac-truong-response)

| Trường | Kiểu | Mô tả |
| ----- | ----- | ----- |
|  |  |  |
| order\_code | String | Mã vận đơn GHN |
| client\_order\_code | String | Mã đơn riêng của bạn |
| status | String | Trạng thái đơn (xem [Mã trạng thái đơn hàng](https://developer.ghn.dev/vi/docs/master-data/order-status)) |
| shop\_id | Int | Shop sở hữu đơn |
| client\_id | Int | Tài khoản sở hữu đơn |
| from\_name | String | Tên người gửi |
| from\_phone | String | SĐT người gửi |
| from\_address | String | Địa chỉ đầy đủ người gửi |
| to\_name | String | Tên người nhận |
| to\_phone | String | SĐT người nhận |
| to\_address | String | Địa chỉ đầy đủ người nhận |
| return\_name | String | Tên liên hệ trả hàng |
| return\_phone | String | SĐT liên hệ trả hàng |
| return\_address | String | Địa chỉ đầy đủ trả hàng |
| weight | Int | Khối lượng khai báo (gram) |
| length | Int | Chiều dài (cm) |
| width | Int | Chiều rộng (cm) |
| height | Int | Chiều cao (cm) |
| converted\_weight | Int | Khối lượng quy đổi thể tích (gram) |
| calculate\_weight | Int | Khối lượng tính cước (gram) — lớn hơn giữa weight và converted\_weight |
| service\_type\_id | Int | Loại dịch vụ, chọn theo khối lượng:2: tổng khối lượng **dưới 20 kg**5: tổng khối lượng **từ 20 kg trở lên**, hoặc đơn nhiều kiện |
| service\_id | Int | Mã dịch vụ vận chuyển |
| payment\_type\_id | Int | Bên trả phí vận chuyển: là 1 (và 4, 5) nếu Shop/Người bán trả, là 2 nếu Người mua/Người nhận trả |
| cod\_amount | Int | COD thu từ người nhận (VND) |
| cod\_collect\_date | String/null | Thời điểm đã thu COD (ISO 8601\) |
| cod\_transfer\_date | String/null | Thời điểm COD đã chuyển cho bạn (ISO 8601\) |
| is\_cod\_collected | Bool | Đã thu COD từ người nhận |
| is\_cod\_transferred | Bool | Đã chuyển COD cho bạn |
| cod\_failed\_amount | Int | Số tiền thu khi giao thất bại (VND) |
| insurance\_value | Int | Giá trị khai giá/bảo hiểm (VND) |
| order\_value | Int | Giá trị đơn hàng (VND) |
| required\_note | String | KHONGCHOXEMHANG / CHOXEMHANGKHONGTHU / CHOTHUHANG |
| content | String | Mô tả hàng hóa |
| note | String | Ghi chú của bạn cho tài xế |
| pickup\_shift | Object | Khung giờ lấy hàng cụ thể; {} khi đơn không có ca lấy. Chỉ API này trả về giá trị này |
| pickup\_shift.from\_time | String | Bắt đầu khung giờ lấy (ISO 8601\) |
| pickup\_shift.to\_time | String | Kết thúc khung giờ lấy (ISO 8601\) |
| leadtime | String | Thời gian giao dự kiến (ISO 8601\) |
| created\_date | String | Thời điểm tạo đơn trong GHN (ISO 8601\) |
| updated\_date | String | Thời điểm cập nhật cuối (ISO 8601\) |
| items | Object\[\] | Danh sách sản phẩm |
| items\[\].name | String | Tên sản phẩm |
| items\[\].code | String | Mã sản phẩm |
| items\[\].quantity | Int | Số lượng |
| items\[\].price | Int | Đơn giá (VND) |
| items\[\].length | Int | Chiều dài sản phẩm (cm) |
| items\[\].width | Int | Chiều rộng sản phẩm (cm) |
| items\[\].height | Int | Chiều cao sản phẩm (cm) |
| items\[\].weight | Int | Khối lượng sản phẩm (gram) |

## [Bảng mã lỗi](https://developer.ghn.dev/vi/docs/order/info#bang-ma-loi)

| Điều kiện | HTTP | Ý nghĩa |
| ----- | ----- | ----- |
|  |  |  |
| Đơn không tồn tại (Đơn hàng không tồn tại) | 400 | Mã đơn không tồn tại |
| 403 | 403 | Tài khoản của bạn thiếu quyền xem chi tiết đơn — liên hệ GHN |
| CLIENT\_NOT\_OWNER\_OF\_SHOP | 400 | Đơn không thuộc shop mà tài khoản của bạn là thành viên |
| SERVER\_ERROR\_COMMON | 500 | Lỗi hệ thống |

# **Thông tin đơn theo mã đơn của shop**

Giống [Thông tin đơn](https://developer.ghn.dev/vi/docs/order/info), nhưng tra cứu đơn theo client\_order\_code của chính bạn thay vì order\_code của GHN. Hữu ích khi bạn quản lý đơn theo mã nội bộ của mình.

## [Endpoint](https://developer.ghn.dev/vi/docs/order/info-by-client-code#endpoint)

| Môi trường | URL |
| ----- | ----- |
|  |  |
| Production | https\://online-gateway.ghn.vn/shiip/public-api/v2/shipping-order/detail-by-client-code |
| Staging | https\://dev-online-gateway.ghn.vn/shiip/public-api/v2/shipping-order/detail-by-client-code |

**Method:GET**

## [Headers](https://developer.ghn.dev/vi/docs/order/info-by-client-code#headers)

| Header | Bắt buộc | Mô tả |
| ----- | ----- | ----- |
|  |  |  |
| Token | Có | Token của bạn (do GHN cấp) — [lấy token](https://developer.ghn.dev/vi/docs/token/get-token) |
| ShopId | Có | Shop ID (số nguyên) |

## [Tham số](https://developer.ghn.dev/vi/docs/order/info-by-client-code#tham-so)

| Tham số | Kiểu | Bắt buộc | Mô tả |
| ----- | ----- | ----- | ----- |
|  |  |  |  |
| client\_order\_code | String | Bắt buộc | Mã đơn riêng của bạn (client\_order\_code bạn đặt khi tạo đơn). Đơn phải thuộc shop mà tài khoản của bạn là thành viên |

## [Ví dụ Request](https://developer.ghn.dev/vi/docs/order/info-by-client-code#vi-du-request)

bashSao chép  
curl \-X GET "https\://dev-online-gateway.ghn.vn/shiip/public-api/v2/shipping-order/detail-by-client-code?client\_order\_code=SHOP-2026-00193" \\  
 \-H "Token: 5c1d8a9e-2f4b-11ed-..." \\  
 \-H "ShopId: 92837"

## [Response](https://developer.ghn.dev/vi/docs/order/info-by-client-code#response)

Giống hệt [Thông tin đơn](https://developer.ghn.dev/vi/docs/order/info#response) — cùng object data và cùng danh sách trường. Chỉ khác một điểm: pickup\_shift luôn là {} ở API này (chỉ API Thông tin đơn mới tính khung giờ lấy hàng).

# **Giao lại**

Yêu cầu giao lại một hoặc nhiều đơn đang ở kho GHN hoặc chờ trả. Đơn phải đang ở trạng thái cho phép giao lại — xem order\_codes bên dưới.

## [Endpoint](https://developer.ghn.dev/vi/docs/order/delivery-again#endpoint)

| Môi trường | URL |
| ----- | ----- |
|  |  |
| Production | https\://online-gateway.ghn.vn/shiip/public-api/v2/switch-status/storing |
| Staging | https\://dev-online-gateway.ghn.vn/shiip/public-api/v2/switch-status/storing |

**Method:POST**

## [Headers](https://developer.ghn.dev/vi/docs/order/delivery-again#headers)

| Header | Bắt buộc | Mô tả |
| ----- | ----- | ----- |
|  |  |  |
| Content-Type | Có | application/json |
| Token | Có | Token của bạn (do GHN cấp) — [lấy token](https://developer.ghn.dev/vi/docs/token/get-token) |
| ShopId | Có | Shop ID (số nguyên) |

## [Tham số](https://developer.ghn.dev/vi/docs/order/delivery-again#tham-so)

| Tham số | Kiểu | Bắt buộc | Mô tả |
| ----- | ----- | ----- | ----- |
|  |  |  |  |
| order\_codes | String\[\] | Bắt buộc | Danh sách mã vận đơn GHN cần yêu cầu giao lại, gửi theo lô. Mỗi đơn phải đang ở trạng thái storing hoặc waiting\_to\_return; trạng thái khác sẽ trả về result: false (xem Response). Xử lý **theo từng đơn** (best-effort), không phải tất-cả-hoặc-không |

## [Ví dụ Request](https://developer.ghn.dev/vi/docs/order/delivery-again#vi-du-request)

bashSao chép  
curl \-X POST https\://dev-online-gateway.ghn.vn/shiip/public-api/v2/switch-status/storing \\  
 \-H "Token: 5c1d8a9e-2f4b-11ed-..." \\  
 \-H "ShopId: 92837" \\  
 \-H "Content-Type: application/json" \\  
 \-d '{  
    "order\_codes": \["LADHUX", "LADFYR"\]  
  }'

## [Response](https://developer.ghn.dev/vi/docs/order/delivery-again#response)

### [Kết quả (HTTP 200\)](https://developer.ghn.dev/vi/docs/order/delivery-again#ket-qua-http-200)

**Best-effort theo từng đơn**: mỗi mã được xử lý độc lập, nên response luôn là HTTP 200 với một phần tử cho mỗi mã — kể cả khi tất cả đều thất bại. **Luôn kiểm tra result của từng phần tử**; HTTP 200 không có nghĩa là mọi đơn đã được chấp nhận.

\_Lấy từ một lần gọi thật trên môi trường Staging\_ (một đơn không ở trạng thái cho phép giao lại, cộng một mã không tồn tại — cả hai đều thất bại theo từng đơn)jsonSao chép  
{  
  "code": 200,  
  "message": "Success",  
  "data": \[  
    {  
      "order\_code": "LADHUX",  
      "result": false,  
      "message": "Trạng thái đơn hàng không hợp lệ"  
    },  
    {  
      "order\_code": "NOTREAL9",  
      "result": false,  
      "message": "Order code không tồn tại."  
    }  
  \]  
}  
Một phần tử thành công có result: true và message: "OK".

### [Các trường Response](https://developer.ghn.dev/vi/docs/order/delivery-again#cac-truong-response)

| Trường | Kiểu | Mô tả |
| ----- | ----- | ----- |
|  |  |  |
| code | Int | Mã HTTP (200 nếu thành công) |
| message | String | Thông báo kết quả tổng (Success) |
| data | Object\[\] | Mỗi phần tử tương ứng một mã đơn trong request |
| data\[\].order\_code | String | Mã đơn của phần tử này |
| data\[\].result | Bool | true nếu yêu cầu giao lại được chấp nhận; false nếu đơn bị từ chối |
| data\[\].message | String | Thông báo từng đơn. OK khi thành công; khi thất bại là một trong:Trạng thái đơn hàng không hợp lệ — trạng thái đơn không cho phép giao lạiOrder code không tồn tại. — mã đơn không tồn tạiKhách hàng không có quyền thay đổi trạng thái đơn hàng. — đơn không thuộc tài khoản của bạn |

## [Bảng mã lỗi](https://developer.ghn.dev/vi/docs/order/delivery-again#bang-ma-loi)

| Điều kiện | HTTP | Ý nghĩa |
| ----- | ----- | ----- |
|  |  |  |
| result: false trong data | 200 | Thất bại theo từng đơn — kiểm tra message của phần tử (trạng thái không cho giao lại / mã không tồn tại / không phải đơn của bạn) |
| CLIENT\_NOT\_OWNER\_OF\_SHOP | 400 | Token không sở hữu shop |
| SERVER\_ERROR\_COMMON | 500 | Lỗi hệ thống |

# **Xem trước đơn**

Xem trước là bản chạy thử của [Tạo đơn](https://developer.ghn.dev/vi/docs/order/create): cùng request body, cùng logic kiểm tra, cùng cách tính phí/thời gian giao — nhưng không tạo đơn nào. Dùng để hiển thị phí vận chuyển và thời gian giao dự kiến trước khi khách xác nhận.

## [Endpoint](https://developer.ghn.dev/vi/docs/order/preview#endpoint)

| Môi trường | URL |
| ----- | ----- |
|  |  |
| Production | https\://online-gateway.ghn.vn/shiip/public-api/v2/shipping-order/preview |
| Staging | https\://dev-online-gateway.ghn.vn/shiip/public-api/v2/shipping-order/preview |

**Method:POST**

## [Headers](https://developer.ghn.dev/vi/docs/order/preview#headers)

| Header | Bắt buộc | Mô tả |
| ----- | ----- | ----- |
|  |  |  |
| Content-Type | Có | application/json |
| Token | Có | Token của bạn (do GHN cấp) — [lấy token](https://developer.ghn.dev/vi/docs/token/get-token) |
| ShopId | Có | Shop ID (số nguyên) |

## [Tham số](https://developer.ghn.dev/vi/docs/order/preview#tham-so)

**Giống hệt [Tạo đơn](https://developer.ghn.dev/vi/docs/order/create#tham-so)** — cùng các trường, cùng quy tắc bắt buộc, cùng giá trị mặc định. Xem trang đó để có danh sách đầy đủ.

## [Ví dụ Request](https://developer.ghn.dev/vi/docs/order/preview#vi-du-request)

bashSao chép  
curl \-X POST https\://dev-online-gateway.ghn.vn/shiip/public-api/v2/shipping-order/preview \\  
 \-H "Token: 5c1d8a9e-2f4b-11ed-..." \\  
 \-H "ShopId: 92837" \\  
 \-H "Content-Type: application/json" \\  
 \-d '{  
    "payment\_type\_id": 2,  
    "required\_note": "CHOXEMHANGKHONGTHU",  
    "to\_name": "Trần Minh Anh",  
    "to\_phone": "0987654321",  
    "to\_address": "72 Lê Thánh Tôn, P. Bến Nghé, Quận 1, TP. Hồ Chí Minh",  
    "to\_ward\_name": "Phường Bến Nghé",  
    "to\_district\_name": "Quận 1",  
    "to\_province\_name": "TP. Hồ Chí Minh",  
    "weight": 600,  
    "length": 25,  
    "width": 20,  
    "height": 8,  
    "service\_type\_id": 2,  
    "content": "Áo thun unisex GHN \- 2 chiếc"  
  }'

## [Response](https://developer.ghn.dev/vi/docs/order/preview#response)

Cùng cấu trúc với Tạo đơn, khác ở hai điểm: order\_code **rỗng** (không tạo gì) và **không có** trường message\_display.

### [Thành công (HTTP 200\)](https://developer.ghn.dev/vi/docs/order/preview#thanh-cong-http-200)

*Lấy từ một lần gọi thật trên môi trường Staging.*

jsonSao chép  
{  
  "code": 200,  
  "message": "Success",  
  "data": {  
    "order\_code": "",  
    "fee": {  
      "main\_service": 20900,  
      "insurance": 0,  
      "cod\_fee": 0,  
      "station\_do": 0,  
      "station\_pu": 0,  
      "return": 0,  
      "r2s": 0,  
      "return\_again": 0,  
      "coupon": 0,  
      "document\_return": 0,  
      "double\_check": 0,  
      "double\_check\_deliver": 0,  
      "pick\_remote\_areas\_fee": 0,  
      "deliver\_remote\_areas\_fee": 0,  
      "pick\_remote\_areas\_fee\_return": 0,  
      "deliver\_remote\_areas\_fee\_return": 0,  
      "cod\_failed\_fee": 0,  
      "change\_to\_address\_fee": 0,  
      "change\_return\_address\_fee": 0  
    },  
    "total\_fee": 20900,  
    "expected\_delivery\_time": "2026-07-16T16:59:59Z"  
  }  
}

### [Các trường Response](https://developer.ghn.dev/vi/docs/order/preview#cac-truong-response)

| Trường | Kiểu | Mô tả |
| ----- | ----- | ----- |
|  |  |  |
| code | Int | Mã HTTP (200 nếu thành công) |
| message | String | Thông báo kết quả (Success) |
| data.order\_code | String | Luôn rỗng với xem trước — không đơn nào được tạo |
| data.fee | Object | Chi tiết phí (VND) — cùng các thành phần như Tạo đơn |
| data.total\_fee | Int | Tổng phí (VND) |
| data.expected\_delivery\_time | String | Thời gian giao dự kiến (ISO 8601\) |

## [Bảng mã lỗi](https://developer.ghn.dev/vi/docs/order/preview#bang-ma-loi)

Giống [Tạo đơn](https://developer.ghn.dev/vi/docs/order/create#bang-ma-loi) — các lỗi xác thực, số điện thoại, quyền sở hữu và định tuyến đều hoạt động như nhau.

# **Thời gian giao dự kiến**

Ước tính thời gian giao hàng dự kiến cho một tuyến trước khi tạo đơn.

## [Endpoint](https://developer.ghn.dev/vi/docs/order/leadtime#endpoint)

| Môi trường | URL |
| ----- | ----- |
|  |  |
| Production | https\://online-gateway.ghn.vn/shiip/public-api/v2/shipping-order/leadtime |
| Staging | https\://dev-online-gateway.ghn.vn/shiip/public-api/v2/shipping-order/leadtime |

**Method:POST**

## [Headers](https://developer.ghn.dev/vi/docs/order/leadtime#headers)

| Header | Bắt buộc | Mô tả |
| ----- | ----- | ----- |
|  |  |  |
| Content-Type | Có | application/json |
| Token | Có | Token của bạn (do GHN cấp) — [lấy token](https://developer.ghn.dev/vi/docs/token/get-token) |
| ShopId | Có | Shop ID (số nguyên) |

## [Tham số](https://developer.ghn.dev/vi/docs/order/leadtime#tham-so)

| Tham số | Kiểu | Bắt buộc | Mô tả |
| ----- | ----- | ----- | ----- |
|  |  |  |  |
| to\_district\_id | Int | Bắt buộc | Mã quận/huyện giao (từ [Lấy Quận/Huyện](https://developer.ghn.dev/vi/docs/master-data/get-district)) |
| to\_ward\_code | String | Bắt buộc | Mã phường/xã giao (từ [Lấy Phường/Xã](https://developer.ghn.dev/vi/docs/master-data/get-ward)) |
| from\_district\_id | Int | Tùy chọn | Mã quận/huyện lấy hàng. Mặc định: địa chỉ đã đăng ký của shop |
| from\_ward\_code | String | Tùy chọn | Mã phường/xã lấy hàng. Mặc định: địa chỉ đã đăng ký của shop |
| service\_type\_id | Int | Tùy chọn | Loại dịch vụ, chọn theo khối lượng:2: tổng khối lượng **dưới 20 kg**5: tổng khối lượng **từ 20 kg trở lên**, hoặc đơn nhiều kiện |
| weight | Int | Tùy chọn | Khối lượng (gram) — ảnh hưởng ước tính với hàng nặng |
| length | Int | Tùy chọn | Chiều dài (cm) |
| width | Int | Tùy chọn | Chiều rộng (cm) |
| height | Int | Tùy chọn | Chiều cao (cm) |

Dùng service\_type\_id, không dùng service\_id. Trang api.ghn.vn có hiển thị service\_id nhưng nó không ảnh hưởng đến kết quả ước tính.

## [Ví dụ Request](https://developer.ghn.dev/vi/docs/order/leadtime#vi-du-request)

bashSao chép  
curl \-X POST https\://dev-online-gateway.ghn.vn/shiip/public-api/v2/shipping-order/leadtime \\  
 \-H "Token: 5c1d8a9e-2f4b-11ed-..." \\  
 \-H "ShopId: 92837" \\  
 \-H "Content-Type: application/json" \\  
 \-d '{  
    "from\_district\_id": 1452,  
    "from\_ward\_code": "21007",  
    "to\_district\_id": 1444,  
    "to\_ward\_code": "20308",  
    "service\_type\_id": 2  
  }'

## [Response](https://developer.ghn.dev/vi/docs/order/leadtime#response)

### [Thành công (HTTP 200\)](https://developer.ghn.dev/vi/docs/order/leadtime#thanh-cong-http-200)

*Lấy từ một lần gọi thật trên môi trường Staging.*

jsonSao chép  
{  
  "code": 200,  
  "message": "Success",  
  "data": {  
    "leadtime": 1784221199,  
    "leadtime\_order": {  
      "from\_estimate\_date": "2026-07-16T16:59:59Z",  
      "to\_estimate\_date": "2026-07-16T16:59:59Z"  
    }  
  }  
}

### [Các trường Response](https://developer.ghn.dev/vi/docs/order/leadtime#cac-truong-response)

| Trường | Kiểu | Mô tả |
| ----- | ----- | ----- |
|  |  |  |
| code | Int | Mã HTTP (200 nếu thành công) |
| message | String | Thông báo kết quả (Success) |
| data.leadtime | Int | Thời gian giao dự kiến dạng **Unix timestamp (giây)** (không phải ngày ISO) |
| data.leadtime\_order.from\_estimate\_date | String | Thời điểm giao sớm nhất dự kiến (ISO 8601\) |
| data.leadtime\_order.to\_estimate\_date | String | Thời điểm giao muộn nhất dự kiến (ISO 8601\) |

### [Lỗi — thiếu to\_district\_id (HTTP 400\)](https://developer.ghn.dev/vi/docs/order/leadtime#loi-thieu-to-district-id-http-400)

*Lấy từ một lần gọi thật trên môi trường Staging.*

jsonSao chép  
{  
  "code": 400,  
  "message": "Key: 'myRequest.ToDistrictID' Error:Field validation for 'ToDistrictID' failed on the 'required' tag",  
  "data": null,  
  "code\_message": "USER\_ERR\_COMMON",  
  "code\_message\_value": "Sai thông tin đầu vào. Vui lòng thử lại."  
}

## [Bảng mã lỗi](https://developer.ghn.dev/vi/docs/order/leadtime#bang-ma-loi)

| code\_message | HTTP | Khi nào |
| ----- | ----- | ----- |
|  |  |  |
| USER\_ERR\_COMMON | 400 | Thiếu to\_district\_id / to\_ward\_code, hoặc dữ liệu không hợp lệ |
| CLIENT\_NOT\_OWNER\_OF\_SHOP | 400 | Token không sở hữu shop |
| SERVER\_ERROR\_COMMON | 500 | Lỗi hệ thống |

# **Tính phí**

Tính phí vận chuyển cho một tuyến và kiện hàng trước khi tạo đơn.

## [Endpoint](https://developer.ghn.dev/vi/docs/order/calculate-fee#endpoint)

| Môi trường | URL |
| ----- | ----- |
|  |  |
| Production | https\://online-gateway.ghn.vn/shiip/public-api/v2/shipping-order/fee |
| Staging | https\://dev-online-gateway.ghn.vn/shiip/public-api/v2/shipping-order/fee |

**Method:POST**

## [Headers](https://developer.ghn.dev/vi/docs/order/calculate-fee#headers)

| Header | Bắt buộc | Mô tả |
| ----- | ----- | ----- |
|  |  |  |
| Content-Type | Có | application/json |
| Token | Có | Token của bạn (do GHN cấp) — [lấy token](https://developer.ghn.dev/vi/docs/token/get-token) |
| ShopId | Có | Shop ID (số nguyên) — shop được dùng để tính giá |

## [Tham số](https://developer.ghn.dev/vi/docs/order/calculate-fee#tham-so)

| Tham số | Kiểu | Bắt buộc | Mô tả |
| ----- | ----- | ----- | ----- |
|  |  |  |  |
| weight | Int | Bắt buộc | Khối lượng kiện (gram). Phải khác 0 |
| to\_district\_id | Int | Bắt buộc | Mã quận/huyện giao (từ [Lấy Quận/Huyện](https://developer.ghn.dev/vi/docs/master-data/get-district)) |
| to\_ward\_code | String | Bắt buộc | Mã phường/xã giao (từ [Lấy Phường/Xã](https://developer.ghn.dev/vi/docs/master-data/get-ward)) |
| from\_district\_id | Int | Tùy chọn | Mã quận/huyện lấy hàng. Mặc định: địa chỉ đã đăng ký của shop |
| from\_ward\_code | String | Tùy chọn | Mã phường/xã lấy hàng. Mặc định: địa chỉ đã đăng ký của shop |
| service\_type\_id | Int | Bắt buộc | Loại dịch vụ, chọn theo khối lượng:2: tổng khối lượng **dưới 20 kg**5: tổng khối lượng **từ 20 kg trở lên**, hoặc đơn nhiều kiện |
| length | Int | Tùy chọn | Chiều dài (cm) |
| width | Int | Tùy chọn | Chiều rộng (cm) |
| height | Int | Tùy chọn | Chiều cao (cm) |
| insurance\_value | Int | Tùy chọn | Giá trị khai giá (VND), dùng để bồi thường |
| cod\_value | Int | Tùy chọn | Số tiền COD cần thu (VND) |
| coupon | String | Tùy chọn | Mã giảm giá |
| items | Object\[\] | Tùy chọn | Danh sách sản phẩm — cùng cấu trúc [Tạo đơn](https://developer.ghn.dev/vi/docs/order/create); dùng cho hàng nặng |

## [Ví dụ Request](https://developer.ghn.dev/vi/docs/order/calculate-fee#vi-du-request)

bashSao chép  
curl \-X POST https\://dev-online-gateway.ghn.vn/shiip/public-api/v2/shipping-order/fee \\  
 \-H "Token: 5c1d8a9e-2f4b-11ed-..." \\  
 \-H "ShopId: 92837" \\  
 \-H "Content-Type: application/json" \\  
 \-d '{  
    "from\_district\_id": 1452,  
    "from\_ward\_code": "21007",  
    "to\_district\_id": 1444,  
    "to\_ward\_code": "20308",  
    "service\_type\_id": 2,  
    "weight": 600,  
    "length": 25,  
    "width": 20,  
    "height": 8,  
    "insurance\_value": 285000  
  }'

## [Response](https://developer.ghn.dev/vi/docs/order/calculate-fee#response)

### [Thành công (HTTP 200\)](https://developer.ghn.dev/vi/docs/order/calculate-fee#thanh-cong-http-200)

*Lấy từ một lần gọi thật trên môi trường Staging.*

jsonSao chép  
{  
  "code": 200,  
  "message": "Success",  
  "data": {  
    "total": 20900,  
    "service\_fee": 20900,  
    "insurance\_fee": 0,  
    "cod\_fee": 0,  
    "pick\_station\_fee": 0,  
    "coupon\_value": 0,  
    "r2s\_fee": 0,  
    "return\_again": 0,  
    "document\_return": 0,  
    "double\_check": 0,  
    "pick\_remote\_areas\_fee": 0,  
    "deliver\_remote\_areas\_fee": 0,  
    "cod\_failed\_fee": 0,  
    "change\_to\_address\_fee": 0,  
    "change\_return\_address\_fee": 0,  
    "return": 0  
  }  
}

### [Các trường Response](https://developer.ghn.dev/vi/docs/order/calculate-fee#cac-truong-response)

Mọi số tiền đơn vị VND.

| Trường | Kiểu | Mô tả |
| ----- | ----- | ----- |
|  |  |  |
| total | Int | Tổng phí |
| service\_fee | Int | Phí vận chuyển cơ bản |
| insurance\_fee | Int | Phí bảo hiểm (theo giá trị khai) |
| cod\_fee | Int | Phí thu hộ COD |
| pick\_station\_fee | Int | Phí lấy hàng tại bưu cục |
| coupon\_value | Int | Giá trị giảm giá coupon |
| r2s\_fee | Int | Phí giao lại ("trả về shop") |
| return\_again | Int | Phí trả hàng lặp lại |
| document\_return | Int | Phí trả chứng từ |
| double\_check | Int | Phí đồng kiểm |
| pick\_remote\_areas\_fee | Int | Phí lấy hàng vùng xa |
| deliver\_remote\_areas\_fee | Int | Phí giao hàng vùng xa |
| cod\_failed\_fee | Int | Phí thu tiền khi giao thất bại |
| change\_to\_address\_fee | Int | Phí đổi địa chỉ giao |
| change\_return\_address\_fee | Int | Phí đổi địa chỉ trả |
| return | Int | Phí trả hàng đã ghi nhận trên đơn |

## [Bảng mã lỗi](https://developer.ghn.dev/vi/docs/order/calculate-fee#bang-ma-loi)

| code\_message | HTTP | Khi nào |
| ----- | ----- | ----- |
|  |  |  |
| USER\_ERR\_COMMON | 400 | Thiếu/0 weight, hoặc dữ liệu không hợp lệ |
| CLIENT\_NOT\_OWNER\_OF\_SHOP | 400 | Token không sở hữu shop |
| SERVER\_ERROR\_COMMON | 500 | Lỗi hệ thống |

# **Phí của đơn hàng**

Lấy chi tiết phí hiện tại của một đơn đã tồn tại.

## [Endpoint](https://developer.ghn.dev/vi/docs/order/fee#endpoint)

| Môi trường | URL |
| ----- | ----- |
|  |  |
| Production | https\://online-gateway.ghn.vn/shiip/public-api/v2/shipping-order/soc |
| Staging | https\://dev-online-gateway.ghn.vn/shiip/public-api/v2/shipping-order/soc |

**Method:GET**

## [Headers](https://developer.ghn.dev/vi/docs/order/fee#headers)

| Header | Bắt buộc | Mô tả |
| ----- | ----- | ----- |
|  |  |  |
| Token | Có | Token của bạn (do GHN cấp) — [lấy token](https://developer.ghn.dev/vi/docs/token/get-token) |
| ShopId | Có | Shop ID (số nguyên) |

## [Tham số](https://developer.ghn.dev/vi/docs/order/fee#tham-so)

| Tham số | Kiểu | Bắt buộc | Mô tả |
| ----- | ----- | ----- | ----- |
|  |  |  |  |
| order\_code | String | Bắt buộc | Mã vận đơn GHN. Đơn phải thuộc tài khoản của bạn |

## [Ví dụ Request](https://developer.ghn.dev/vi/docs/order/fee#vi-du-request)

bashSao chép  
curl \-X GET "https\://dev-online-gateway.ghn.vn/shiip/public-api/v2/shipping-order/soc?order\_code=LAD8VT" \\  
 \-H "Token: 5c1d8a9e-2f4b-11ed-..." \\  
 \-H "ShopId: 92837"

## [Response](https://developer.ghn.dev/vi/docs/order/fee#response)

### [Thành công (HTTP 200\)](https://developer.ghn.dev/vi/docs/order/fee#thanh-cong-http-200)

*Lấy từ một lần gọi thật trên môi trường Staging (detail rút gọn còn các phí chính).*

jsonSao chép  
{  
  "code": 200,  
  "message": "Success",  
  "data": {  
    "order\_code": "LAD8VT",  
    "detail": {  
      "main\_service": 20900,  
      "insurance": 0,  
      "cod\_fee": 0,  
      "station\_do": 0,  
      "station\_pu": 0,  
      "return": 0,  
      "r2s": 0,  
      "coupon": 0  
    },  
    "cod\_collect\_date": "0001-01-01T00:00:00Z"  
  }  
}

### [Các trường Response](https://developer.ghn.dev/vi/docs/order/fee#cac-truong-response)

Mọi số tiền phí đơn vị VND.

| Trường | Kiểu | Mô tả |
| ----- | ----- | ----- |
|  |  |  |
| order\_code | String | Mã vận đơn GHN |
| detail | Object | Chi tiết phí hiện tại của đơn |
| detail.main\_service | Int | Phí vận chuyển cơ bản |
| detail.insurance | Int | Phí bảo hiểm (theo giá trị khai) |
| detail.cod\_fee | Int | Phí thu hộ COD |
| detail.station\_do | Int | Phí **nhận** hàng tại bưu cục (chặng giao) |
| detail.station\_pu | Int | Phí **gửi** hàng tại bưu cục (chặng lấy) |
| detail.return | Int | Phí trả hàng |
| detail.r2s | Int | Phí giao lại ("trả về shop") |
| detail.coupon | Int | Giảm giá coupon |
| detail.total | Int | Tổng phí (bị bỏ khi 0\) |
| cod\_collect\_date | String | Thời điểm đã thu COD (ISO 8601). Ngày rỗng 0001-01-01T00:00:00Z nghĩa là chưa thu |

detail còn có các thành phần phí khác như ở Tính phí (document\_return, double\_check, phí vùng xa, phí đổi địa chỉ, …); giá trị 0 được trả về là 0.

## [Bảng mã lỗi](https://developer.ghn.dev/vi/docs/order/fee#bang-ma-loi)

| Điều kiện | HTTP | Ý nghĩa |
| ----- | ----- | ----- |
|  |  |  |
| Không tìm thấy thông tin đơn hàng | 404 | Mã đơn không tồn tại |
| CLIENT\_NOT\_OWNER\_OF\_SHOP | 400 | Đơn không thuộc tài khoản của bạn |
| SERVER\_ERROR\_COMMON | 500 | Lỗi hệ thống |

# **Lấy Tỉnh/Thành**

Lấy danh sách tỉnh/thành phố.

**Mô hình địa chỉ cũ.** API này dùng mô hình địa chỉ theo quận/huyện của GHN (trước đợt sắp xếp hành chính 01/07/2025). Với tích hợp mới, nên dùng địa chỉ theo tên trong [Tạo đơn](https://developer.ghn.dev/vi/docs/order/create) (to\_province\_name \+ is\_new\_to\_address) Danh mục của hệ mới: [Lấy Tỉnh/Thành (Mới)](https://developer.ghn.dev/vi/docs/master-data/get-province-new) và [Lấy Phường/Xã (Mới)](https://developer.ghn.dev/vi/docs/master-data/get-ward-new).

## [Endpoint](https://developer.ghn.dev/vi/docs/master-data/get-province#endpoint)

| Môi trường | URL |
| ----- | ----- |
|  |  |
| Production | https\://online-gateway.ghn.vn/shiip/public-api/master-data/province |
| Staging | https\://dev-online-gateway.ghn.vn/shiip/public-api/master-data/province |

**Method:GET**

## [Headers](https://developer.ghn.dev/vi/docs/master-data/get-province#headers)

| Header | Bắt buộc | Mô tả |
| ----- | ----- | ----- |
|  |  |  |
| Token | Có | Token của bạn (do GHN cấp) — [lấy token](https://developer.ghn.dev/vi/docs/token/get-token) |
| ShopId | Có | Shop ID (số nguyên) |

## [Tham số](https://developer.ghn.dev/vi/docs/master-data/get-province#tham-so)

API này **không nhận tham số** — luôn trả về toàn bộ danh sách tỉnh/thành.

## [Ví dụ Request](https://developer.ghn.dev/vi/docs/master-data/get-province#vi-du-request)

bashSao chép  
curl \-X GET https\://dev-online-gateway.ghn.vn/shiip/public-api/master-data/province \\  
 \-H "Token: 5c1d8a9e-2f4b-11ed-..." \\  
 \-H "ShopId: 92837"

## [Response](https://developer.ghn.dev/vi/docs/master-data/get-province#response)

### [Thành công (HTTP 200\)](https://developer.ghn.dev/vi/docs/master-data/get-province#thanh-cong-http-200)

*Lấy từ một lần gọi thật trên môi trường Staging (rút gọn còn một tỉnh, các trường thường dùng).*

jsonSao chép  
{  
  "code": 200,  
  "message": "Success",  
  "data": \[  
    {  
      "ProvinceID": 202,  
      "ProvinceName": "Hồ Chí Minh",  
      "CanUpdateCOD": false  
    }  
  \]  
}

### [Các trường Response](https://developer.ghn.dev/vi/docs/master-data/get-province#cac-truong-response)

data là một mảng object tỉnh/thành.

| Trường | Kiểu | Mô tả |
| ----- | ----- | ----- |
|  |  |  |
| ProvinceID | Int | Mã tỉnh — truyền sang [Lấy Quận/Huyện](https://developer.ghn.dev/vi/docs/master-data/get-district) |
| ProvinceName | String | Tên tỉnh / thành phố |
| CanUpdateCOD | Bool | Đơn ở tỉnh này có được phép cập nhật COD hay không |

## [Bảng mã lỗi](https://developer.ghn.dev/vi/docs/master-data/get-province#bang-ma-loi)

| Điều kiện | HTTP | Ý nghĩa |
| ----- | ----- | ----- |
|  |  |  |
| Authorization header is required\! | 401 | Thiếu Token |
| IP không được phép | 401 | Tài khoản của bạn có whitelist IP và request đến từ IP khác |
| SERVER\_ERROR\_COMMON | 500 | Lỗi hệ thống |

# **Lấy Quận/Huyện**

Lấy danh sách quận/huyện trong một tỉnh.

**Mô hình địa chỉ cũ.** API này dùng mô hình địa chỉ theo quận/huyện của GHN (trước đợt sắp xếp hành chính 01/07/2025, đợt này đã bỏ cấp quận/huyện). Với tích hợp mới, nên dùng địa chỉ theo tên trong [Tạo đơn](https://developer.ghn.dev/vi/docs/order/create) Danh mục của hệ mới: [Lấy Tỉnh/Thành (Mới)](https://developer.ghn.dev/vi/docs/master-data/get-province-new) và [Lấy Phường/Xã (Mới)](https://developer.ghn.dev/vi/docs/master-data/get-ward-new).

## [Endpoint](https://developer.ghn.dev/vi/docs/master-data/get-district#endpoint)

| Môi trường | URL |
| ----- | ----- |
|  |  |
| Production | https\://online-gateway.ghn.vn/shiip/public-api/master-data/district |
| Staging | https\://dev-online-gateway.ghn.vn/shiip/public-api/master-data/district |

**Method:GET**

## [Headers](https://developer.ghn.dev/vi/docs/master-data/get-district#headers)

| Header | Bắt buộc | Mô tả |
| ----- | ----- | ----- |
|  |  |  |
| Token | Có | Token của bạn (do GHN cấp) — [lấy token](https://developer.ghn.dev/vi/docs/token/get-token) |
| ShopId | Có | Shop ID (số nguyên) |

## [Tham số](https://developer.ghn.dev/vi/docs/master-data/get-district#tham-so)

| Tham số | Kiểu | Bắt buộc | Mô tả |
| ----- | ----- | ----- | ----- |
|  |  |  |  |
| province\_id | Int | Bắt buộc | Mã tỉnh (từ [Lấy Tỉnh/Thành](https://developer.ghn.dev/vi/docs/master-data/get-province)). |

## [Ví dụ Request](https://developer.ghn.dev/vi/docs/master-data/get-district#vi-du-request)

bashSao chép  
curl \-X GET "https\://dev-online-gateway.ghn.vn/shiip/public-api/master-data/district?province\_id=202" \\  
 \-H "Token: 5c1d8a9e-2f4b-11ed-..." \\  
 \-H "ShopId: 92837"

## [Response](https://developer.ghn.dev/vi/docs/master-data/get-district#response)

### [Thành công (HTTP 200\)](https://developer.ghn.dev/vi/docs/master-data/get-district#thanh-cong-http-200)

*Lấy từ một lần gọi thật trên môi trường Staging (rút gọn còn một quận/huyện, các trường thường dùng).*

jsonSao chép  
{  
  "code": 200,  
  "message": "Success",  
  "data": \[  
    {  
      "DistrictID": 3695,  
      "DistrictName": "Thành Phố Thủ Đức",  
      "SupportType": 3,  
      "CanUpdateCOD": false  
    }  
  \]  
}

### [Các trường Response](https://developer.ghn.dev/vi/docs/master-data/get-district#cac-truong-response)

data là một mảng object quận/huyện.

| Trường | Kiểu | Mô tả |
| ----- | ----- | ----- |
|  |  |  |
| DistrictID | Int | Mã quận/huyện — truyền sang [Lấy Phường/Xã](https://developer.ghn.dev/vi/docs/master-data/get-ward) và tạo đơn |
| DistrictName | String | Tên quận/huyện |
| SupportType | Int | Hỗ trợ dịch vụ:0: khóa (không có dịch vụ)1: chỉ lấy hàng2: chỉ giao hàng3: lấy \+ giao |
| CanUpdateCOD | Bool | Đơn ở quận/huyện này có được phép cập nhật COD hay không |

## [Bảng mã lỗi](https://developer.ghn.dev/vi/docs/master-data/get-district#bang-ma-loi)

| code\_message | HTTP | Khi nào |
| ----- | ----- | ----- |
|  |  |  |
| USER\_ERR\_COMMON | 400 | Dữ liệu không hợp lệ |
| SERVER\_ERROR\_COMMON | 500 | Lỗi hệ thống |

# **Lấy Phường/Xã**

Lấy danh sách phường/xã trong một quận/huyện.

**Mô hình địa chỉ cũ.** API này dùng mô hình địa chỉ theo quận/huyện của GHN (trước đợt sắp xếp hành chính 01/07/2025). Với tích hợp mới, nên dùng địa chỉ theo tên trong [Tạo đơn](https://developer.ghn.dev/vi/docs/order/create) Danh mục của hệ mới: [Lấy Tỉnh/Thành (Mới)](https://developer.ghn.dev/vi/docs/master-data/get-province-new) và [Lấy Phường/Xã (Mới)](https://developer.ghn.dev/vi/docs/master-data/get-ward-new).

## [Endpoint](https://developer.ghn.dev/vi/docs/master-data/get-ward#endpoint)

| Môi trường | URL |
| ----- | ----- |
|  |  |
| Production | https\://online-gateway.ghn.vn/shiip/public-api/master-data/ward |
| Staging | https\://dev-online-gateway.ghn.vn/shiip/public-api/master-data/ward |

**Method:GET**

## [Headers](https://developer.ghn.dev/vi/docs/master-data/get-ward#headers)

| Header | Bắt buộc | Mô tả |
| ----- | ----- | ----- |
|  |  |  |
| Token | Có | Token của bạn (do GHN cấp) — [lấy token](https://developer.ghn.dev/vi/docs/token/get-token) |
| ShopId | Có | Shop ID (số nguyên) |

## [Tham số](https://developer.ghn.dev/vi/docs/master-data/get-ward#tham-so)

| Tham số | Kiểu | Bắt buộc | Mô tả |
| ----- | ----- | ----- | ----- |
|  |  |  |  |
| district\_id | Int | Bắt buộc | Mã quận/huyện (từ [Lấy Quận/Huyện](https://developer.ghn.dev/vi/docs/master-data/get-district)). 0 hoặc thiếu sẽ bị từ chối |

## [Ví dụ Request](https://developer.ghn.dev/vi/docs/master-data/get-ward#vi-du-request)

bashSao chép  
curl \-X GET "https\://dev-online-gateway.ghn.vn/shiip/public-api/master-data/ward?district\_id=1444" \\  
 \-H "Token: 5c1d8a9e-2f4b-11ed-..." \\  
 \-H "ShopId: 92837"

## [Response](https://developer.ghn.dev/vi/docs/master-data/get-ward#response)

### [Thành công (HTTP 200\)](https://developer.ghn.dev/vi/docs/master-data/get-ward#thanh-cong-http-200)

*Lấy từ một lần gọi thật trên môi trường Staging (rút gọn còn một phường/xã, các trường thường dùng).*

jsonSao chép  
{  
  "code": 200,  
  "message": "Success",  
  "data": \[  
    {  
      "WardCode": "90795",  
      "WardName": "Phường Võ Thị Sáu",  
      "SupportType": 3,  
      "CanUpdateCOD": true  
    }  
  \]  
}

### [Các trường Response](https://developer.ghn.dev/vi/docs/master-data/get-ward#cac-truong-response)

data là một mảng object phường/xã.

| Trường | Kiểu | Mô tả |
| ----- | ----- | ----- |
|  |  |  |
| WardCode | String | Mã phường/xã — **là chuỗi, không phải số** (có thể có số 0 ở đầu). Dùng khi tạo đơn |
| WardName | String | Tên phường/xã |
| SupportType | Int | Hỗ trợ dịch vụ:0: khóa (không có dịch vụ)1: chỉ lấy hàng2: chỉ giao hàng3: lấy \+ giao |
| CanUpdateCOD | Bool | Đơn ở phường/xã này có được phép cập nhật COD hay không |

## [Bảng mã lỗi](https://developer.ghn.dev/vi/docs/master-data/get-ward#bang-ma-loi)

| code\_message | HTTP | Khi nào |
| ----- | ----- | ----- |
|  |  |  |
| USER\_ERR\_COMMON | 400 | Thiếu hoặc 0 district\_id, hoặc dữ liệu không hợp lệ |
| SERVER\_ERROR\_COMMON | 500 | Lỗi hệ thống |

# **Lấy Tỉnh/Thành (Mới)**

Lấy danh sách tỉnh/thành theo mô hình hành chính 2 cấp.

**Mô hình địa chỉ 2 cấp (mới).** Bộ API mới trả danh mục sau đợt sắp xếp hành chính 01/07/2025 (sáp nhập tỉnh, bỏ cấp quận/huyện). name trả về là giá trị truyền thẳng vào to\_province\_name khi [Tạo đơn](https://developer.ghn.dev/vi/docs/order/create) với is\_new\_to\_address: true. Danh mục theo quận/huyện cũ vẫn ở [Lấy Tỉnh/Thành](https://developer.ghn.dev/vi/docs/master-data/get-province).

## [Endpoint](https://developer.ghn.dev/vi/docs/master-data/get-province-new#endpoint)

| Môi trường | URL |
| ----- | ----- |
|  |  |
| Production | https\://online-gateway.ghn.vn/shiip/public-api/v3/master-data/province/all |
| Staging | https\://dev-online-gateway.ghn.vn/shiip/public-api/v3/master-data/province/all |

**Method:GET**

## [Headers](https://developer.ghn.dev/vi/docs/master-data/get-province-new#headers)

| Header | Bắt buộc | Mô tả |
| ----- | ----- | ----- |
|  |  |  |
| Token | Có | Token của bạn (do GHN cấp) — [lấy token](https://developer.ghn.dev/vi/docs/token/get-token) |
| ShopId | Có | Shop ID (số nguyên) |

## [Tham số](https://developer.ghn.dev/vi/docs/master-data/get-province-new#tham-so)

| Tham số | Kiểu | Bắt buộc | Mô tả |
| ----- | ----- | ----- | ----- |
|  |  |  |  |
| offset | Int | Tùy chọn | Vị trí bắt đầu khi phân trang. Mặc định: 0 |
| limit | Int | Tùy chọn | Số phần tử tối đa, không quá 200. Bỏ trống hoặc 0 để lấy tất cả — hiện có 34 tỉnh/thành nên một lần gọi là đủ |

## [Ví dụ Request](https://developer.ghn.dev/vi/docs/master-data/get-province-new#vi-du-request)

bashSao chép  
curl \-X GET "https\://dev-online-gateway.ghn.vn/shiip/public-api/v3/master-data/province/all?offset=0\&limit=200" \\  
 \-H "Token: 5c1d8a9e-2f4b-11ed-..." \\  
 \-H "ShopId: 92837"

## [Response](https://developer.ghn.dev/vi/docs/master-data/get-province-new#response)

### [Thành công (HTTP 200\)](https://developer.ghn.dev/vi/docs/master-data/get-province-new#thanh-cong-http-200)

*Lấy từ một lần gọi thật trên môi trường Staging (rút gọn còn một trong 34 tỉnh/thành, bỏ các trường quản trị nội bộ).*

jsonSao chép  
{  
  "code": 200,  
  "message": "Success",  
  "data": \[  
    {  
      "\_id": 1000001,  
      "name": "Hồ Chí Minh",  
      "extension\_names": \[  
        "hồ chí minh",  
        "tp.hồ chí minh",  
        "thành phố hồ chí minh",  
        "hcm",  
        "ho chi minh"  
      \],  
      "type": "province",  
      "parent\_id": 1,  
      "status": 1  
    }  
  \]  
}

### [Các trường Response](https://developer.ghn.dev/vi/docs/master-data/get-province-new#cac-truong-response)

| Trường | Kiểu | Mô tả |
| ----- | ----- | ----- |
|  |  |  |
| data\[\].\_id | Int | Mã tỉnh/thành hệ 2 cấp — truyền vào province\_id của [Lấy Phường/Xã (Mới)](https://developer.ghn.dev/vi/docs/master-data/get-ward-new) |
| data\[\].name | String | Tên chuẩn của tỉnh/thành — dùng nguyên văn cho to\_province\_name khi [Tạo đơn](https://developer.ghn.dev/vi/docs/order/create) hệ mới |
| data\[\].extension\_names | String\[\] | Các biến thể tên (không dấu, viết tắt, có tiền tố…) — dùng để so khớp hoặc gợi ý khi người dùng gõ tự do |
| data\[\].type | String | Luôn là province |
| data\[\].parent\_id | Int | Mã quốc gia (Việt Nam là 1) |
| data\[\].status | Int | Là 1 nếu đang hoạt động, là 2 nếu ngưng sử dụng, là 10 nếu đã xoá |

## [Bảng mã lỗi](https://developer.ghn.dev/vi/docs/master-data/get-province-new#bang-ma-loi)

| Điều kiện | HTTP | Ý nghĩa |
| ----- | ----- | ----- |
|  |  |  |
| Authorization header is required\! | 401 | Thiếu Token |
| limit tối đa chỉ được 200 | 400 | limit vượt quá 200 |
| IP không được phép | 401 | Tài khoản của bạn có whitelist IP và request đến từ IP khác |
| SERVER\_ERROR\_COMMON | 500 | Lỗi hệ thống |

# **Lấy Phường/Xã (Mới)**

Lấy danh sách phường/xã của một tỉnh/thành theo mô hình hành chính 2 cấp.

**Mô hình địa chỉ 2 cấp (mới).** Phường/xã thuộc thẳng tỉnh/thành, không còn cấp quận/huyện. name trả về là giá trị truyền thẳng vào to\_ward\_name khi [Tạo đơn](https://developer.ghn.dev/vi/docs/order/create) với is\_new\_to\_address: true. Danh mục theo quận/huyện cũ vẫn ở [Lấy Phường/Xã](https://developer.ghn.dev/vi/docs/master-data/get-ward).

## [Endpoint](https://developer.ghn.dev/vi/docs/master-data/get-ward-new#endpoint)

| Môi trường | URL |
| ----- | ----- |
|  |  |
| Production | https\://online-gateway.ghn.vn/shiip/public-api/v3/master-data/ward/all-by-province-id |
| Staging | https\://dev-online-gateway.ghn.vn/shiip/public-api/v3/master-data/ward/all-by-province-id |

**Method:GET**

## [Headers](https://developer.ghn.dev/vi/docs/master-data/get-ward-new#headers)

| Header | Bắt buộc | Mô tả |
| ----- | ----- | ----- |
|  |  |  |
| Token | Có | Token của bạn (do GHN cấp) — [lấy token](https://developer.ghn.dev/vi/docs/token/get-token) |
| ShopId | Có | Shop ID (số nguyên) |

## [Tham số](https://developer.ghn.dev/vi/docs/master-data/get-ward-new#tham-so)

| Tham số | Kiểu | Bắt buộc | Mô tả |
| ----- | ----- | ----- | ----- |
|  |  |  |  |
| province\_id | Int | Bắt buộc | Mã tỉnh/thành hệ 2 cấp, lấy từ [Lấy Tỉnh/Thành (Mới)](https://developer.ghn.dev/vi/docs/master-data/get-province-new) (trường \_id). Thiếu hoặc sai mã thì API vẫn trả HTTP 200 nhưng data là null — hãy coi data rỗng là dấu hiệu sai mã tỉnh |
| offset | Int | Tùy chọn | Vị trí bắt đầu khi phân trang. Mặc định: 0 |
| limit | Int | Tùy chọn | Số phần tử tối đa, không quá 200. Tỉnh nhiều phường/xã nhất hiện là TP. Hồ Chí Minh với 168 — một lần gọi limit=200 là đủ |

## [Ví dụ Request](https://developer.ghn.dev/vi/docs/master-data/get-ward-new#vi-du-request)

bashSao chép  
curl \-X GET "https\://dev-online-gateway.ghn.vn/shiip/public-api/v3/master-data/ward/all-by-province-id?province\_id=1000001\&offset=0\&limit=200" \\  
 \-H "Token: 5c1d8a9e-2f4b-11ed-..." \\  
 \-H "ShopId: 92837"

## [Response](https://developer.ghn.dev/vi/docs/master-data/get-ward-new#response)

### [Thành công (HTTP 200\)](https://developer.ghn.dev/vi/docs/master-data/get-ward-new#thanh-cong-http-200)

*Lấy từ một lần gọi thật trên môi trường Staging với province\_id=1000001 (TP. Hồ Chí Minh — rút gọn còn một trong 168 phường/xã, bỏ các trường quản trị nội bộ).*

jsonSao chép  
{  
  "code": 200,  
  "message": "Success",  
  "data": \[  
    {  
      "\_id": 1003646,  
      "name": "Phường Vũng Tàu",  
      "extension\_names": \[  
        "phường vũng tàu",  
        "p.vũng tàu",  
        "vũng tàu",  
        "vung tau",  
        "phuong vung tau"  
      \],  
      "type": "ward",  
      "parent\_id": 1000001,  
      "status": 1  
    }  
  \]  
}

### [Các trường Response](https://developer.ghn.dev/vi/docs/master-data/get-ward-new#cac-truong-response)

| Trường | Kiểu | Mô tả |
| ----- | ----- | ----- |
|  |  |  |
| data\[\].\_id | Int | Mã phường/xã hệ 2 cấp (định danh nội bộ của GHN) |
| data\[\].name | String | Tên chuẩn của phường/xã — dùng nguyên văn cho to\_ward\_name khi [Tạo đơn](https://developer.ghn.dev/vi/docs/order/create) hệ mới |
| data\[\].extension\_names | String\[\] | Các biến thể tên (không dấu, viết tắt…) — dùng để so khớp hoặc gợi ý khi người dùng gõ tự do |
| data\[\].type | String | Luôn là ward |
| data\[\].parent\_id | Int | Mã tỉnh/thành chứa phường/xã này (\_id bên [Lấy Tỉnh/Thành (Mới)](https://developer.ghn.dev/vi/docs/master-data/get-province-new)) |
| data\[\].status | Int | Là 1 nếu đang hoạt động, là 2 nếu ngưng sử dụng, là 10 nếu đã xoá |

## [Bảng mã lỗi](https://developer.ghn.dev/vi/docs/master-data/get-ward-new#bang-ma-loi)

| Điều kiện | HTTP | Ý nghĩa |
| ----- | ----- | ----- |
|  |  |  |
| Authorization header is required\! | 401 | Thiếu Token |
| data là null | 200 | province\_id thiếu hoặc không tồn tại (API không trả lỗi riêng) |
| IP không được phép | 401 | Tài khoản của bạn có whitelist IP và request đến từ IP khác |
| SERVER\_ERROR\_COMMON | 500 | Lỗi hệ thống |

# **Lấy Bưu cục**

Tìm bưu cục GHN gần một quận/huyện, ví dụ để shop mang hàng đến gửi tại bưu cục.

## [Endpoint](https://developer.ghn.dev/vi/docs/master-data/get-station#endpoint)

| Môi trường | URL |
| ----- | ----- |
|  |  |
| Production | https\://online-gateway.ghn.vn/shiip/public-api/v2/station/get |
| Staging | https\://dev-online-gateway.ghn.vn/shiip/public-api/v2/station/get |

**Method:GET**

## [Headers](https://developer.ghn.dev/vi/docs/master-data/get-station#headers)

| Header | Bắt buộc | Mô tả |
| ----- | ----- | ----- |
|  |  |  |
| Token | Có | Token của bạn (do GHN cấp) — [lấy token](https://developer.ghn.dev/vi/docs/token/get-token) |
| ShopId | Có | Shop ID (số nguyên) |
| captcha | Tùy điều kiện | Một số tài khoản (không có quyền tra cứu bưu cục) phải gửi captcha token ở header này, nếu không sẽ nhận HTTP 422\. Liên hệ GHN nếu gặp trường hợp này |

## [Tham số](https://developer.ghn.dev/vi/docs/master-data/get-station#tham-so)

| Tham số | Kiểu | Bắt buộc | Mô tả |
| ----- | ----- | ----- | ----- |
|  |  |  |  |
| district\_id | String | Bắt buộc | Mã quận/huyện (từ [Lấy Quận/Huyện](https://developer.ghn.dev/vi/docs/master-data/get-district)). |
| ward\_code | String | Tùy chọn | Mã phường/xã (từ [Lấy Phường/Xã](https://developer.ghn.dev/vi/docs/master-data/get-ward)) để thu hẹp tìm kiếm |
| offset | Int | Tùy chọn | Vị trí phân trang. Mặc định: 0 |
| limit | Int | Tùy chọn | Số lượng mỗi trang. Mặc định: do GHN quy định |

## [Ví dụ Request](https://developer.ghn.dev/vi/docs/master-data/get-station#vi-du-request)

bashSao chép  
curl \-X GET "https\://dev-online-gateway.ghn.vn/shiip/public-api/v2/station/get?district\_id=1444\&offset=0\&limit=20" \\  
 \-H "Token: 5c1d8a9e-2f4b-11ed-..." \\  
 \-H "ShopId: 92837"

## [Response](https://developer.ghn.dev/vi/docs/master-data/get-station#response)

data là một **mảng** object bưu cục (\[\] khi không có kết quả, không bao giờ null).

*Cấu trúc theo tài liệu trường của GHN — tài khoản test này yêu cầu captcha token nên không lấy được mẫu thành công thật (xem lỗi 422 bên dưới).*

jsonSao chép  
{  
  "code": 200,  
  "message": "Success",  
  "data": \[  
    {  
      "locationId": 1888,  
      "locationName": "Bưu cục 1888 \- Quận 1",  
      "address": "123 Lê Lợi, Phường Bến Thành, Quận 1, TP. Hồ Chí Minh",  
      "wardName": "Phường Bến Thành",  
      "districtName": "Quận 1",  
      "provinceName": "Hồ Chí Minh",  
      "latitude": 10.7721,  
      "longitude": 106.698,  
      "email": "buucuc1888@ghn.vn"  
    }  
  \]  
}

### [Các trường Response](https://developer.ghn.dev/vi/docs/master-data/get-station#cac-truong-response)

| Trường | Kiểu | Mô tả |
| ----- | ----- | ----- |
|  |  |  |
| locationId | Int | **Mã bưu cục** — dùng làm pick\_station\_id khi tạo đơn |
| locationName | String | Tên bưu cục |
| address | String | Địa chỉ bưu cục |
| wardName | String | Tên phường/xã |
| districtName | String | Tên quận/huyện |
| provinceName | String | Tên tỉnh |
| latitude | Float | Vĩ độ |
| longitude | Float | Kinh độ |
| email | String | Email bưu cục |

### [Lỗi — cần captcha (HTTP 422\)](https://developer.ghn.dev/vi/docs/master-data/get-station#loi-can-captcha-http-422)

*Lấy từ một lần gọi thật trên môi trường Staging — tài khoản này không có quyền tra cứu bưu cục nên endpoint yêu cầu captcha token.*

jsonSao chép  
{  
  "code": 422,  
  "message": "token contains an invalid number of segments",  
  "data": null,  
  "code\_message": "SECURITY\_CHECK\_FAILED",  
  "code\_message\_value": "Discover fraud and abuse potential"  
}

## [Bảng mã lỗi](https://developer.ghn.dev/vi/docs/master-data/get-station#bang-ma-loi)

| code\_message | HTTP | Khi nào |
| ----- | ----- | ----- |
|  |  |  |
| SECURITY\_CHECK\_FAILED | 422 | Tài khoản cần captcha token ở header captcha — liên hệ GHN |
| USER\_ERR\_COMMON | 400 | Dữ liệu không hợp lệ |
| STATION\_INVALID | 400 | Tra cứu bưu cục thất bại phía GHN |
| SERVER\_ERROR\_COMMON | 500 | Lỗi hệ thống |

# **Lấy Dịch vụ**

Liệt kê các dịch vụ vận chuyển khả dụng cho một tuyến và shop. Gọi API này trước, rồi truyền service\_type\_id trả về vào Tính phí và Tạo đơn.

## [Endpoint](https://developer.ghn.dev/vi/docs/master-data/get-service#endpoint)

| Môi trường | URL |
| ----- | ----- |
|  |  |
| Production | https\://online-gateway.ghn.vn/shiip/public-api/v2/shipping-order/available-services |
| Staging | https\://dev-online-gateway.ghn.vn/shiip/public-api/v2/shipping-order/available-services |

**Method:POST**

## [Headers](https://developer.ghn.dev/vi/docs/master-data/get-service#headers)

| Header | Bắt buộc | Mô tả |
| ----- | ----- | ----- |
|  |  |  |
| Content-Type | Có | application/json |
| Token | Có | Token của bạn (do GHN cấp) — [lấy token](https://developer.ghn.dev/vi/docs/token/get-token) |
| ShopId | Có | Shop ID (số nguyên) |

## [Tham số](https://developer.ghn.dev/vi/docs/master-data/get-service#tham-so)

| Tham số | Kiểu | Bắt buộc | Mô tả |
| ----- | ----- | ----- | ----- |
|  |  |  |  |
| shop\_id | Int | Bắt buộc | Shop có hợp đồng/bảng giá quyết định các dịch vụ khả dụng. Đây là **trường trong body** (khác với header ShopId) |
| from\_district | Int | Bắt buộc | Mã quận/huyện lấy hàng (từ [Lấy Quận/Huyện](https://developer.ghn.dev/vi/docs/master-data/get-district)) |
| to\_district | Int | Bắt buộc | Mã quận/huyện giao (từ [Lấy Quận/Huyện](https://developer.ghn.dev/vi/docs/master-data/get-district)) |

## [Ví dụ Request](https://developer.ghn.dev/vi/docs/master-data/get-service#vi-du-request)

bashSao chép  
curl \-X POST https\://dev-online-gateway.ghn.vn/shiip/public-api/v2/shipping-order/available-services \\  
 \-H "Token: 5c1d8a9e-2f4b-11ed-..." \\  
 \-H "ShopId: 92837" \\  
 \-H "Content-Type: application/json" \\  
 \-d '{  
    "shop\_id": 196560,  
    "from\_district": 1452,  
    "to\_district": 1444  
  }'

## [Response](https://developer.ghn.dev/vi/docs/master-data/get-service#response)

### [Thành công (HTTP 200\)](https://developer.ghn.dev/vi/docs/master-data/get-service#thanh-cong-http-200)

*Lấy từ một lần gọi thật trên môi trường Staging.*

jsonSao chép  
{  
  "code": 200,  
  "message": "Success",  
  "data": \[  
    {  
      "service\_id": 53320,  
      "short\_name": "Hàng nhẹ",  
      "service\_type\_id": 2  
    },  
    {  
      "service\_id": 100039,  
      "short\_name": "Hàng nặng",  
      "service\_type\_id": 5  
    }  
  \]  
}

### [Các trường Response](https://developer.ghn.dev/vi/docs/master-data/get-service#cac-truong-response)

data là một mảng dịch vụ khả dụng.

| Trường | Kiểu | Mô tả |
| ----- | ----- | ----- |
|  |  |  |
| service\_id | Int | Mã dịch vụ do GHN sinh — chỉ để đối chiếu, không dùng khi gọi Tính phí hay Tạo đơn |
| short\_name | String | Tên hiển thị dịch vụ (ví dụ "Hàng nhẹ", "Hàng nặng") |
| service\_type\_id | Int | Loại dịch vụ, chọn theo khối lượng:2: tổng khối lượng **dưới 20 kg**5: tổng khối lượng **từ 20 kg trở lên**, hoặc đơn nhiều kiện |

## [Lưu ý](https://developer.ghn.dev/vi/docs/master-data/get-service#luu-y)

* **Response rỗng nghĩa là không tìm thấy shop.** Nếu shop\_id bạn gửi không tồn tại, GHN trả về HTTP 200 với **body rỗng** (không phải lỗi JSON). Hãy coi body rỗng là "không tìm thấy shop".

## [Bảng mã lỗi](https://developer.ghn.dev/vi/docs/master-data/get-service#bang-ma-loi)

| code\_message | HTTP | Khi nào |
| ----- | ----- | ----- |
|  |  |  |
| USER\_ERR\_COMMON | 400 | Thiếu shop\_id / from\_district / to\_district |
| WARD\_IS\_INVALID | 400 | Địa chỉ định dạng mới không map được sang quận/huyện |
| SERVER\_ERROR\_COMMON | 500 | Lỗi hệ thống |

# **Ca lấy hàng**

## [Endpoint](https://developer.ghn.dev/vi/docs/master-data/pick-shift#endpoint)

| Môi trường | URL |
| ----- | ----- |
|  |  |
| Production | https\://online-gateway.ghn.vn/shiip/public-api/v2/shift/date |
| Staging | https\://dev-online-gateway.ghn.vn/shiip/public-api/v2/shift/date |

**Method:GET**

## [Headers](https://developer.ghn.dev/vi/docs/master-data/pick-shift#headers)

| Header | Bắt buộc | Mô tả |
| ----- | ----- | ----- |
|  |  |  |
| Token | Có | Token của bạn (do GHN cấp) — [lấy token](https://developer.ghn.dev/vi/docs/token/get-token) |

## [Tham số](https://developer.ghn.dev/vi/docs/master-data/pick-shift#tham-so)

API này **không nhận tham số** — không có body, không có query string.

## [Ví dụ Request](https://developer.ghn.dev/vi/docs/master-data/pick-shift#vi-du-request)

bashSao chép  
curl \-X GET https\://dev-online-gateway.ghn.vn/shiip/public-api/v2/shift/date \\  
 \-H "Token: 5c1d8a9e-2f4b-11ed-..."

## [Response](https://developer.ghn.dev/vi/docs/master-data/pick-shift#response)

### [Thành công (HTTP 200\)](https://developer.ghn.dev/vi/docs/master-data/pick-shift#thanh-cong-http-200)

*Lấy từ một lần gọi thật trên môi trường Staging.*

jsonSao chép  
{  
  "code": 200,  
  "message": "Success",  
  "data": \[  
    {  
      "id": 2,  
      "title": "Ca lấy 15-07-2026 (12h00 \- 18h00)",  
      "from\_time": 43200,  
      "to\_time": 64800  
    },  
    {  
      "id": 3,  
      "title": "Ca lấy 16-07-2026 (7h00 \- 12h00)",  
      "from\_time": 111600,  
      "to\_time": 129600  
    },  
    {  
      "id": 4,  
      "title": "Ca lấy 16-07-2026 (12h00 \- 18h00)",  
      "from\_time": 129600,  
      "to\_time": 151200  
    }  
  \]  
}

### [Các trường Response](https://developer.ghn.dev/vi/docs/master-data/pick-shift#cac-truong-response)

| Trường | Kiểu | Mô tả |
| ----- | ----- | ----- |
|  |  |  |
| code | Int | Mã HTTP (200 nếu thành công) |
| message | String | Thông báo kết quả (Success) |
| data | Object\[\] | Danh sách ca lấy hàng khả dụng |
| data\[\].id | Int | Mã ca — truyền vào pick\_shift khi tạo đơn. Mã được sinh theo ngày, **không phải enum cố định**: luôn gọi lấy danh sách hiện tại và không hard-code |
| data\[\].title | String | Nhãn ca (trả về bằng tiếng Việt): ngày lấy \+ khung giờ |
| data\[\].from\_time | Int | Thời điểm bắt đầu ca, tính bằng giây kể từ 00:00 hôm nay (ví dụ 43200 là 12:00 hôm nay; giá trị lớn hơn 86400 rơi vào các ngày sau) |
| data\[\].to\_time | Int | Thời điểm kết thúc ca, cùng đơn vị và mốc với from\_time |

### [Lỗi — thiếu token (HTTP 401\)](https://developer.ghn.dev/vi/docs/master-data/pick-shift#loi-thieu-token-http-401)

*Lấy từ một lần gọi thật trên môi trường Staging.*

jsonSao chép  
{  
  "code": 401,  
  "message": "Authorization header is required\!",  
  "data": null  
}

## [Bảng mã lỗi](https://developer.ghn.dev/vi/docs/master-data/pick-shift#bang-ma-loi)

| Điều kiện | HTTP | Ý nghĩa |
| ----- | ----- | ----- |
|  |  |  |
| Authorization header is required\! | 401 | Thiếu header Token |
| IP không được phép | 401 | Tài khoản của bạn có whitelist IP và request đến từ IP khác |
| SERVER\_ERROR\_COMMON | 500 | Lỗi hệ thống |

# **Mã lý do**

Danh mục mã lý do (reason\_code) GHN dùng trên vòng đời đơn hàng. Đối tác gặp mã này ở hai chỗ:

* Gửi lên khi [Hủy đơn](https://developer.ghn.dev/vi/docs/order/cancel) — trường reason\_code (chỉ nhóm "Hủy bởi người gửi" bên dưới).  
* Nhận về qua [Callback trạng thái đơn](https://developer.ghn.dev/vi/docs/webhook/callback-order-status) — cặp trường ReasonCode \+ Reason; cột "Thông điệp" bên dưới là đúng nội dung trường Reason.

Danh mục do GHN quản lý tập trung (master data) và có thể được bổ sung theo thời gian — hệ thống của bạn nên chấp nhận mã lạ thay vì báo lỗi.

## [Hủy bởi người gửi](https://developer.ghn.dev/vi/docs/master-data/reason-code#huy-boi-nguoi-gui)

Các mã đối tác được phép gửi trong API [Hủy đơn](https://developer.ghn.dev/vi/docs/order/cancel).

| reason\_code | Thông điệp (Reason) |
| ----- | ----- |
|  |  |
| GHN-CO001 | Thời gian lấy hàng quá lâu |
| GHN-CO002 | Hết hàng, chưa có hàng để gửi |
| GHN-CO003 | Người nhận không còn nhu cầu nữa |
| GHN-CANCEL-OTHER | Lý do khác |

## [Lấy hàng thất bại](https://developer.ghn.dev/vi/docs/master-data/reason-code#lay-hang-that-bai)

Nhóm GHN-PFA\*, GHN-PFC\* — phát sinh khi lấy hàng không thành công, đơn quay về chờ lấy lại.

| reason\_code | Thông điệp (Reason) |
| ----- | ----- |
|  |  |
| GHN-PFA1A0 | Người gửi hẹn lại ngày lấy hàng |
| GHN-PFA1A1 | Người gửi muốn gửi hàng tại bưu cục |
| GHN-PFA2A1 | Thuê bao không liên lạc được / Máy bận |
| GHN-PFA2A2 | Thông tin lấy hàng sai (địa chỉ / SĐT) |
| GHN-PFA2A3 | Người gửi không nghe máy |
| GHN-PFA3A2 | Nhân viên gặp sự cố |
| GHN-PFA3A4 | Nhân viên gặp sự cố |
| GHN-PFA3A5 | Nhân viên gặp sự cố |
| GHN-PFA3A6 | Nhân viên gặp sự cố |
| GHN-PFA3A7 | Nhân viên gặp sự cố |
| GHN-PFA3A8 | Nhân viên gặp sự cố |
| GHN-PFA3A9 | Nhân viên gặp sự cố |
| GHN-PFA3A10 | Nhân viên gặp sự cố |
| GHN-PFA3A11 | Nhân viên gặp sự cố |
| GHN-PFA4A1 | Hàng vi phạm quy cách đóng gói |
| GHN-PFA4A2 | Hàng hóa GHN không vận chuyển |
| GHN-PFC1A8 | Nhân viên gặp sự cố |
| GHN-PFC1A9 | Nhân viên gặp sự cố |

## [Hủy tại điểm lấy hàng](https://developer.ghn.dev/vi/docs/master-data/reason-code#huy-tai-diem-lay-hang)

Nhóm GHN-PCB\* — đơn bị hủy ngay tại thời điểm lấy hàng.

| reason\_code | Thông điệp (Reason) |
| ----- | ----- |
|  |  |
| GHN-PCB0B1 | Người gửi không muốn gửi hàng nữa |
| GHN-PCB0B2 | Hàng vi phạm quy định khối lượng, kích thước |

## [Giao hàng thất bại](https://developer.ghn.dev/vi/docs/master-data/reason-code#giao-hang-that-bai)

Nhóm GHN-DFC\* — phát sinh khi giao không thành công, đơn chờ giao lại.

| reason\_code | Thông điệp (Reason) |
| ----- | ----- |
|  |  |
| GHN-DFC1A0 | Người nhận hẹn lại ngày giao |
| GHN-DFC1A1 | Người nhận đổi địa chỉ giao hàng |
| GHN-DFC1A2 | Không liên lạc được người nhận / Chặn số |
| GHN-DFC1A4 | Người nhận không nghe máy |
| GHN-DFC1A6 | Nhân viên gặp sự cố |
| GHN-DFC1A7 | Người nhận từ chối nhận do không cho xem / thử hàng |
| GHN-DFC1A9 | Người nhận không đặt hàng |
| GHN-DFC2A0 | Tai nạn/thời tiết |
| GHN-DFC2A1 | Nhân viên gặp sự cố |

## [Người nhận từ chối nhận](https://developer.ghn.dev/vi/docs/master-data/reason-code#nguoi-nhan-tu-choi-nhan)

Nhóm GHN-DCD\* — sự cố tại điểm giao liên quan người nhận, thường dẫn tới hủy hoặc chuyển hoàn.

| reason\_code | Thông điệp (Reason) |
| ----- | ----- |
|  |  |
| GHN-DCD0A1 | Sai thông tin người nhận (địa chỉ / SĐT) |
| GHN-DCD0A5 | Người nhận từ chối nhận do hàng hư hỏng |
| GHN-DCD0A6 | Người nhận từ chối nhận do sai sản phẩm |
| GHN-DCD0A7 | Người nhận từ chối nhận do sai COD |
| GHN-DCD0A8 | Người nhận đổi ý không mua nữa |
| GHN-DCD1A1 | Người nhận báo không đặt hàng |
| GHN-DCD1A5 | Người nhận từ chối nhận do không có tiền |

## [Trả hàng thất bại](https://developer.ghn.dev/vi/docs/master-data/reason-code#tra-hang-that-bai)

Nhóm GHN-RFE\* — phát sinh khi trả hàng về người gửi không thành công.

| reason\_code | Thông điệp (Reason) |
| ----- | ----- |
|  |  |
| GHN-RFE0A0 | Người nhận hàng trả hẹn lại ngày trả hàng |
| GHN-RFE0A1 | Người nhận hàng trả đổi địa chỉ trả hàng |
| GHN-RFE0A3 | Người nhận hàng trả từ chối nhận do sai sản phẩm |
| GHN-RFE0A4 | Người nhận hàng trả từ chối nhận do hàng hư hỏng |
| GHN-RFE0A5 | Nhân viên gặp sự cố |
| GHN-RFE0A6 | Người nhận hàng trả không nghe máy/ Thuê bao không liên lạc được |
| GHN-RFE0A7 | Nhân viên gặp sự cố |
| GHN-RFE0A8 | Nhân viên gặp sự cố |

## [Giao lại](https://developer.ghn.dev/vi/docs/master-data/reason-code#giao-lai)

Hai mã này không gửi kèm thông điệp trong callback (Reason rỗng); nội dung dưới đây là bản hiển thị trên app/web GHN.

| reason\_code | Thông điệp |
| ----- | ----- |
|  |  |
| GHN-RDAI01 | Hệ thống đánh giá giao lại |
| GHN-RDC001 | Người nhận khiếu nại giao hàng |

# **Mã trạng thái đơn hàng**

Toàn bộ giá trị status của một đơn GHN, kèm ý nghĩa và đánh dấu trạng thái kết thúc. Giá trị này xuất hiện ở [Thông tin đơn](https://developer.ghn.dev/vi/docs/order/info) và ở [Callback trạng thái đơn](https://developer.ghn.dev/vi/docs/webhook/callback-order-status).

## [Các giá trị trạng thái](https://developer.ghn.dev/vi/docs/master-data/order-status#cac-gia-tri-trang-thai)

| Trạng thái | Ý nghĩa | Cuối |
| ----- | ----- | ----- |
|  |  |  |
| ready\_to\_pick | Đã tạo đơn, chờ lấy hàng |  |
| picking | Tài xế đang đến lấy hàng |  |
| money\_collect\_picking | Thu tiền khi lấy hàng |  |
| picked | Đã lấy hàng từ shop |  |
| storing | Đang ở kho GHN |  |
| sorting | Đang phân loại |  |
| transporting | Đang vận chuyển giữa các kho |  |
| delivering | Đang giao cho người nhận |  |
| money\_collect\_delivering | Thu tiền khi giao hàng |  |
| delivered | Giao thành công | ✓ |
| delivery\_fail | Giao thất bại |  |
| waiting\_to\_return | Chờ trả về người gửi |  |
| return | Đã yêu cầu trả hàng |  |
| return\_transporting | Đang vận chuyển trả |  |
| return\_sorting | Đang phân loại hàng trả |  |
| returning | Đang trả về người gửi |  |
| return\_fail | Trả hàng thất bại |  |
| returned | Đã trả về người gửi | ✓ |
| cancel | Đã hủy | ✓ |
| exception | Sự cố — cần xử lý thủ công | ✓ |
| lost | Thất lạc | ✓ |
| damage | Hư hỏng | ✓ |
| scrap | Đã tiêu hủy | ✓ |

[image1]: <data:image/png;base64,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>

[image2]: <data:image/png;base64,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>

[image3]: <data:image/png;base64,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>

[image4]: <data:image/png;base64,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>