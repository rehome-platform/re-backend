Mục lục
Tài liệu gồm 14 mục, đi từ bức tranh chung xuống chi tiết kỹ thuật rồi đến các qui định vận hành.
Mục	Nội dung	Đọc để biết gì
0	Các thay đổi sau Review 1	So sánh bản cũ và bản hiện tại, để người đã đọc bản trước biết chỗ nào đã khác
1	Giới thiệu nền tảng	Nền tảng giải quyết vấn đề gì và bằng cách nào
2	Actor và nền tảng	Ai dùng hệ thống, trên web hay mobile
3	Chức năng chính theo actor	Mỗi vai làm được những gì, xem nhanh không cần đọc bảng use case
4	Bảng use case	Danh sách đầy đủ use case kèm mã, dùng để vẽ use case diagram
5	Bảng function	Danh sách function dạng user story, đưa thẳng vào backlog
6	Các luồng chính	Từng nghiệp vụ chạy qua những bước nào, kèm các nhánh hỏng
7	Luồng tiền	Tiền vào đâu, nằm ở đâu, ra bằng cách nào; phần nào thật phần nào giả lập
8	Nguồn thu của nền tảng	Nền tảng sống bằng gì và tính tiền ra sao trên một đơn
9	Xác minh tổ chức	Nhận những loại tổ chức nào, giấy tờ gì, Moderator kiểm chứng thủ công ra sao
10	Giới hạn của đồ án	Phần nào mô phỏng thay vì làm thật và lý do
11	Tham số Admin cấu hình	Mọi con số đổi được mà không cần sửa code
12	Mẫu biên nhận ký gửi	Nội dung hợp đồng giữa nền tảng và người ký gửi
13	Vi phạm và hình thức xử lý	Loại vi phạm nào xử sao, ai quyết định cuối cùng
Đọc nhanh theo nhu cầu: muốn hiểu ý tưởng thì đọc mục 1, 2, 3. Muốn làm tài liệu thiết kế hoặc code thì đọc mục 4, 5, 6, 11. Muốn hiểu phần nghiệp vụ dễ bị hỏi nhất khi bảo vệ thì đọc mục 7, 8, 9, 10.
ReHome — Tài liệu tổng quan hệ thống
0. Các thay đổi sau Review 1
   Ba nhóm thay đổi: quyên góp không lưu dữ liệu, tiền đi qua ví, thử đồ AI gắn vào luồng mua. Phần còn lại giữ nguyên.
   Hạng mục	Bản trước Review 1	Bản hiện tại
   Dữ liệu quyên góp	Member đăng ký kg, tổ chức cân và lưu từng lượt tiếp nhận	Hệ thống không lưu bất kỳ dữ liệu quyên góp nào
   Vai trò Organization	Tạo chiến dịch + tiếp nhận đồ + xuất báo cáo kg	Chỉ tạo chiến dịch và đăng bài viết cộng đồng
   Chiến dịch	Mục tiêu kg, kg đã nhận, kg giữ chỗ	Chỉ thông tin: mô tả, thời gian nhận, địa điểm, loại đồ ưu tiên
   Tiền người bán	Doanh thu theo đơn, Moderator chi thủ công	Cộng vào ví Member, mọi biến động là một giao dịch
   Tiền hoàn người mua	Chuyển khoản thủ công, upload biên lai	Hệ thống tự hoàn vào ví người mua
   Tài khoản ngân hàng	Thêm, đổi, OTP, Moderator xác minh	Bỏ hoàn toàn, không liên kết tài khoản nào
   Chiều tiền ra	Chuyển khoản thật ngoài đời	Giao dịch giả lập, lưu kết quả trong hệ thống
   Tủ đồ	Tủ đồ lấy từ giỏ hàng và đơn đã mua	Bỏ hẳn, chỉ còn bộ sưu tập lưu ảnh đã thử
   Thử đồ AI	4 lối vào, 4 slot Nón / Áo / Quần / Giày	Vào từ trang sản phẩm, 2 slot Áo + Quần, cả hai bắt buộc
   Hoa hồng	Một tỷ lệ % cố định cho cả vòng đời món hàng	Tăng dần theo kỳ: 10% kỳ 1, 15% kỳ 2, 20% kỳ 3; chốt lúc đặt đơn
1. Giới thiệu nền tảng
   ReHome là nền tảng ký gửi và mua bán quần áo đã qua sử dụng có kiểm định tại kho, kèm thử đồ AI và một khu cộng đồng cho các tổ chức từ thiện.
   Vấn đề
   Mua đồ cũ online rủi ro: người mua không biết hàng thật hay giả, tình trạng thực tế ra sao, tiền chuyển trước cho người lạ.
   Người bán ngại đăng tin, chụp ảnh, trả lời khách, đóng gói và gửi hàng.
   Không thử được trước khi mua, khó hình dung món đồ lên người trông thế nào.
   Muốn quyên góp quần áo nhưng không biết đang có chương trình nào, nhận ở đâu, giờ nào.
   Giải pháp
   Ký gửi có kiểm định: người bán mang đồ đến kho, Moderator kiểm tra thật giả và tình trạng, chốt giá, giữ hàng và đăng bán thay. Kho đóng gói, gửi hàng và nhận hàng trả.
   Tiền qua nền tảng: người mua trả cho hệ thống, người bán chỉ nhận tiền vào ví khi đơn hoàn thành. Hai bên không phải tin nhau.
   Thử đồ AI: từ trang sản phẩm, ghép đủ áo và quần rồi sinh ảnh trên người mẫu để hình dung trước khi mua.
   Cộng đồng quyên góp: tổ chức đã xác minh đăng chiến dịch và bài viết; người dùng xem địa điểm, thời gian rồi tự mang đồ đến. Hệ thống chỉ là nơi thông tin, không quản lý việc nhận đồ.
   Món hàng nằm trong kho của nền tảng trước khi được bán, nên chất lượng và dòng tiền đều kiểm soát được.
2. Actor và nền tảng
   Actor	Nền tảng	Vai trò
   Guest	Web + Mobile	Chưa đăng nhập: xem sản phẩm, tổ chức, chiến dịch, bài viết; đăng ký, đăng nhập
   Authenticated User	Web + Mobile	Mọi tài khoản đã đăng nhập: đăng xuất, đổi mật khẩu
   Member	Mobile	Một tài khoản đóng ba vai: người mua, người ký gửi, người quyên góp. Sở hữu một ví
   Organization	Mobile	Tổ chức từ thiện đã xác minh: tạo chiến dịch, đăng bài, chat với Member
   Moderator	Web	Nhân viên thuộc đúng một kho: duyệt, kiểm định, nhập kho, đóng gói, trả hàng, xác minh tổ chức
   Admin	Web	Tài khoản, kho, phân Moderator vào kho, cấu hình, dashboard, audit log
   System	—	Tác nhân nội bộ: hết hạn, tách đơn, tính tiền, ghi giao dịch ví, sinh ảnh AI, thông báo
   Hệ thống ngoài (không phải actor): MoMo, GHN, Google OAuth, dịch vụ tách nền ảnh, FCM/email, bản đồ, cloud storage, mô hình AI tự host.
3. Chức năng chính theo actor
   Guest
   Đăng ký tài khoản Member hoặc Organization
   Đăng nhập
   Đặt lại mật khẩu
   Xem sản phẩm, tổ chức, chiến dịch và bài viết cộng đồng
   Member (mobile)
   Ký gửi:
   Tạo form
   Theo dõi duyệt
   Xác nhận biên nhận
   Hạ giá
   Xử lý cuối kỳ (nhận lại hoặc ký gửi lại)
   Rút lại hàng
   Mua hàng:
   Giỏ hàng
   Cập nhật Địa chỉ
   Đặt hàng
   Thanh toán Qua MoMo hoặc ví
   Theo dõi đơn
   Xác nhận đã nhận hàng nhận hàng
   Đánh giá đơn hàng
   Trả hàng:
   Gửi yêu cầu kèm lý do và ảnh
   Bàn giao cho đơn vị vận chuyển GHN
   Ví:
   Xem số dư và lịch sử giao dịch
   Xem doanh thu theo đơn
   Tạo yêu cầu rút tiền
   Thử đồ AI:
   Thử từ trang sản phẩm
   Lưu và xem bộ sưu tập ảnh đã thử đồ đính kèm sản phẩm
   Cộng đồng:
   Theo dõi tổ chức
   Xem bản đồ địa điểm nhận đồ
   Thích và bình luận bài viết cộng đồng
   Báo cáo tổ chức vi phạm
   Chat với tổ chức
   Chat với của kho gửi hàng hoặc kho bán hàng
   Organization (Mobile)
   Cập nhật hồ sơ thường
   Xin đổi thông tin pháp lý (phải qua Moderator duyệt)
   Tạo, sửa, đóng và xem chiến dịch quyên góp
   Đăng, sửa, xóa bài viết cộng đồng kèm ảnh
   Chat với Member
   Moderator (web)
   Tra cứu Member tại quầy bằng mã Member hoặc số điện thoại
   Duyệt form ký gửi, kiểm định thật giả, chốt giá, nhập kho, in tem SKU
   Quản lý tồn kho của kho mình, đóng gói và tạo vận đơn, bàn giao hàng tại quầy
   Xử lý yêu cầu trả hàng và các ca nghi hàng giả
   Xác minh hồ sơ tổ chức, duyệt đổi thông tin pháp lý, xử lý báo cáo vi phạm
   Quản lý danh mục và thương hiệu được nhận
   Admin (web)
   Quản lý tài khoản, khóa và mở khóa
   Tạo tài khoản Moderator và gán vào kho
   Quản lý chi nhánh kho
   Chỉnh thông số cấu hình: hoa hồng, phí, thuế, các thời hạn
   Xem dashboard, xuất báo cáo Excel (Doanh thu nền tảng), xem audit log
   System
   Tự động hóa theo thời gian: hết hạn form, hủy đơn chưa thanh toán, tự hoàn thành đơn, tự đóng chiến dịch
   Tính tiền, ghi giao dịch ví, hoàn tiền, giả lập chi tiền
   Sinh ảnh thử đồ, tách nền ảnh sản phẩm
   Gửi thông báo, đồng bộ trạng thái vận chuyển GHN, ghi audit log
4. Bảng use case
   ID	Use case	Actor	Mô tả ngắn
   UC-01	Login	Guest	Đăng nhập bằng tài khoản hoặc Google; tổ chức được điều hướng theo trạng thái xác minh
   UC-02	Reset Password	Guest	Quên mật khẩu thì đặt lại qua liên kết gửi về email
   UC-03	Register Member Account	Guest	Tạo tài khoản Member — vừa mua, vừa ký gửi, vừa quyên góp
   UC-04	Register Organization Account	Guest	Gửi hồ sơ tổ chức kèm giấy tờ pháp lý, chờ Moderator xác minh
   UC-05	Logout	Authenticated User	Kết thúc phiên, hủy token
   UC-06	Change Password	Authenticated User	Nhập mật khẩu cũ và mật khẩu mới
   UC-07	View Profile	Member	Xem họ tên, ảnh đại diện, SDT, email, trạng thái xác minh danh tính
   UC-08	Update Profile	Member	Sửa họ tên, ảnh đại diện, số điện thoại
   UC-09	Show Member Code	Member	Xem mã Member để đọc hoặc cho Moderator xem khi đến kho
   UC-10	Browse Products	Guest, Member	Xem danh sách, tìm kiếm, lọc và mở chi tiết sản phẩm đang bán
   UC-11	View Organization Profile	Guest, Member	Xem hồ sơ tổ chức đã xác minh, chiến dịch và bài viết của họ
   UC-12	View Donation Campaign	Guest, Member	Xem thông tin chiến dịch: địa điểm, khung giờ nhận, loại đồ ưu tiên
   UC-13	View Community Post	Guest, Member	Xem bài viết cộng đồng của các tổ chức; Member có thêm news feed
   UC-14	Submit Consignment Form	Member	Gửi một món đồ kèm ảnh, thương hiệu, các thông tin của sản phẩm
   UC-15	View Consignment Form	Member	Xem trạng thái, lý do từ chối và hạn mang đồ đến kho
   UC-16	Confirm Consignment Receipt	Member	Xem điều khoản và bấm xác nhận tại quầy để sản phẩm được lên sàn
   UC-17	View Consigned Product	Member	Xem SKU, kho, giá bán, kỳ ký gửi, ngày hết kỳ, tỷ lệ hoa hồng kỳ hiện tại và tiền dự kiến nhận
   UC-18	Reduce Selling Price	Member	Nhập giá mới bằng số tiền bất kỳ lúc nào, áp dụng ngay; chỉ được giảm và không thấp hơn giá sàn
   UC-19	Handle End of Term	Member	Hết 90 ngày: chọn nhận lại hàng hoặc ký gửi lại với giá mới
   UC-20	Renew Consignment	Member	Thương lượng giá mới với Moderator và xác nhận biên nhận mới, hàng vẫn ở kho
   UC-21	Request Item Withdrawal	Member	Rút sản phẩm khỏi sàn, trả phí nếu ngoài cửa sổ rút miễn phí
   UC-22	Handle Degraded Item	Member	Món hàng về kho trong tình trạng xấu hơn: chọn nhận tiền đền hoặc nhận lại hàng kèm đền một phần
   UC-23	View Wallet	Member	Xem số dư và toàn bộ lịch sử giao dịch tiền vào, tiền ra
   UC-24	View Revenue	Member	Xem từng đơn đã hoàn thành và số tiền đã cộng vào ví
   UC-25	Request Payout	Member	Rút tiền từ ví; hệ thống ghi nhận bằng giao dịch chi tiền
   UC-26	Add Product to Cart	Member	Thêm sản phẩm vào giỏ, từ trang sản phẩm hoặc từ kết quả thử đồ
   UC-27	View Cart	Member	Xem sản phẩm trong giỏ kèm kho đang giữ và tổng tạm tính
   UC-28	Remove Product from Cart	Member	Xóa sản phẩm khỏi giỏ hàng
   UC-29	View Shipping Address	Member	Xem danh sách địa chỉ và địa chỉ mặc định
   UC-30	Add Shipping Address	Member	Nhập người nhận, SDT và địa chỉ chi tiết
   UC-31	Edit Shipping Address	Member	Sửa thông tin hoặc đặt làm mặc định
   UC-32	Delete Shipping Address	Member	Xóa địa chỉ khỏi danh sách
   UC-33	Place Order	Member	Đặt từ giỏ hoặc Mua ngay; giỏ nhiều kho được tách thành nhiều đơn
   UC-34	Make Online Payment	Member	Chọn thanh toán bằng ví hoặc bằng MoMo, trả trọn đơn bằng phương thức đã chọn
   UC-35	View Order	Member	Xem danh sách và chi tiết đơn kèm mã vận đơn
   UC-36	Track Order	Member	Theo dõi trạng thái đơn đồng bộ từ GHN
   UC-37	Cancel Order	Member	Hủy đơn đã thanh toán khi kho chưa đóng gói, hoàn 100%
   UC-38	Confirm Order Receipt	Member	Xác nhận đơn đã giao; không bấm thì hệ thống tự hoàn thành
   UC-39	Rate Order Service	Member	Chấm 1–5 sao cho đơn hàng
   UC-40	Request Return	Member	Gửi yêu cầu trả hàng trong thời hạn, kèm lý do và ảnh
   UC-41	Try On Outfit	Member	Từ trang sản phẩm: chọn người mẫu, ghép đủ áo + quần rồi sinh ảnh thử
   UC-42	View Try-on Collection	Member	Xem các ảnh thử đồ đã lưu
   UC-43	Delete Try-on Image	Member	Xóa ảnh khỏi bộ sưu tập
   UC-44	Follow Organization	Member	Theo dõi để nhận bài viết mới trên newsfeed, có thông báo khi có chiến dịch hoặc bài đăng
   UC-45	View Nearby Locations on Map	Member	Xem tổ chức và địa điểm nhận đồ gần vị trí hiện tại
   UC-46	Like and Comment Post	Member	Tương tác với bài viết cộng đồng
   UC-47	Report Violation	Member	Báo cáo bài viết, chiến dịch hoặc tài khoản tổ chức
   UC-48	Send Message	Member, Organization, Moderator	Chat Member – tổ chức và Member – hộp thư chung của kho
   UC-49	Receive Notification	Member, Organization, Moderator	Nhận thông báo thời gian thực theo vai trò
   UC-50	Update Organization Profile	Organization	Sửa avatar, mô tả, SDT, giờ nhận đồ — áp dụng ngay
   UC-51	Request Legal Information Change	Organization	Gửi yêu cầu đổi tên, người đại diện, giấy tờ liên quan pháp lý để Moderator duyệt
   UC-52	View Own Campaign	Organization	Xem danh sách và chi tiết chiến dịch của mình
   UC-53	Create Campaign	Organization	Tạo chiến dịch: mô tả, thời gian, địa điểm nhận, loại đồ ưu tiên
   UC-54	Edit Campaign	Organization	Cập nhật chiến dịch đang diễn ra
   UC-55	Close Campaign	Organization	Đóng thủ công; hết thời gian nhận thì tự đóng
   UC-56	View Own Post	Organization	Xem bài đã đăng kèm số thích và bình luận
   UC-57	Create Community Post	Organization	Đăng bài viết kèm ảnh, hiển thị ngay
   UC-58	Edit Community Post	Organization	Sửa nội dung bài của tổ chức mình
   UC-59	Delete Community Post	Organization	Xóa bài của tổ chức mình
   UC-60	Look Up Member at Counter	Moderator	Nhập mã Member hoặc số điện thoại để mở hồ sơ và việc cần xử lý của Member
   UC-61	Review Consignment Form	Moderator	Nhận việc từ hàng đợi kho, duyệt hoặc từ chối kèm lý do
   UC-62	Inspect and Stock Item	Moderator	Kiểm thật/giả, chốt giá và kỳ ký gửi, nhập kho, gán SKU và in tem
   UC-63	View Inventory	Moderator	Xem sản phẩm trong kho mình kèm SKU, vị trí kệ, lịch sử nhập xuất
   UC-64	Update Inventory	Moderator	Cập nhật vị trí kệ và ghi nhận biến động nhập xuất
   UC-65	Pack and Ship Order	Moderator	Quét SKU đối chiếu, đóng gói và tạo vận đơn GHN
   UC-66	Hand Over Item to Member	Moderator	Giao hàng cho người bán rút hàng hoặc người mua nhận hàng trả
   UC-67	Handle Unclaimed Item	Moderator	Xác nhận món vô thừa nhận
   UC-68	Process Return Request	Moderator	Duyệt yêu cầu, kiểm hàng về kho và quyết định cách xử lý món hàng
   UC-69	Verify Organization Registration	Moderator	Kiểm giấy tờ theo checklist, xác thực hoặc từ chối kèm lý do
   UC-70	Review Legal Change Request	Moderator	Duyệt hoặc từ chối yêu cầu đổi thông tin pháp lý của tổ chức
   UC-71	Handle Violation Report	Moderator	Gỡ nội dung, ẩn chiến dịch hoặc bác bỏ báo cáo
   UC-72	View Category and Brand	Moderator	Xem danh mục kèm slot thử đồ và thương hiệu được nhận
   UC-73	Create Category and Brand	Moderator	Thêm danh mục mới kèm slot Áo / Quần hoặc thương hiệu mới
   UC-74	Edit Category and Brand	Moderator	Sửa tên hoặc đổi slot thử đồ
   UC-75	Hide Category and Brand	Moderator	Ẩn mục không còn dùng, vẫn giữ dữ liệu cũ
   UC-76	View Account	Admin	Xem, tìm và mở chi tiết mọi tài khoản
   UC-77	Ban Account	Admin	Khóa tài khoản vi phạm
   UC-78	Unban Account	Admin	Mở lại tài khoản đã bị khóa
   UC-79	Create Moderator Account	Admin	Tạo Moderator mới và gán vào một kho
   UC-80	Update Moderator Account	Admin	Sửa thông tin Moderator — Moderator không tự sửa được
   UC-81	Assign Moderator to Warehouse	Admin	Gán hoặc chuyển Moderator sang kho khác
   UC-82	View Warehouse	Admin	Xem danh sách kho kèm số Moderator và tình trạng tồn
   UC-83	Create Warehouse	Admin	Tạo kho mới kèm địa chỉ, tọa độ và giờ mở cửa
   UC-84	Edit Warehouse	Admin	Cập nhật thông tin kho
   UC-85	Hide Warehouse	Admin	Ẩn kho không còn hoạt động
   UC-86	View System Configuration	Admin	Xem phí, hoa hồng, thuế, kỳ ký gửi, giá sàn và các thời hạn
   UC-87	Update System Configuration	Admin	Sửa tham số, chỉ áp dụng cho giao dịch phát sinh sau đó
   UC-88	View Dashboard	Admin	Xem doanh thu, đơn, tồn kho, đánh giá dịch vụ và số dư ví
   UC-89	Export Excel Report	Admin	Xuất số liệu dashboard ra file Excel
   UC-90	View Audit Log	Admin	Xem nhật ký thao tác Moderator, giao dịch tiền và thay đổi cấu hình
5. Bảng function
   162 function viết dạng user story As a …, I want … so that … để đưa thẳng vào backlog. Các dòng As the system là technical story, không có actor người dùng.
   ID	Tên tiếng Việt	User story	Nền tảng
   F-001	Đăng ký tài khoản Member	As a guest, I want to register a Member account so that I can buy, consign and donate.	Mobile
   F-002	Đăng ký tài khoản Organization	As a guest, I want to register an organization account with legal documents so that my organization can be verified.	Mobile
   F-003	Gửi lại hồ sơ bị từ chối	As an organization, I want to resubmit my rejected profile so that I can fix what the moderator pointed out.	Mobile
   F-004	Đăng nhập	As a guest, I want to log in by account or Google so that I can use the features of my role.	Web, Mobile
   F-005	Điều hướng tổ chức theo trạng thái	As the system, I want to route an organization by verification status so that it only sees screens it may use.	Mobile
   F-006	Đăng xuất	As a logged-in user, I want to log out so that my session is closed on this device.	Web, Mobile
   F-007	Đặt lại mật khẩu	As a guest, I want to reset a forgotten password by email so that I can get back into my account.	Web, Mobile
   F-008	Đổi mật khẩu	As a logged-in user, I want to change my password so that my account stays secure.	Web, Mobile
   F-009	Xem hồ sơ cá nhân	As a Member, I want to view my profile so that I can check my information and verification status.	Mobile
   F-010	Cập nhật hồ sơ cá nhân	As a Member, I want to update my name, avatar and phone so that my information stays correct.	Mobile
   F-011	Hiển thị mã Member	As a Member, I want to view my member code so that a moderator can look me up at the warehouse.	Mobile
   F-012	Xem sản phẩm	As a guest or Member, I want to view products on sale so that I can find something to buy.	Mobile
   F-013	Tìm kiếm và lọc sản phẩm	As a guest or Member, I want to search and filter products so that I can narrow down to what fits me.	Mobile
   F-014	Tạo form ký gửi	As a Member, I want to submit a consignment form with photos, brand and desired price so that the warehouse can review my item.	Mobile
   F-015	Gợi ý kho gần nhất	As the system, I want to suggest the nearest warehouse so that the member does not travel far.	Mobile
   F-016	Giới hạn form đang chờ	As the system, I want to limit pending forms per member so that the review queue is not flooded.	Mobile
   F-017	Sửa form đang chờ duyệt	As a Member, I want to edit a pending form so that I can correct it before review.	Mobile
   F-018	Xem form ký gửi	As a Member, I want to view my forms so that I know the status and the deadline to bring the item.	Mobile
   F-019	Hết hạn form đã duyệt	As the system, I want to expire approved forms not brought in on time so that warehouse slots are freed.	Web, Mobile
   F-020	Xem biên nhận ký gửi	As a Member, I want to read the receipt with its term, floor price and end-of-term rules so that I know what I am agreeing to.	Mobile
   F-021	Xác nhận biên nhận ký gửi	As a Member, I want to confirm the receipt so that my item can be listed for sale.	Mobile
   F-022	Sinh biên nhận PDF	As the system, I want to snapshot the term, the commission rate of every term, fees, floor price and the unclaimed-item clause into a PDF so that later config changes do not affect this contract.	Web, Mobile
   F-023	Mở lại biên nhận đã xác nhận	As a Member, I want to reopen a confirmed receipt so that I can check the fees and terms later.	Mobile
   F-024	Xem sản phẩm đang ký gửi	As a Member, I want to see the end date and how many renewals are left so that I know when I must decide.	Mobile
   F-025	Giảm giá bán	As a Member, I want to type a new price as an amount and see the floor price, the commission of the current term and what I would receive before confirming so that I can decide how far to drop it.	Mobile
   F-026	Chặn giá dưới sàn	As the system, I want to reject a price below the floor or above the agreed price so that the platform does not sell at a loss.	Mobile
   F-027	Tự gia hạn kỳ ký gửi	As the system, I want to renew the term up to the configured limit so that an unsold item stays listed without manual work.	Web, Mobile
   F-028	Kết thúc kỳ ký gửi	As the system, I want to unlist the item after the last term and open the free-withdrawal window so that the contract has a clear end.	Web, Mobile
   F-029	Chọn cách xử lý cuối kỳ	As a Member, I want to choose between taking the item back and consigning it again so that I decide what happens to it.	Mobile
   F-030	Ký gửi lại với giá mới	As a Member, I want to agree a new price with the moderator and confirm a new receipt so that the item is listed again without leaving the warehouse.	Mobile, Web
   F-031	Đánh dấu hàng vô thừa nhận	As the system, I want to flag an item not claimed within the deadline so that the warehouse can clear it.	Web, Mobile
   F-032	Tạo yêu cầu rút lại hàng	As a Member, I want to request my item back so that I can stop selling it.	Mobile
   F-033	Trả phí rút hàng	As a Member, I want to pay the withdrawal fee from my wallet or MoMo so that my request is accepted.	Mobile
   F-034	Đóng yêu cầu khi bàn giao	As the system, I want to close the request when the item is handed over so that the record matches reality.	Web, Mobile
   F-035	Hết hạn yêu cầu rút hàng	As the system, I want to expire uncollected withdrawal requests so that the item goes back on sale.	Web, Mobile
   F-036	Xử lý hàng xuống cấp	As a Member, I want to choose between being paid for a degraded item and taking it back with partial compensation so that I do not bear a loss I did not cause.	Mobile
   F-037	Xem ví và số dư	As a Member, I want to see my wallet balance so that I know how much I can withdraw.	Mobile
   F-038	Xem lịch sử giao dịch ví	As a Member, I want to see my wallet transactions so that I can trace every amount in and out.	Mobile
   F-039	Ghi giao dịch ví và bút toán quỹ	As the system, I want to record every balance change as a wallet transaction with a matching entry on the platform account so that the books balance and both sides are auditable.	Web, Mobile
   F-040	Xem doanh thu theo đơn	As a Member, I want to see revenue per completed order so that I know what each sale paid me.	Mobile
   F-041	Hoàn tiền vào ví người mua	As the system, I want to credit a valid refund into the buyer's wallet as a transaction so that no real transfer is needed.	Web, Mobile
   F-042	Tạo yêu cầu rút tiền	As a Member, I want to withdraw an amount from my wallet without waiting for anyone to approve it so that I get my money quickly.	Mobile
   F-043	Kiểm tra và khóa số dư rút	As the system, I want to check the minimum amount, the daily limit and the available balance, then lock the amount, so that the request is safe without a human approver.	Web, Mobile
   F-044	Giả lập chi tiền và ghi kết quả	As the system, I want to execute the payout as a simulated transfer and record its result so that the flow completes without moving real money.	Web, Mobile
   F-045	Thêm sản phẩm vào giỏ	As a Member, I want to add a product to my cart so that I can buy it.	Mobile
   F-046	Xem giỏ hàng	As a Member, I want to view my cart so that I can check items and total before ordering.	Mobile
   F-047	Xóa sản phẩm khỏi giỏ	As a Member, I want to remove a product from my cart so that I only pay for what I want.	Mobile
   F-048	Xem địa chỉ giao hàng	As a Member, I want to view my saved addresses so that I can pick one at checkout.	Mobile
   F-049	Thêm địa chỉ giao hàng	As a Member, I want to add a shipping address so that my order is delivered to the right place.	Mobile
   F-050	Sửa địa chỉ giao hàng	As a Member, I want to edit an address or set it as default so that checkout is correct and fast.	Mobile
   F-051	Xóa địa chỉ giao hàng	As a Member, I want to delete an address so that my address list stays clean.	Mobile
   F-052	Đặt hàng	As a Member, I want to place an order from my cart or Buy now so that I can receive the items.	Mobile
   F-053	Tách đơn theo kho	As the system, I want to split a multi-warehouse cart into one order per warehouse so that each warehouse ships its own parcel.	Mobile
   F-054	Tính phí vận chuyển	As the system, I want to calculate the shipping fee per order so that the member sees the real total.	Mobile
   F-055	Giữ sản phẩm khi chờ thanh toán	As the system, I want to reserve the product while payment is pending so that two members cannot buy the same item.	Web, Mobile
   F-056	Thanh toán bằng ví hoặc MoMo	As a Member, I want to choose between paying with my wallet balance or via MoMo so that I can use money I already have in the app when it covers the order.	Mobile
   F-057	Xử lý webhook thanh toán	As the system, I want to process the MoMo webhook so that order status matches the real payment.	Web, Mobile
   F-058	Tự hủy đơn quá hạn thanh toán	As the system, I want to cancel unpaid orders after the deadline so that reserved items are released.	Web, Mobile
   F-059	Xem đơn hàng	As a Member, I want to view my orders so that I can see details and the tracking code.	Mobile
   F-060	Theo dõi trạng thái đơn	As a Member, I want to track my order so that I know when it arrives.	Mobile
   F-061	Hủy đơn đã thanh toán	As a Member, I want to cancel a paid order before packing so that I get a full refund.	Mobile
   F-062	Tự hủy đơn khi GHN lỗi	As the system, I want to cancel the order when the carrier fails or loses the parcel so that the buyer is refunded.	Web, Mobile
   F-063	Xác nhận đã nhận hàng	As a Member, I want to confirm receipt so that the order is completed.	Mobile
   F-064	Tự hoàn thành đơn đã giao	As the system, I want to auto-complete delivered orders after the window so that payouts are not blocked.	Web, Mobile
   F-065	Tính tiền và cộng vào ví người bán	As the system, I want to compute the seller amount using the commission rate of the term the order was placed in and credit the wallet as a transaction so that the seller is paid on completion.	Web, Mobile
   F-066	Đánh giá dịch vụ đơn hàng	As a Member, I want to rate packing and delivery so that warehouse service is measured.	Mobile
   F-067	Gửi yêu cầu trả hàng	As a Member, I want to request a return with reason and photos so that I can send back a wrong item.	Mobile
   F-068	Bàn giao hàng trả cho GHN	As a Member, I want to hand the returned parcel to the carrier so that my return is processed.	Mobile
   F-069	Hủy yêu cầu khi không bàn giao	As the system, I want to cancel returns not handed over in time so that the order can be closed.	Web, Mobile
   F-070	Chọn cách nhận hàng trả không hợp lệ	As a Member, I want to choose ship-back or warehouse pickup for an invalid return so that I can get my item back.	Mobile
   F-071	Hết hạn nhận hàng trả không hợp lệ	As the system, I want to expire uncollected invalid returns so that items are not stored forever.	Web, Mobile
   F-072	Mở phòng thử đồ từ trang sản phẩm	As a Member, I want to tap try-on on a product so that I can see it worn before buying.	Mobile
   F-073	Chọn người mẫu	As a Member, I want to choose a male or female model so that the result looks closer to me.	Mobile
   F-074	Khóa sản phẩm đang xem vào slot	As the system, I want to lock the viewed product into its slot so that the outfit is built around the item being considered.	Mobile
   F-075	Gợi ý món còn thiếu	As the system, I want to suggest matching items for the empty slot so that the member can complete the outfit in one tap.	Mobile
   F-076	Tự chọn món còn thiếu	As a Member, I want to pick the other item myself so that the outfit matches what I have in mind.	Mobile
   F-077	Kiểm tra đủ áo và quần	As the system, I want to require both a top and a bottom so that the generated image is a complete outfit.	Mobile
   F-078	Sinh ảnh thử đồ	As the system, I want to generate the try-on image so that the member sees the outfit worn on the model.	Mobile
   F-079	Lưu ảnh vào bộ sưu tập	As the system, I want to save the generated image to the member's collection so that it can be opened again later.	Mobile
   F-080	Xem bộ sưu tập thử đồ	As a Member, I want to view my saved try-on images so that I can compare outfits before buying.	Mobile
   F-081	Xóa ảnh thử đồ	As a Member, I want to delete a try-on image so that my collection keeps only what I like.	Mobile
   F-082	Xem hồ sơ tổ chức	As a guest or Member, I want to view a verified organization so that I can decide whether to donate to it.	Mobile
   F-083	Xem chiến dịch quyên góp	As a guest or Member, I want to view campaign details so that I know where and when to bring my clothes.	Mobile
   F-084	Xem địa điểm gần trên bản đồ	As a Member, I want to see nearby drop-off points on a map so that I can pick the closest one.	Mobile
   F-085	Theo dõi tổ chức	As a Member, I want to follow an organization so that its new posts appear in my feed.	Mobile
   F-086	Xem bài viết cộng đồng	As a guest or Member, I want to read community posts so that I can see how donations are used.	Mobile
   F-087	Thích và bình luận bài viết	As a Member, I want to like and comment on a post so that I can interact with the organization.	Mobile
   F-088	Báo cáo vi phạm	As a Member, I want to report a post, campaign or account so that a moderator can review it.	Mobile
   F-089	Chat với tổ chức	As a Member, I want to chat with an organization so that I can ask about a campaign.	Web, Mobile
   F-090	Chat với hộp thư kho	As a Member, I want to chat with the warehouse inbox so that I can ask about my item or order.	Mobile
   F-091	Trả lời hộp thư kho	As a Moderator, I want to answer the warehouse inbox so that members get support.	Web
   F-092	Thông báo cho Member	As the system, I want to notify members about orders, wallet events, term expiry and the unclaimed-item deadline so that they act in time.	Mobile
   F-093	Thông báo cho Organization	As the system, I want to notify organizations of verification and report results so that they can respond.	Web
   F-094	Thông báo cho Moderator	As the system, I want to notify moderators of new queue tasks so that work is not delayed.	Web
   F-095	Báo Admin việc quá hạn	As the system, I want to alert the admin about overdue tasks so that bottlenecks are handled.	Web
   F-096	Cập nhật thông tin thường	As an organization, I want to update my avatar, description and receiving hours so that donors see current information.	Web
   F-097	Yêu cầu đổi thông tin pháp lý	As an organization, I want to request a legal information change so that a moderator can verify it.	Web
   F-098	Tạo chiến dịch	As an organization, I want to create a campaign with time, place and wanted item types so that donors know where to come.	Web
   F-099	Giới hạn số chiến dịch đang chạy	As the system, I want to cap concurrent campaigns per organization so that campaigns stay manageable.	Web
   F-100	Sửa chiến dịch	As an organization, I want to edit a running campaign so that donors see the latest information.	Web
   F-101	Đóng chiến dịch	As an organization, I want to close a campaign so that donors stop coming.	Web
   F-102	Tự đóng chiến dịch	As the system, I want to close a campaign when its receiving period ends so that no outdated campaign is shown.	Web
   F-103	Xem chiến dịch của tổ chức	As an organization, I want to view my campaigns so that I can edit or close them.	Web
   F-104	Đăng bài viết cộng đồng	As an organization, I want to publish a post with text and photos about what we did with the donated clothes so that donors trust us.	Web
   F-105	Sửa bài viết cộng đồng	As an organization, I want to edit my post so that I can fix content after publishing.	Web
   F-106	Xóa bài viết cộng đồng	As an organization, I want to delete my post so that outdated content is removed.	Web
   F-107	Xem bài viết đã đăng	As an organization, I want to view my published posts so that I can see likes and comments.	Web
   F-108	Tra cứu Member tại kho	As a Moderator, I want to look up a member by member code or phone number so that I can serve them even when they cannot open the app.	Web
   F-109	Mở hồ sơ Member tại kho	As a Moderator, I want to open the member's pending tasks so that I serve the right request.	Web
   F-110	Kiểm tra mã vừa quét	As the system, I want to resolve the lookup to exactly one account and show its name and avatar for the moderator to confirm so that no wrong account is opened.	Web
   F-111	Xem hàng đợi công việc	As a Moderator, I want to see my warehouse queues so that I know what is waiting.	Web
   F-112	Nhận việc	As a Moderator, I want to claim a task so that two moderators do not handle the same case.	Web
   F-113	Tự nhả việc đã nhận	As the system, I want to release claimed tasks after 48h so that abandoned tasks return to the queue.	Web
   F-114	Duyệt form ký gửi	As a Moderator, I want to approve or reject a form with a reason so that only suitable items come in.	Web
   F-115	Bắt đầu kiểm định	As a Moderator, I want to start an inspection so that the item is checked against its form.	Web
   F-116	Xác nhận đúng người tại quầy	As a Moderator, I want to confirm that the person at the counter is the account holder by checking the name and avatar so that I hand the item to the right person.	Web
   F-117	Ghi kết quả kiểm định và chốt giá	As a Moderator, I want to record the inspection, the agreed price and the consignment term so that the listing and the receipt are accurate.	Web
   F-118	Trả đồ tại chỗ	As a Moderator, I want to return a failed item on the spot so that it is not stored in the warehouse.	Web
   F-119	Nhập thông tin sản phẩm và nhập kho	As a Moderator, I want to enter product details and stock the item so that it can be listed.	Web
   F-120	Xử lý ảnh try-on	As the system, I want to remove the background of the product photo so that it can be used for try-on.	Web
   F-121	Thêm nhanh thương hiệu	As a Moderator, I want to quick-add a missing brand so that stocking is not blocked.	Web
   F-122	In tem SKU	As a Moderator, I want to print the SKU label so that the item can be found on the shelf.	Web
   F-123	Xem tồn kho	As a Moderator, I want to view my warehouse inventory so that I know what is in stock and where.	Web
   F-124	Quét QR sản phẩm	As a Moderator, I want to scan a product QR so that I open the right item quickly.	Web
   F-125	Cập nhật vị trí kệ	As a Moderator, I want to update the shelf location so that picking is fast.	Web
   F-126	Ghi nhận biến động kho	As the system, I want to log every stock movement so that inventory history is traceable.	Web
   F-127	Đóng gói và tạo vận đơn	As a Moderator, I want to pack the order and create a GHN shipment so that it can be delivered.	Web
   F-128	Đồng bộ trạng thái GHN	As the system, I want to sync carrier status so that order tracking is up to date.	Web, Mobile
   F-129	Bàn giao hàng tại quầy	As a Moderator, I want to hand an item over at the counter so that the member receives it.	Web
   F-130	Xử lý hàng vô thừa nhận	As a Moderator, I want to confirm an unclaimed item and pass it to a partner charity so that the warehouse is cleared with a record.	Web
   F-131	Xử lý yêu cầu trả hàng	As a Moderator, I want to handle a return request so that the parcel comes back to the warehouse.	Web
   F-132	Kiểm tra hàng trả	As a Moderator, I want to inspect returned goods so that I can judge the return fairly.	Web
   F-133	Quyết định xử lý món hàng trả	As a Moderator, I want to decide relist, return to seller or discard so that the item has a clear outcome.	Web
   F-134	Ràng buộc kiểm tra hàng nghi giả	As the system, I want to require a second moderator for suspected counterfeits so that the judgement is fair.	Web
   F-135	Kết luận hàng giả	As a Moderator, I want to conclude a counterfeit case so that the item is destroyed and the buyer refunded.	Web
   F-136	Xử lý hàng trả thất lạc	As the system, I want to handle lost return parcels so that the buyer is not left waiting.	Web, Mobile
   F-137	Xác minh hồ sơ tổ chức	As a Moderator, I want to check an organization's documents against a checklist so that only real organizations are listed.	Web
   F-138	Xác thực hoặc từ chối tổ chức	As a Moderator, I want to approve or reject with a reason so that the organization knows the outcome.	Web
   F-139	Hạn chế và tự xóa giấy tờ	As the system, I want to restrict and auto-delete legal images so that sensitive documents are not kept.	Web
   F-140	Duyệt yêu cầu đổi thông tin pháp lý	As a Moderator, I want to review a legal change request so that profile data stays trustworthy.	Web
   F-141	Xử lý báo cáo vi phạm	As a Moderator, I want to act on a violation report so that bad content is removed.	Web
   F-142	Xem danh mục và thương hiệu	As a Moderator, I want to view categories and brands so that I know what is accepted.	Web

F-143	Thêm danh mục và thương hiệu	As a Moderator, I want to add a category or brand so that new item types can be consigned.	Web
F-144	Sửa danh mục và thương hiệu	As a Moderator, I want to edit a category or brand so that names and try-on slots stay correct.	Web
F-145	Ẩn danh mục và thương hiệu	As a Moderator, I want to hide an unused entry so that it disappears without losing old data.	Web
F-146	Xem tài khoản	As an Admin, I want to search and view accounts so that I can handle support and violations.	Web
F-147	Khóa tài khoản	As an Admin, I want to ban an account so that a violating user cannot use the platform.	Web
F-148	Mở khóa tài khoản	As an Admin, I want to unban an account so that a cleared user can come back.	Web
F-149	Tạo tài khoản Moderator	As an Admin, I want to create a moderator account so that a branch has staff.	Web
F-150	Cập nhật tài khoản Moderator	As an Admin, I want to update a moderator account so that staff information stays correct.	Web
F-151	Gán Moderator vào kho	As an Admin, I want to assign or move a moderator between warehouses so that staffing matches each branch's load.	Web
F-152	Xem chi nhánh kho	As an Admin, I want to view warehouses so that I can see staffing and stock at a glance.	Web
F-153	Tạo chi nhánh kho	As an Admin, I want to create a warehouse so that the platform can open a new branch.	Web
F-154	Sửa chi nhánh kho	As an Admin, I want to edit a warehouse so that its information stays correct.	Web
F-155	Ẩn chi nhánh kho	As an Admin, I want to hide a closed warehouse so that it is no longer selectable.	Web
F-156	Xem thông số cấu hình	As an Admin, I want to view fees, commission, term length, floor price and deadlines so that I know the current rules.	Web
F-157	Chỉnh thông số cấu hình	As an Admin, I want to update a parameter so that business rules can change without a deploy.	Web
F-158	Xem dashboard	As an Admin, I want to see orders, stock, platform revenue, money held for members and the fund balance so that I can monitor the platform.	Web
F-159	Xuất báo cáo Excel	As an Admin, I want to export dashboard data to Excel so that I can report outside the system.	Web
F-160	Xem audit log	As an Admin, I want to read the audit log so that I can trace who did what.	Web
F-161	Ghi audit log	As the system, I want to log moderator actions, money transactions and config changes so that everything is traceable.	Web, Mobile
F-162	Đền bù hàng xuống cấp	As the system, I want to credit the agreed compensation into the seller's wallet as a transaction so that the seller does not bear a loss caused by the platform or the carrier.	Web, Mobile
6. Các luồng chính
   Mỗi luồng viết theo dạng Actor (hành động) → Actor (hành động). Dòng Unhappy là các nhánh rẽ.
   L-01 Đăng ký và đăng nhập
   Guest (đăng ký tài khoản) → System (gửi OTP / xác thực email) → Guest (đăng nhập) → System (điều hướng theo vai)
   Tổ chức: Guest (đăng ký kèm giấy tờ) → System (đặt trạng thái Chờ xác minh) → Moderator (xác minh) → System (mở đầy đủ chức năng)
   Unhappy — hồ sơ bị từ chối: Moderator (từ chối kèm lý do) → Organization (sửa và gửi lại) → quay lại bước xác minh
   Unhappy — sai mật khẩu: Guest (quên mật khẩu) → System (gửi liên kết đặt lại) → Guest (đặt mật khẩu mới)
   Unhappy — tài khoản bị khóa: System (chặn đăng nhập, báo lý do)
   L-02 Ký gửi sản phẩm
   Member (tạo form ký gửi, chọn kho) → Moderator (duyệt form) → Member (mang đồ đến kho) → Moderator (tra cứu mã Member, kiểm định thật giả, chốt giá và kỳ ký gửi) → Member (xác nhận biên nhận) → Moderator (nhập kho, gán SKU, in tem) → System (đăng sản phẩm lên sàn)
   Vòng đời kỳ: System (hết 30 ngày, tự gia hạn, mở cửa sổ rút miễn phí 7 ngày) → Member (hạ giá cho kỳ mới) → System (hết kỳ thứ ba ở ngày 90, gỡ khỏi sàn, chuyển Chờ xử lý cuối kỳ, đếm ngược 30 ngày) → Member (chọn nhận lại hàng hoặc ký gửi lại) → Moderator (bàn giao hàng hoặc chốt giá mới và tạo biên nhận mới)
   Unhappy — form bị từ chối: Moderator (từ chối kèm lý do) → Member (sửa form hoặc bỏ)
   Unhappy — không mang đồ đến đúng hạn: System (hết hạn form, đóng yêu cầu)
   Unhappy — hàng không đạt hoặc nghi giả: Moderator (từ chối, trả đồ tại chỗ) → kết thúc
   Unhappy — không đồng ý giá: Member (từ chối giá) → Moderator (trả đồ tại chỗ)
   Unhappy — không xác nhận biên nhận: Member (không bấm xác nhận) → Moderator (không nhập kho, trả đồ)
   Unhappy — nhập giá dưới sàn hoặc đòi tăng giá: System (chặn, báo phải thương lượng lại biên nhận)
   Unhappy — cuối kỳ không chọn gì trong 30 ngày: System (đánh dấu Vô thừa nhận) → Moderator (xác nhận, chuyển cho tổ chức từ thiện đối tác)
   Unhappy — ký gửi lại nhưng không chốt được giá: Moderator (giữ nguyên trạng thái Chờ xử lý cuối kỳ) → Member (đến nhận lại hàng)
   L-03 Mua hàng và thanh toán
   Member (xem sản phẩm, thêm vào giỏ) → Member (chọn địa chỉ, đặt hàng) → System (tách đơn theo kho, tính phí ship, giữ sản phẩm) → Member (chọn thanh toán bằng ví hoặc MoMo) → System (trừ ví ngay nếu chọn ví, hoặc nhận webhook nếu chọn MoMo — xác nhận đơn, chuyển sản phẩm sang Đã bán)
   Unhappy — không thanh toán đúng hạn: System (hủy đơn, nhả sản phẩm về đang bán)
   Unhappy — thanh toán lỗi: System (giữ đơn ở Chờ thanh toán) → Member (thử lại)
   Unhappy — người khác mua trước: System (báo sản phẩm đã được giữ, loại khỏi giỏ)
   L-04 Giao hàng và hoàn thành đơn
   Moderator (nhận việc đóng gói, quét SKU, tạo vận đơn GHN) → System (đồng bộ trạng thái giao hàng) → Member (nhận hàng, xác nhận) → System (hoàn thành đơn, cộng tiền vào ví người bán) → Member (đánh giá dịch vụ)
   Unhappy — hủy trước khi đóng gói: Member (hủy đơn) → System (hoàn 100% vào ví người mua, sản phẩm về lại sàn)
   Unhappy — GHN lỗi hoặc thất lạc: System (hủy đơn, hoàn tiền vào ví người mua) → Moderator (xử lý tồn kho)
   Unhappy — không bấm xác nhận: System (tự hoàn thành đơn sau thời hạn)
   L-05 Trả hàng và hoàn tiền
   Member (gửi yêu cầu trả hàng kèm lý do và ảnh) → Moderator (duyệt yêu cầu, tạo vận đơn trả) → Member (bàn giao cho GHN) → Moderator (kiểm tra hàng về kho) → System (hoàn tiền vào ví người mua)
   Unhappy — trả không hợp lệ: Moderator (kết luận không hợp lệ, không hoàn tiền) → Member (chọn gửi lại tự trả phí hoặc đến kho nhận)
   Unhappy — không đến nhận đúng hạn: System (hết hạn, kho xử lý món hàng theo quy định)
   Unhappy — nghi hàng giả: Moderator A (báo nghi ngờ) → Moderator B (kiểm định lại) → System (hoàn tiền cho người mua, hủy món hàng, không phạt người bán)
   Unhappy — không bàn giao cho GHN: System (hủy yêu cầu trả, đóng đơn)
   Unhappy — hàng trả thất lạc: System (ghi nhận thất lạc, hoàn tiền cho người mua)
   Unhappy — hàng về bị xuống cấp: Moderator (kết luận hàng xuống cấp, không đăng bán lại) → Member bán (chọn nhận tiền đền hoặc nhận lại hàng kèm đền một phần) → System (cộng tiền đền vào ví người bán)
   L-06 Rút lại hàng ký gửi
   Member (tạo yêu cầu rút hàng) → System (tính phí theo kỳ) → Member (trả phí từ ví hoặc MoMo) → Member (đến kho) → Moderator (bàn giao hàng) → System (đóng yêu cầu, gỡ sản phẩm khỏi sàn)
   Trong thời gian miễn phí: bỏ qua bước trả phí
   Unhappy — không đủ số dư ví: Member (thanh toán phần thiếu qua MoMo)
   Unhappy — sản phẩm đã có đơn: System (từ chối yêu cầu, phải chờ đơn kết thúc)
   Unhappy — không đến nhận đúng hạn: System (hủy yêu cầu, đăng bán lại, không hoàn phí)
   L-07 Rút tiền từ ví
   Member (xem số dư, tạo yêu cầu rút) → System (kiểm số dư khả dụng, khóa số tiền) → System (giả lập chi tiền, ghi giao dịch) → System (trừ số dư, thông báo kết quả)
   Unhappy — số dư không đủ: System (từ chối yêu cầu ngay)
   Unhappy — giao dịch thất bại: System (nhả số tiền đã khóa, ghi lý do, Member tạo lại)
   L-08 Thử đồ AI
   Member (ở trang sản phẩm, bấm Thử đồ AI) → System (khóa món đang xem vào slot Áo hoặc Quần) → Member (chọn người mẫu) → System (gợi ý món còn thiếu) hoặc Member (tự chọn món còn thiếu) → System (kiểm đủ áo + quần, sinh ảnh) → System (lưu vào bộ sưu tập) → Member (mua lẻ hoặc thêm cả outfit vào giỏ → sang L-03)
   Unhappy — chưa đủ áo và quần: System (chặn nút tạo ảnh, nhắc chọn thêm)
   Unhappy — không có món phù hợp để gợi ý: System (mở danh sách để Member tự chọn)
   Unhappy — sinh ảnh lỗi hoặc quá lâu: System (báo lỗi, cho chạy nền hoặc thử lại)
   Unhappy — món trong outfit đã bán: System (báo khi thêm vào giỏ, bỏ món đó)
   L-09 Chiến dịch và cộng đồng
   Organization (tạo chiến dịch, đăng bài viết) → System (hiển thị công khai, đẩy lên newsfeed người theo dõi) → Member (xem chiến dịch, xem bản đồ) → Member (tự mang đồ đến địa điểm nhận) → kết thúc, hệ thống không ghi nhận gì
   Unhappy — hết thời gian nhận: System (tự đóng chiến dịch)
   Unhappy — nội dung vi phạm: Member (báo cáo) → Moderator (gỡ bài hoặc ẩn chiến dịch)
   Unhappy — vượt giới hạn chiến dịch đang chạy: System (chặn tạo mới)
   L-10 Xác minh tổ chức và xử lý vi phạm
   Organization (nộp hồ sơ) → Moderator (nhận việc từ hàng đợi, đối chiếu theo checklist) → Moderator (xác thực) → System (mở chức năng, tự xóa ảnh giấy tờ sau thời hạn)
   Unhappy — giấy tờ thiếu hoặc sai: Moderator (từ chối kèm lý do) → Organization (bổ sung, gửi lại)
   Đổi thông tin pháp lý: Organization (gửi yêu cầu) → Moderator (duyệt hoặc từ chối) → System (áp dụng nếu được duyệt)
   Báo cáo vi phạm: Member (báo cáo) → Moderator (xem xét) → Moderator (gỡ nội dung) hoặc Moderator (bác bỏ báo cáo)
   Unhappy — không ai nhận việc: System (sau 48h tự nhả về hàng đợi, báo Admin)
   L-11 Quản trị hệ thống
   Admin (tạo kho, tạo Moderator, gán vào kho) → Admin (chỉnh hoa hồng, phí, thời hạn) → System (áp dụng cho giao dịch phát sinh sau đó, ghi audit log) → Admin (xem dashboard, xuất Excel)
   Unhappy — tài khoản vi phạm: Admin (khóa tài khoản) → System (chặn đăng nhập, giữ nguyên đơn đang chạy)
   Unhappy — ẩn kho đang còn tồn: System (chặn, yêu cầu xử lý tồn trước)
7. Luồng tiền
   Chỉ chiều tiền vào là thật. Mọi chiều tiền ra được giả lập bằng một bản ghi giao dịch, không chuyển khoản ngoài đời.
   Tiền vào (thật)
   Member (chuyển khoản qua MoMo) → MoMo (báo kết quả qua webhook) → System (xác nhận đơn, tiền nằm ở tài khoản nền tảng)
   Các khoản thu qua MoMo: tiền đơn hàng, phí vận chuyển, phí rút lại hàng, phí gửi lại hàng trả không hợp lệ. Đơn hàng trả được bằng ví nếu đủ số dư, hoặc bằng MoMo — Member chọn một trong hai, không chọn được cả hai cùng lúc cho một đơn. Phí rút hàng thì vẫn trừ ví trước, thiếu mới qua MoMo, vì đó là khoản nhỏ và phát sinh ngay tại quầy. Thanh toán bằng ví không tạo tiền mới, chỉ dùng lại tiền đã nằm sẵn trong quỹ nền tảng dưới tên Member.
   Tiền giữ
   Tiền đơn hàng nằm ở nền tảng từ lúc thanh toán đến lúc đơn hoàn thành. Trong giai đoạn này người bán chưa nhận được gì, nên nếu có trả hàng thì không phải thu hồi tiền từ người bán.
   Tiền ra (giả lập)
   Trường hợp	Đi từ → đến	Cách thực hiện
   Đơn hoàn thành	Nền tảng → ví người bán	System tính tiền sau hoa hồng và thuế, cộng vào ví, ghi một giao dịch
   Trả hàng hợp lệ hoặc hàng giả	Nền tảng → ví người mua	System hoàn toàn bộ tiền đơn, ghi giao dịch hoàn tiền
   Hủy đơn trước đóng gói, GHN lỗi	Nền tảng → ví người mua	Hoàn 100%, kể cả phí vận chuyển
   Member rút tiền	Ví Member → ngoài hệ thống	System khóa số dư, giả lập chi tiền, lưu kết quả thành công hoặc thất bại
   Phí rút lại hàng	Ví Member → nền tảng	Trừ ví trước; thiếu thì thanh toán phần còn lại qua MoMo
   Đền hàng xuống cấp	Quỹ nền tảng → ví người bán	Đền toàn phần nếu nền tảng giữ hàng, đền một phần nếu người bán lấy hàng về
   Ví Member
   Mỗi Member có đúng một ví, dùng cho cả vai mua và vai bán.
   Không liên kết tài khoản ngân hàng, hệ thống không lưu số tài khoản nào.
   Mọi biến động số dư đều là một giao dịch có loại, số tiền, thời điểm, đơn liên quan và kết quả.
   Số dư khả dụng = số dư − số tiền đang khóa cho yêu cầu rút đang xử lý.
   Tài khoản quỹ nền tảng
   Tiền MoMo về nằm ở một chỗ duy nhất, gọi là quỹ nền tảng. Đây không phải ví của ai và không rút được; nó để biết nền tảng đang nắm bao nhiêu tiền và bao nhiêu trong đó là của mình. Mỗi giao dịch ví của Member luôn có một bút toán đối ứng ở quỹ — nguyên tắc ghi sổ kép.
   Chỉ số	Nghĩa	Cách tính
   Tiền đang giữ	Tiền của người khác, nền tảng giữ hộ	Tổng số dư mọi ví Member + tiền đơn đang treo chưa hoàn thành
   Doanh thu nền tảng	Tiền thực sự của nền tảng	Tổng hoa hồng + tổng phí rút hàng − các khoản chi
   Số dư quỹ	Tiền đang nằm ở tài khoản nền tảng	Tiền đang giữ + doanh thu lũy kế − tiền đã chi ra
   Ba con số này hiển trên dashboard của Admin. Về lưu trữ, quỹ dùng chung bảng giao dịch với ví Member, phân biệt bằng loại ví (MEMBER hoặc PLATFORM) và loại giao dịch, không cần bảng riêng.
   Rút tiền không có bước người duyệt
   Tiền vào ví đã qua đủ cửa kiểm soát: đơn phải hoàn thành và hết hạn trả hàng mới được cộng tiền. Đến bước rút thì không còn gì để duyệt, nên Member bấm rút là hệ thống xử lý thẳng, không qua Moderator. Yêu cầu rút vẫn được lưu thành bản ghi có trạng thái PENDING → PROCESSING → SUCCESS hoặc FAILED, để Member theo dõi và để nhả lại tiền khi thất bại. Kiểm soát thay cho người duyệt: số tiền rút tối thiểu, giới hạn số lần rút mỗi ngày, và khóa số dư ngay khi tạo yêu cầu.
   Thuật ngữ tiền dùng trong tài liệu
   Thuật ngữ	Nghĩa	Trong hệ thống là gì
   Giao dịch (transaction)	Một lần tiền di chuyển	Một dòng trong bảng transaction: ngày, số tiền, loại, đơn liên quan, kết quả
   Bút toán	Một dòng ghi sổ cho một bên	Cũng là một dòng transaction, nhưng gắn vào một ví cụ thể
   Ghi sổ kép	Mỗi lần tiền chạy ghi hai dòng: bên nhận và bên đưa, bằng nhau và ngược dấu	Đơn 300k xong, hoa hồng 10%: ví người bán +270k, quỹ nền tảng −270k
   Ví Member	Nơi giữ tiền của một Member	Một bản ghi ví, mỗi Member đúng một cái
   Quỹ nền tảng	Tài khoản chứa tiền MoMo đổ về, không phải ví của ai	Cũng là một ví nhưng loại PLATFORM
   Tiền đang giữ	Tiền nằm ở nền tảng nhưng là của người khác	Tổng số dư ví Member + tiền đơn chưa hoàn thành
   Số dư khả dụng	Phần tiền Member rút được ngay	Số dư trừ phần đang khóa
   Khóa số dư	Tạm giữ một khoản lại trong lúc xử lý	Tạo yêu cầu rút 500k thì khóa 500k, tránh rút trùng hai lần
   Hoa hồng	Phần nền tảng giữ lại trên mỗi món bán được	Giá bán nhân tỷ lệ % Admin cấu hình
   Chi tiền (payout)	Nhả tiền ra khỏi hệ thống cho người dùng	Ở đồ án là giao dịch giả lập, chỉ lưu kết quả
   Hoàn tiền (refund)	Trả lại tiền cho người mua	Cộng vào ví người mua, không chuyển ra ngoài
   Webhook	Cổng thanh toán gọi ngược về hệ thống để báo kết quả	MoMo báo đã nhận tiền, hệ thống mới xác nhận đơn
   Đối soát	Cộng lại toàn bộ bút toán xem có khớp không	Nếu tổng hai chiều lệch nhau thì có lỗi dữ liệu
8. Nguồn thu của nền tảng
   Nền tảng thu trên mỗi món bán được, không thu phí đăng ký gửi.
   Nguồn thu	Khi nào thu	Cách tính
   Hoa hồng bán hàng	Đơn hoàn thành	Tỷ lệ % trên giá bán, tăng dần theo kỳ ký gửi, Admin cấu hình
   Phí rút lại hàng	Member rút hàng ngoài thời gian miễn phí	Số tiền cố định, Admin cấu hình
   Phí gửi lại hàng trả không hợp lệ	Người mua chọn gửi lại	Phí vận chuyển thực tế, người mua chịu
   Hoa hồng tăng dần theo kỳ để bù chi phí giữ hàng lâu và tạo động lực bán nhanh. Người bán không phải trả phí lưu kho đồng nào, không bán được thì không mất gì.
   Kỳ	Thời gian trên sàn	Hoa hồng
   Kỳ 1	Ngày 0 – 30	10%
   Kỳ 2	Ngày 31 – 60	15%
   Kỳ 3	Ngày 61 – 90	20%
   Tỷ lệ được chốt theo kỳ đang chạy lúc đơn được đặt, không phải lúc đơn hoàn thành — nếu không, một đơn đặt ngày 29 nhưng giao xong ngày 35 sẽ bị tính nhầm sang kỳ 2. Người bán hạ giá thì tỷ lệ giữ nguyên, số tiền hoa hồng tự giảm theo giá. Cả ba mức được ghi vào biên nhận lúc ký gửi nên người bán biết trước.
   Cửa sổ rút hàng miễn phí: mỗi khi một kỳ bắt đầu, Member có 7 ngày để rút hàng về mà không mất phí — tính cả ngày đầu kỳ 1 ngay sau khi nhập kho. Rút ngoài 7 ngày đó thì chịu phí rút hàng. Lý do có cửa sổ này: sang kỳ mới đồng nghĩa hoa hồng tăng, nên phải cho người bán quyền rút ra nếu họ không chấp nhận mức mới. Riêng hàng xuống cấp và hàng nhận lại ở cuối kỳ 90 ngày thì luôn miễn phí, không phụ thuộc cửa sổ này.
   Tính tiền trên một món đã bán
   Người mua trả: giá bán + phí vận chuyển
   Nền tảng giữ lại: hoa hồng = giá bán × tỷ lệ của kỳ đang chạy lúc đặt đơn
   Khấu trừ thuế theo cấu hình (VAT)
   Người bán nhận vào ví: giá bán − hoa hồng − thuế
   Phí vận chuyển trả cho GHN, nền tảng không ăn phần này
   Khi đơn không thành
   Trả hàng hợp lệ, hàng giả, hủy đơn, GHN làm mất: nền tảng không thu hoa hồng, hoàn toàn bộ cho người mua.
   Hàng giả lọt kiểm định: nền tảng chịu toàn bộ, hủy món hàng, không phạt người bán.
   Trả hàng không hợp lệ: đơn vẫn tính là bán thành công, nền tảng vẫn thu hoa hồng.
   Hoa hồng, phí rút hàng, thời gian miễn phí rút hàng và tỷ lệ thuế đều nằm trong cấu hình hệ thống và chỉ áp dụng cho giao dịch phát sinh sau khi đổi. Biên nhận ký gửi lưu lại mức phí tại thời điểm ký, nên đổi cấu hình không ảnh hưởng hợp đồng cũ.
   Món hàng vô thừa nhận cuối kỳ được chuyển cho tổ chức từ thiện đối tác, nền tảng không bán và không thu gì từ món đó. Nền tảng cũng không thu phí lưu kho theo ngày.
   Hoa hồng và phí rút hàng được ghi thẳng vào quỹ nền tảng dưới dạng bút toán doanh thu, cộng dồn thành con số doanh thu ở mục 7.
   Hàng xuống cấp: nền tảng chịu lỗ
   Món hàng chỉ quay về kho trong tình trạng xấu hơn khi lỗi thuộc về kho, vận chuyển hoặc kiểm định bỏ sót — người bán không có lỗi. Member chọn một trong hai:
   Nhận tiền đền: nền tảng trả đúng số tiền lẽ ra nhận được nếu đơn thành công, tức giá bán trừ hoa hồng. Món hàng trở thành tài sản của nền tảng để thanh lý hoặc hủy.
   Nhận lại hàng kèm đền một phần: nền tảng trả một tỷ lệ của số tiền trên (Admin cấu hình, mặc định 50%), Member đến kho lấy hàng, không mất phí rút hàng.
   Cả hai trường hợp nền tảng đều lỗ: không thu được hoa hồng mà còn chi tiền đền. Nếu nguyên nhân là GHN làm hỏng thì nền tảng đền trước cho người bán rồi tự đòi GHN — phần đòi này nằm ngoài hệ thống.
9. Xác minh tổ chức
   Hệ thống không tự xác minh được tư cách pháp lý của tổ chức. Các cơ quan nhà nước không mở API cho bên thứ ba để đối chiếu giấy phép tự động. Vì vậy xác minh là thao tác thủ công của Moderator, dựa trên các cổng tra cứu công khai; hệ thống chỉ lưu hồ sơ, kết luận và ai kết luận.
   Các loại tổ chức được chấp nhận và giấy tờ tương ứng
   Nền tảng chỉ nhận tổ chức có một pháp nhân đứng tên chịu trách nhiệm — với câu lạc bộ trực thuộc thì đó là tổ chức chủ quản. Không phải tổ chức nào cũng có mã số thuế, nhưng mọi tổ chức đều phải có giấy tờ thành lập do một cơ quan cấp.
   Loại tổ chức	Là gì	Giấy tờ cần có	Cơ quan cấp	Ví dụ có thật
   Quỹ xã hội, quỹ từ thiện	Tổ chức phi chính phủ do cá nhân hoặc tổ chức tự nguyện góp tài sản lập ra, hoạt động không vì lợi nhuận	Giấy phép thành lập và công nhận điều lệ quỹ	Bộ Nội vụ (phạm vi toàn quốc) hoặc Chủ tịch UBND theo phạm vi hoạt động	Quỹ Trò nghèo vùng cao (tiền thân là chương trình Cơm Có Thịt), Bộ Nội vụ cấp phép tại QĐ 142/QĐ-BNV ngày 25/02/2014 — Quỹ Hy Vọng (Hope Foundation), lập năm 2017, vận hành bởi báo VnExpress và FPT
   Hội, hiệp hội	Tổ chức tự nguyện của những người cùng lĩnh vực hoặc cùng mục đích, tự lo kinh phí, có pháp nhân và con dấu riêng	Quyết định cho phép thành lập hội kèm điều lệ được phê duyệt	Bộ Nội vụ hoặc UBND theo phạm vi hoạt động	Hội Chữ thập đỏ Việt Nam — Hội Bảo trợ người khuyết tật và trẻ mồ côi Việt Nam
   Câu lạc bộ có pháp nhân	Về luật chính là hội, chỉ khác tên gọi nên giấy tờ y hệt hội	Như hội	Như hội	Ít gặp; thường là câu lạc bộ được lập theo thủ tục hội cấp tỉnh hoặc cấp xã
   Câu lạc bộ trực thuộc một pháp nhân	Nhóm sinh hoạt bên trong một tổ chức khác, không có pháp nhân riêng nên phải mượn tư cách của chủ quản	Giấy tờ pháp nhân của tổ chức chủ quản + quyết định thành lập câu lạc bộ + văn bản cử người phụ trách	Cơ quan cấp giấy cho tổ chức chủ quản	Các câu lạc bộ tình nguyện thuộc Đoàn trường đại học, câu lạc bộ thiện nguyện thuộc nhà văn hóa phường
   Cơ sở trợ giúp xã hội ngoài công lập	Mái ấm, trung tâm bảo trợ nuôi dưỡng và chăm sóc người có hoàn cảnh khó khăn	Hai giấy: Giấy chứng nhận đăng ký thành lập để được tồn tại, và Giấy phép hoạt động để được nhận người	Sở hoặc Phòng phụ trách lĩnh vực bảo trợ xã hội tại địa phương; ghi rõ trên giấy phép	Làng trẻ em SOS Việt Nam — các mái ấm, trung tâm bảo trợ tư nhân tại địa phương
   Doanh nghiệp xã hội	Doanh nghiệp đăng ký bình thường nhưng cam kết dùng phần lớn lợi nhuận cho mục tiêu xã hội	Giấy chứng nhận đăng ký doanh nghiệp + Cam kết thực hiện mục tiêu xã hội đã gửi cơ quan đăng ký kinh doanh	Sở Tài chính, Phòng Đăng ký kinh doanh	KOTO (dạy nghề nhà hàng cho trẻ khó khăn), Tòhe (sản phẩm từ tranh của trẻ khuyết tật)
   Câu lạc bộ tự lập, nhóm thiện nguyện tự phát	Nhóm lập trên mạng hoặc giữa bạn bè, không trực thuộc ai	Không chấp nhận	—	Không có giấy tờ nào để đối chiếu, rủi ro lừa đảo cao
   Căn cứ pháp lý
   Quỹ xã hội, quỹ từ thiện: NĐ 03/2026/NĐ-CP, trước đó là NĐ 93/2019 và NĐ 136/2024
   Hội và câu lạc bộ có pháp nhân: NĐ 126/2024/NĐ-CP, hiệu lực 26/11/2024, thay NĐ 45/2010. Điều 6 xếp câu lạc bộ vào nhóm hội
   Cơ sở trợ giúp xã hội: NĐ 103/2017/NĐ-CP
   Doanh nghiệp xã hội: Luật Doanh nghiệp 2020
   Về câu lạc bộ: phần lớn câu lạc bộ thiện nguyện ở Việt Nam không có pháp nhân riêng mà trực thuộc một tổ chức khác, ví dụ câu lạc bộ sinh viên của một trường đại học. Trường hợp này tổ chức chủ quản phải đứng tên đăng ký tài khoản, câu lạc bộ chỉ là tên hiển thị của chiến dịch. Lý do: phải có một pháp nhân chịu trách nhiệm nếu xảy ra tranh chấp. Câu lạc bộ lập tự do trên mạng, không trực thuộc ai, thì không được mở tài khoản tổ chức.
   Người đại diện theo pháp luật nộp thêm ảnh CCCD để đối chiếu tên với tên ghi trên giấy phép.
   Cách Moderator kiểm chứng bên ngoài
   Bước	Làm gì	Công cụ
   1	Nếu tổ chức có mã số thuế: tra và đối chiếu tên, địa chỉ, tình trạng hoạt động	tracuunnt.gdt.gov.vn — miễn phí, không cần đăng nhập
   2	Với doanh nghiệp xã hội: đối chiếu mã số doanh nghiệp, người đại diện pháp luật	dangkykinhdoanh.gov.vn
   3	Kiểm ảnh giấy tờ: còn hiệu lực, rõ nét, có ngày cấp và dấu, tên khớp với thông tin khai; có số thì đối chiếu với số đã khai	Thủ công
   4	Đối chiếu tên người đại diện trên CCCD với tên trên giấy phép	Thủ công
   5	Gọi điện hoặc gửi email đến số và địa chỉ công khai của tổ chức để xác nhận đúng họ nộp hồ sơ	Thủ công
   6	Khi nghi ngờ: gửi văn bản hỏi cơ quan đã cấp giấy phép ghi trên hồ sơ	Sở Nội vụ hoặc sở chuyên ngành
   7	Tìm tên tổ chức trên báo chí và mạng xã hội xem có dấu hiệu lừa đảo	Thủ công
   8	Tích đủ checklist trên màn hình rồi bấm Xác thực hoặc Từ chối kèm lý do	Hệ thống
   Bước 1 và 2 là hai bước kiểm chứng được bằng dữ liệu nhà nước công khai, nên bắt buộc khi tổ chức có mã số thuế. Câu lạc bộ trường học và chi hội cấp xã thường không có mã số thuế; khi đó trọng số dồn sang bước 5 và bước 6, tức gọi điện xác nhận và hỏi cơ quan đã cấp giấy. Các bước còn lại dựa trên phán đoán và liên hệ trực tiếp.
   Các trang tra cứu
   Nơi tra	Link	Tra được gì
   Tra cứu người nộp thuế	tracuunnt.gdt.gov.vn	Nhập mã số thuế ra tên, địa chỉ, cơ quan thuế quản lý, tình trạng hoạt động. Miễn phí, không cần đăng nhập

Cổng thông tin quốc gia về đăng ký doanh nghiệp	dangkykinhdoanh.gov.vn	Mã số doanh nghiệp, loại hình, người đại diện pháp luật, tình trạng. Dùng cho doanh nghiệp xã hội
Cổng Dịch vụ công Quốc gia	dichvucong.gov.vn	Tra thủ tục và đầu mối cơ quan giải quyết khi cần gửi văn bản hỏi
Sở Nội vụ tỉnh hoặc thành phố nơi cấp phép	Website Sở Nội vụ địa phương, mục công bố hội và quỹ	Đối chiếu quyết định thành lập hội và giấy phép quỹ. Không có cổng tập trung toàn quốc nên phải vào đúng tỉnh
Cơ sở trợ giúp xã hội không có cổng tra cứu trực tuyến; phải hỏi sở đã cấp giấy phép ghi trên hồ sơ.
Về quyền kêu gọi quyên góp
Nghị định 93/2021/NĐ-CP quy định ai được vận động, tiếp nhận, phân phối đóng góp tự nguyện để khắc phục thiên tai, dịch bệnh, sự cố và hỗ trợ bệnh nhân hiểm nghèo: Mặt trận Tổ quốc, Hội Chữ thập đỏ, các quỹ xã hội và quỹ từ thiện, các tổ chức có tư cách pháp nhân, và cả cá nhân đủ năng lực hành vi dân sự. Chiến dịch nhận quần áo thường ngày không thuộc phạm vi nghị định này, nhưng nếu tổ chức mở chiến dịch cứu trợ bão lũ thì phải tuân theo. Nền tảng chỉ nhận tổ chức có pháp nhân nên luôn nằm trong nhóm được phép.
Hệ thống hỗ trợ được gì
Checklist bắt buộc tích đủ mới cho bấm Xác thực
Ghi lại ai xác minh, lúc nào, kết luận gì vào audit log
Chỉ Moderator xem được ảnh giấy tờ; Admin không xem
Tự xóa ảnh giấy tờ sau khi hồ sơ được duyệt một thời gian
Đổi thông tin pháp lý sau này phải qua duyệt lại, không tự sửa được
Giới hạn phải chấp nhận
Kết luận phụ thuộc phán đoán của Moderator. Hệ thống không phát hiện được giấy tờ giả làm kỹ, và cũng không kiểm được tổ chức có dùng đồ quyên góp đúng mục đích hay không. Phần giám sát sau khi duyệt dựa vào bài viết tự kể của tổ chức và báo cáo vi phạm từ người dùng.
Mẫu hồ sơ đăng ký tổ chức
Đây là toàn bộ thông tin một tổ chức phải điền và nộp ở UC-04. Dấu * là bắt buộc.
1. Thông tin tổ chức
   Trường	Bắt buộc	Dùng để làm gì
   Tên đầy đủ theo giấy tờ	*	Đối chiếu với ảnh giấy tờ đính kèm
   Tên hiển thị trên nền tảng	*	Tên người dùng nhìn thấy
   Loại hình tổ chức	*	Quyết định checklist giấy tờ ở mục 3
   Mã số thuế		Nếu có thì tra được trên cổng thuế và cổng đăng ký doanh nghiệp. Câu lạc bộ trường học, chi hội cấp xã thường không có
   Phạm vi hoạt động	*	Toàn quốc, tỉnh, huyện hay xã
   Địa chỉ trụ sở	*	Đối chiếu với địa chỉ trên giấy tờ
   Số điện thoại, email liên hệ	*	Moderator gọi kiểm chứng
   Website hoặc fanpage		Tra dấu hiệu bất thường
   Mô tả hoạt động	*	Hiển thị trên hồ sơ công khai
   Logo		Hiển thị
2. Người đại diện theo pháp luật
   Trường	Bắt buộc	Dùng để làm gì
   Họ và tên	*	Phải khớp tên trên giấy phép
   Chức vụ	*	Xác định thẩm quyền đại diện
   Số CCCD	*	Đối chiếu với ảnh CCCD
   Số điện thoại, email	*	Tài khoản đăng nhập và liên hệ
3. Tệp đính kèm
   Form đăng ký chỉ bắt buộc hai nhóm, áp dụng cho mọi loại tổ chức: ít nhất một tệp giấy tờ pháp lý của tổ chức, và CCCD người đại diện hai mặt — tức tối thiểu 3 tệp. Mỗi tệp tải lên khai kèm bốn thông tin: loại giấy tờ (bắt buộc), số giấy tờ nếu có, ngày cấp và cơ quan cấp. Nhiều quyết định nội bộ của trường học hay nhà văn hóa không có số rõ ràng nên không bắt buộc nhập số.
   Bảng dưới đây là checklist gợi ý, hiện theo loại hình đã chọn ở mục 1. Nó không khóa nút Gửi — tổ chức vẫn nộp được khi tên giấy tờ của họ không khớp nhãn, hoặc khi nhiều giấy nằm chung một file PDF. Bù lại, chính checklist này xuất hiện ở màn hình duyệt và Moderator phải tích đủ mới bấm Xác thực được — chặn ở khâu duyệt chứ không chặn ở khâu nộp.
   Loại tổ chức	Các giấy tờ cần có
   Quỹ xã hội, quỹ từ thiện	1. Giấy phép thành lập và công nhận điều lệ quỹ — 2. Điều lệ quỹ đã được công nhận — 3. CCCD người đại diện, hai mặt
   Hội, hiệp hội, câu lạc bộ có pháp nhân	1. Quyết định cho phép thành lập hội — 2. Điều lệ đã được phê duyệt — 3. CCCD người đại diện, hai mặt
   Câu lạc bộ trực thuộc một pháp nhân	1. Giấy tờ pháp nhân của tổ chức chủ quản — 2. Quyết định thành lập câu lạc bộ — 3. Văn bản cử người phụ trách — 4. CCCD người phụ trách, hai mặt
   Cơ sở trợ giúp xã hội ngoài công lập	1. Giấy chứng nhận đăng ký thành lập — 2. Giấy phép hoạt động — 3. CCCD người đại diện, hai mặt
   Doanh nghiệp xã hội	1. Giấy chứng nhận đăng ký doanh nghiệp — 2. Cam kết thực hiện mục tiêu xã hội đã gửi cơ quan đăng ký kinh doanh — 3. CCCD người đại diện, hai mặt
   Ràng buộc chung cho mọi tệp: định dạng JPG, PNG hoặc PDF; mỗi tệp tối đa 5 MB; tối đa 10 tệp một hồ sơ; ảnh phải rõ đến mức đọc được số giấy phép, ngày cấp và con dấu. Ảnh CCCD tự xóa sau khi hồ sơ được duyệt; các tệp giấy phép được giữ để đối chiếu về sau.
4. Địa điểm nhận đồ — khai ít nhất một điểm, có thể thêm nhiều
   Trường	Bắt buộc
   Tên điểm nhận	*
   Địa chỉ đầy đủ và tọa độ trên bản đồ	*
   Khung giờ nhận trong tuần	*
   Số điện thoại tại điểm nhận
   Ví dụ hồ sơ đã điền
   Trường	Giá trị
   Tên đầy đủ	Quỹ từ thiện Áo Ấm Vùng Cao
   Tên hiển thị	Quỹ Áo Ấm Vùng Cao
   Loại hình	Quỹ từ thiện
   Mã số thuế	0312xxxxxx
   Phạm vi hoạt động	Trong phạm vi TP.HCM
   Trụ sở	45 Nguyễn Đình Chiểu, Phường Đa Kao, TP.HCM
   Liên hệ	028 3xxx xxxx — lienhe@aoamvungcao.org
   Người đại diện	Lê Văn C, Chủ tịch Hội đồng quản lý quỹ
   CCCD	079xxxxxxxxx
   Điểm nhận đồ	Văn phòng quỹ, 45 Nguyễn Đình Chiểu — thứ Hai đến thứ Sáu, 8h–17h
   Tệp đính kèm	3 tệp — Giấy phép thành lập và công nhận điều lệ (số 145/QĐ-UBND, cấp 12/03/2024, UBND TP.HCM) — Điều lệ quỹ — CCCD hai mặt
10. Giới hạn của đồ án
    Những phần đồ án mô phỏng thay vì làm thật, và lý do.
    Hạng mục	Giới hạn	Lý do
    Kho hàng	Kho và tồn kho là dữ liệu giả lập, không có kho vật lý	Không có mặt bằng và nhân sự thật
    Kiểm định hàng	Thao tác kiểm định chỉ là biểu mẫu nhập kết quả	Kiểm định thật giả cần chuyên gia và thiết bị
    Chi tiền	Trả người bán, hoàn tiền, rút tiền đều là giao dịch giả lập	Cổng thanh toán không cấp API chi tiền cho tài khoản cá nhân
    Thanh toán	Chỉ dùng môi trường thử của MoMo	Tài khoản merchant thật cần pháp nhân
    Vận chuyển	Dùng sandbox GHN, không có đơn giao thật	Không có hàng thật để giao
    Kỳ ký gửi	Chu kỳ 30 / 60 / 90 ngày và 30 ngày vô thừa nhận là tham số, khi demo rút ngắn lại	Không đủ thời gian chạy thật bốn tháng trong 10 tuần đồ án
    Xác minh tổ chức	Moderator tự đối chiếu giấy tờ, không có API nhà nước	Xem chi tiết ở mục 9
    Thử đồ AI	Chỉ 2 slot Áo + Quần, ảnh mang tính minh họa, không đúng size thật	Mô hình tự host, giới hạn tài nguyên và thời gian xử lý
    Quyên góp	Hệ thống không lưu dữ liệu quyên góp, không đo được hiệu quả	Việc nhận đồ diễn ra ngoài đời, nền tảng không kiểm soát được
    Thuế và hóa đơn	Tính thuế theo tham số cấu hình, không xuất hóa đơn điện tử	Cần pháp nhân và nhà cung cấp hóa đơn
    Dữ liệu	Dữ liệu mẫu vài trăm sản phẩm, chưa kiểm tải cao	Phạm vi đồ án 10 tuần
    Khiếu nại	Không có luồng kháng nghị trong hệ thống, chỉ nhắn qua hộp thư kho hoặc email hỗ trợ	Không đủ thời gian xây quy trình khiếu nại chính thức trong 10 tuần
    Ảnh hưởng khi demo: luồng nghiệp vụ, trạng thái đơn, số dư ví và lịch sử giao dịch đều chạy đúng và kiểm chứng được. Phần không kiểm chứng được là tiền có thật sự về tài khoản người bán hay không, và hàng có thật sự được kiểm định đúng hay không.
    Riêng quỹ nền tảng: vì chi tiền là giả lập nên số dư quỹ trong hệ thống chỉ tăng, không giảm thật. Bút toán vẫn ghi đầy đủ hai chiều nên báo cáo vẫn cân; nếu chạy thật thì mỗi lần chi sẽ trừ quỹ tương ứng.
    Về giấy tờ tùy thân: chỉ người đại diện tổ chức mới phải cung cấp CCCD, và chỉ ở bước xác minh hồ sơ tổ chức. Ảnh giấy tờ được upload cho Moderator đối chiếu rồi tự xóa sau khi duyệt (mục 9). Phía ký gửi không thu thập CCCD dưới bất kỳ dạng nào
11. Tham số Admin cấu hình
    Mọi thay đổi đều vào audit log.
    Kỳ ký gửi
    Tham số	Mặc định	Áp dụng ở đâu
    Độ dài một kỳ	30 ngày	Tự gia hạn kỳ, tính ngày hết kỳ
    Số lần gia hạn tối đa	2 lần	Tổng 90 ngày trên sàn, hết thì chuyển Chờ xử lý cuối kỳ
    Cửa sổ rút hàng miễn phí	7 ngày đầu mỗi kỳ	Miễn phí rút hàng
    Hạn chọn cách xử lý cuối kỳ	30 ngày	Hết hạn thì thành hàng vô thừa nhận
    Hạn mang đồ đến kho sau khi duyệt form	7 ngày	Hết hạn thì form tự hủy
    Số form đang chờ tối đa mỗi Member	5 form	Chặn tạo form mới
    Giá và hoa hồng
    Tham số	Mặc định	Áp dụng ở đâu
    Hoa hồng kỳ 1	10%	Tính tiền người bán khi đơn hoàn thành
    Hoa hồng kỳ 2	15%	Như trên, đơn đặt trong kỳ 2
    Hoa hồng kỳ 3	20%	Như trên, đơn đặt trong kỳ 3
    Giá sàn	50% giá chốt	Chặn Member hạ giá quá thấp
    Số lần đổi giá mỗi ngày	1 lần	Chống spam đổi giá
    Phí rút lại hàng	Số tiền cố định	Thu khi rút ngoài cửa sổ miễn phí
    Tỷ lệ đền khi Member lấy hàng xuống cấp về	50%	Đền một phần, Member giữ món hàng
    Tỷ lệ thuế khấu trừ	Theo quy định	Trừ trước khi cộng vào ví người bán
    Ví và rút tiền
    Tham số	Mặc định	Áp dụng ở đâu
    Số tiền rút tối thiểu	10.000đ	Chặn yêu cầu rút quá nhỏ
    Số lần rút tối đa mỗi ngày	3 lần	Chống spam giao dịch
    Đơn hàng và trả hàng
    Tham số	Mặc định	Áp dụng ở đâu
    Thời hạn thanh toán đơn	15 phút	Hết hạn thì hủy đơn, nhả sản phẩm về sàn
    Thời hạn tự hoàn thành đơn sau khi giao	3 ngày	Member không bấm xác nhận thì hệ thống tự chốt
    Thời hạn gửi yêu cầu trả hàng	3 ngày sau khi nhận	Quá hạn không trả được
    Thời hạn bàn giao hàng trả cho GHN	3 ngày	Không bàn giao thì hủy yêu cầu trả
    Thời hạn đến kho nhận hàng	7 ngày	Áp cho hàng rút về và hàng trả không hợp lệ
    Vận hành kho, tổ chức và cộng đồng
    Tham số	Mặc định	Áp dụng ở đâu
    Thời gian tự nhả việc đã nhận	48 giờ	Trả việc về hàng đợi kho
    Số chiến dịch chạy đồng thời mỗi tổ chức	3 chiến dịch	Chặn tạo chiến dịch mới
    Số ngày giữ ảnh giấy tờ sau khi duyệt	30 ngày	Hết hạn thì tự xóa ảnh
12. Mẫu biên nhận ký gửi (Đây là mẫu tham khảo)
    Mẫu dưới đây là nội dung hệ thống sinh ra file PDF (F-022). Số liệu trong ngoặc là ví dụ minh họa. Mọi mức phí và thời hạn được chụp lại tại thời điểm ký, đổi cấu hình sau đó không ảnh hưởng biên nhận này.
    CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM Độc lập – Tự do – Hạnh phúc
    BIÊN NHẬN KÝ GỬI HÀNG HÓA
    Số: KG-2026-000123
    Hôm nay, ngày 10 tháng 10 năm 2026, tại Kho Quận 7 – ReHome, chúng tôi gồm:
    BÊN A – BÊN NHẬN KÝ GỬI

Tên nền tảng	ReHome
Chi nhánh kho	Kho Quận 7 – số 12 Nguyễn Thị Thập, Phường Tân Phú, Quận 7, TP.HCM
Người tiếp nhận	Nguyễn Văn A – Moderator, mã MOD-07-003
Điện thoại	1900 xxxx
BÊN B – BÊN KÝ GỬI

Họ và tên	Trần Thị B
Mã Member	MB-0098231
Điện thoại	09xx xxx xxx
Email	tranthib@example.com
Hai bên thống nhất xác lập biên nhận ký gửi với các nội dung sau:
ĐIỀU 1. THÔNG TIN HÀNG HÓA KÝ GỬI
Nội dung	Chi tiết
Tên sản phẩm	Áo khoác dạ nữ
Thương hiệu	Uniqlo
Danh mục	Áo khoác
Kích cỡ	M
Mã SKU	Q7-AK-000845
Tình trạng theo kiểm định	Như mới, không lỗi, đường chỉ nguyên vẹn
Kết luận kiểm định	Hàng chính hãng, đủ điều kiện đăng bán
Số lượng	01 (một) sản phẩm
ĐIỀU 2. GIÁ BÁN VÀ HOA HỒNG
2.1. Giá bán niêm yết hai bên thống nhất: 1.000.000 đ (một triệu đồng).
2.2. Giá sàn: 500.000 đ. Bên B được chủ động giảm giá bán trong ứng dụng bất kỳ lúc nào nhưng không được thấp hơn giá sàn, không được tăng giá, và tối đa 01 lần mỗi ngày.
2.3. Hoa hồng Bên A được hưởng, tính trên giá bán tại thời điểm đơn hàng được đặt:
Kỳ	Thời gian	Tỷ lệ hoa hồng
Kỳ 1	Ngày 0 – 30	10%
Kỳ 2	Ngày 31 – 60	15%
Kỳ 3	Ngày 61 – 90	20%
2.4. Thuế khấu trừ theo quy định pháp luật hiện hành.
ĐIỀU 3. THỜI HẠN KÝ GỬI
3.1. Một kỳ ký gửi là 30 ngày, bắt đầu từ ngày 10/10/2026.
3.2. Hết mỗi kỳ, nếu hàng chưa bán được, biên nhận tự gia hạn sang kỳ tiếp theo, tối đa 02 lần, tổng thời gian trên sàn không quá 90 ngày, tức đến hết ngày 08/01/2027.
3.3. Mỗi lần sang kỳ mới, Bên B có 07 ngày để rút hàng về mà không chịu phí.
ĐIỀU 4. QUYỀN VÀ NGHĨA VỤ CỦA BÊN A
4.1. Bảo quản hàng hóa tại kho trong suốt thời hạn ký gửi.
4.2. Chụp ảnh, đăng bán, đóng gói và giao hàng cho người mua.
4.3. Thu hộ tiền bán hàng và chuyển vào ví của Bên B sau khi trừ hoa hồng và thuế.
4.4. Không tự ý thay đổi giá bán đã thống nhất tại Điều 2.
4.5. Chịu trách nhiệm với hư hỏng, mất mát phát sinh trong quá trình lưu kho và vận chuyển.
ĐIỀU 5. QUYỀN VÀ NGHĨA VỤ CỦA BÊN B
5.1. Cam kết hàng hóa thuộc sở hữu hợp pháp của mình, không phải hàng gian, hàng cấm.
5.2. Chịu trách nhiệm về nguồn gốc hàng hóa trước pháp luật.
5.3. Được theo dõi tình trạng hàng hóa, giảm giá bán và rút hàng theo Điều 7.
5.4. Được nhận tiền bán hàng vào ví và rút tiền theo quy định của nền tảng.
ĐIỀU 6. THANH TOÁN
6.1. Khi đơn hàng hoàn thành và hết thời hạn trả hàng, Bên A cộng số tiền Bên B được hưởng vào ví của Bên B trên ứng dụng.
6.2. Số tiền Bên B được hưởng = giá bán thực tế − hoa hồng − thuế.
6.3. Ví dụ: bán ở giá 1.000.000 đ trong kỳ 1, hoa hồng 10% là 100.000 đ, Bên B nhận 900.000 đ (chưa trừ thuế).
6.4. Bên B rút tiền từ ví theo yêu cầu, không cần Bên A phê duyệt.
ĐIỀU 7. RÚT LẠI HÀNG HÓA
7.1. Bên B được rút hàng bất kỳ lúc nào khi hàng chưa có đơn đặt.
7.2. Rút trong 07 ngày đầu mỗi kỳ: miễn phí. Rút ngoài thời gian này: chịu phí rút hàng là 50.000 đ.
7.3. Bên B đến kho nhận hàng trong 07 ngày kể từ khi yêu cầu được chấp nhận. Quá hạn, yêu cầu bị hủy, hàng được đăng bán lại và không hoàn phí.
ĐIỀU 8. XỬ LÝ CUỐI KỲ VÀ HÀNG VÔ THỪA NHẬN
8.1. Hết 90 ngày, hàng được gỡ khỏi sàn. Bên B chọn một trong hai: nhận lại hàng miễn phí, hoặc ký biên nhận mới với giá thương lượng lại.
8.2. Bên A thông báo qua ứng dụng và email. Bên B có 30 ngày kể từ ngày thông báo để phản hồi.
8.3. Quá 30 ngày mà Bên B không phản hồi, hàng hóa được xem là vô thừa nhận. Bên B được coi là từ bỏ quyền sở hữu, Bên A chuyển hàng cho tổ chức từ thiện đối tác và Bên B không được bồi hoàn.
ĐIỀU 9. RỦI RO VÀ BỒI THƯỜNG
9.1. Hàng hư hỏng, xuống cấp do lưu kho hoặc vận chuyển: Bên A bồi thường. Bên B chọn nhận đủ số tiền lẽ ra được hưởng và giao hàng cho Bên A, hoặc nhận lại hàng kèm bồi thường 50% số tiền đó.
9.2. Hàng bị xác định là hàng giả sau khi đã bán: Bên A hoàn tiền cho người mua và tiêu hủy hàng hóa. Bên B không bị phạt nếu đã trung thực khai báo.
9.3. Hàng bị người mua trả và được xác nhận hợp lệ: hàng được đăng bán lại, kỳ ký gửi tiếp tục, Bên B không chịu chi phí.
ĐIỀU 10. HIỆU LỰC
10.1. Biên nhận có hiệu lực kể từ thời điểm Bên B xác nhận trên ứng dụng.
10.2. Các mức phí, tỷ lệ và thời hạn ghi tại biên nhận này được giữ nguyên đến khi kết thúc, không thay đổi theo cấu hình mới của nền tảng.
XÁC NHẬN ĐIỆN TỬ
BÊN A	BÊN B
Nguyễn Văn A – MOD-07-003	Trần Thị B – MB-0098231
Lập lúc 10/10/2026 14:32	Xác nhận trên ứng dụng lúc 10/10/2026 14:35
Biên nhận này không có chữ ký tay. Giá trị xác nhận dựa trên thao tác bấm xác nhận của Bên B trên tài khoản đã đăng nhập, cùng thời điểm được hệ thống ghi nhận và lưu trong audit log.
13. Vi phạm và hình thức xử lý
    Nguyên tắc phân quyền: Moderator xử lý nội dung và hàng hóa — gỡ bài, ẩn chiến dịch, từ chối hồ sơ, loại hàng. Admin xử lý tài khoản — chỉ Admin mới khóa và mở khóa được. Vì vậy Admin là người quyết định cuối cùng trong mọi trường hợp đụng đến quyền truy cập của một tài khoản.
    Thang xử lý tăng dần
    Mức	Hình thức	Ai thực hiện
    1	Gỡ bài viết, xóa bình luận, ẩn chiến dịch	Moderator
    2	Từ chối hồ sơ, từ chối nhận hàng, loại hàng khỏi sàn	Moderator
    3	Khóa tài khoản	Admin
    4	Mở khóa tài khoản sau khi xác minh lại	Admin
    Hệ thống không có bước cảnh cáo chính thức. Moderator xử lý nội dung ngay, còn việc có khóa tài khoản hay không do Admin cân nhắc dựa trên lịch sử vi phạm trong audit log.
    Các loại vi phạm
    Vi phạm	Ai phát hiện	Xử lý ngay	Quyết định cuối
    Bài viết của tổ chức sai sự thật, kêu gọi lừa đảo	Member báo cáo	Moderator gỡ bài	Admin khóa tài khoản tổ chức nếu tái phạm
    Chiến dịch ma, địa điểm nhận không có thật	Member báo cáo	Moderator ẩn chiến dịch	Admin
    Hồ sơ tổ chức có giấy tờ giả hoặc sai lệch	Moderator lúc xác minh	Từ chối hồ sơ kèm lý do	Moderator; nếu đã duyệt rồi mới phát hiện thì Admin khóa
    Bình luận công kích, spam, quảng cáo	Member báo cáo	Moderator xóa bình luận	Admin nếu lặp lại nhiều lần
    Ký gửi hàng giả, phát hiện lúc kiểm định	Moderator tại quầy	Từ chối, trả đồ tại chỗ, không phạt	Không ai — kết thúc tại đó
    Ký gửi hàng giả, lọt kiểm định và bị phát hiện sau khi bán	Moderator lúc kiểm hàng trả	Hoàn tiền người mua, tiêu hủy hàng, nền tảng chịu	Admin, chỉ khi người bán tái phạm có hệ thống
    Ký gửi hàng gian, hàng cấm	Moderator tại quầy	Từ chối nhận, lập ghi chú trong hồ sơ	Admin khóa tài khoản
    Lạm dụng trả hàng — nhiều yêu cầu bị kết luận không hợp lệ	System đếm, Moderator thấy khi xử lý	Không hoàn tiền từng lần	Admin khóa nếu thành hệ thống
    Trả về món khác với món đã mua	Moderator lúc kiểm hàng trả	Kết luận không hợp lệ, không hoàn tiền	Admin khóa tài khoản
    Moderator thao tác sai hoặc duyệt ẩu	Admin qua audit log và báo cáo	Không có	Admin thu hồi hoặc khóa tài khoản Moderator
    Tạo nhiều form ký gửi rồi không mang đồ đến	System	Form tự hết hạn, bị giới hạn 5 form chờ	Không ai — tự động
    Không đến kho nhận hàng đúng hạn	System	Hủy yêu cầu, hàng đăng bán lại hoặc thành vô thừa nhận	Không ai — tự động
    Khiếu nại
