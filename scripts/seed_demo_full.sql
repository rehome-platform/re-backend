-- =============================================================================
-- ReHome - SEED DỮ LIỆU DEMO ĐẦY ĐỦ (dùng cho buổi bảo vệ đồ án)
-- =============================================================================
-- Chạy file này là có NGUYÊN một hệ thống đang hoạt động: tài khoản mọi vai trò,
-- 2 địa điểm ký gửi (TP.HCM + Hà Nội), danh mục/thương hiệu, gói thành viên VIP,
-- voucher, 53 sản phẩm đang bán, phiếu ký gửi ở mọi trạng thái, đơn hàng ở mọi
-- trạng thái, ví - sổ quỹ - hoa hồng - hoàn tiền, trả hàng, rút hàng, rút tiền,
-- chiến dịch từ thiện, bài viết, hàng đợi Moderator, thử đồ AI, thông báo, chat.
--
-- ------------------------------------------------------------------ CÁCH CHẠY
--   1. Để app chạy ít nhất 1 lần trên database này (ddl-auto: update tạo schema).
--   2. Chạy TOÀN BỘ file này bằng pgAdmin / DBeaver / psql:
--        psql -h localhost -p 5432 -U postgres -d jwtdemo -f scripts/seed_demo_full.sql
--   3. Khởi động lại app. Đăng nhập bằng danh sách tài khoản ở CUỐI FILE.
--
-- Chạy lại bao nhiêu lần cũng được: bước 0 xoá sạch dữ liệu cũ rồi seed lại từ đầu,
-- nên luôn ra đúng một bộ dữ liệu giống nhau.
--
--   /!\  BƯỚC 0 TRUNCATE TOÀN BỘ 65 BẢNG. Đừng chạy trên database có dữ liệu thật.
--
-- -------------------------------------------------- QUAN HỆ VỚI CÁC INITIALIZER
-- Các initializer tự seed trong code (UserInitializer, WarehouseInitializer,
-- CategoryInitializer, BrandInitializer, MembershipPlanInitializer,
-- VoucherTemplateInitializer, SystemConfigInitializer, ProductInitializer) đã được
-- TẮT mặc định bằng cờ app.initializer.seed-enabled (application.yml). File này là
-- nguồn dữ liệu demo duy nhất. Hai nhóm initializer vẫn chạy và KHÔNG xung đột:
--   - SequenceInitializer: CREATE SEQUENCE IF NOT EXISTS (file này cũng tự tạo).
--   - 3 initializer chuẩn hóa tên: chỉ điền cột *_normalized đang NULL (file này
--     đã điền sẵn hết ở bước 14 nên chúng không có việc gì làm).
-- Muốn quay lại cách seed bằng code: đặt APP_INITIALIZER_SEED=true.
--
-- ---------------------------------------------------------------- MẬT KHẨU CHUNG
-- TẤT CẢ tài khoản: 123456
-- (hash bcrypt bên dưới đã verify bằng BCryptPasswordEncoder.matches("123456", ...))
--
-- -------------------------------------------------------------------- ẢNH SẢN PHẨM
-- Ảnh sản phẩm trỏ tới CDN công khai (dummyjson / fakestoreapi / unsplash /
-- wikimedia) nên không cần copy file gì, mở FE là thấy ảnh ngay. Mọi URL đã được
-- kiểm tra trả HTTP 200.
--
-- RIÊNG tính năng THỬ ĐỒ AI (POST /try-on) đọc ảnh qua FileStorageService.loadByUrl,
-- và impl này CỐ TÌNH TỪ CHỐI mọi URL không do chính storage sinh ra (chống SSRF) -
-- nên không thử đồ được với ảnh CDN. Vì vậy 2 sản phẩm HCM-ASM-000069 (áo) và
-- HCM-QT-000070 (quần) dùng ảnh NỘI BỘ để demo thử đồ:
--      uploads/products/seed-tryon-top.png
--      uploads/products/seed-tryon-bottom.png
-- Thiếu 2 file đó thì copy từ resources (chạy ở thư mục gốc project):
--      mkdir -p uploads/products
--      cp src/main/resources/seed/tryon/top.png    uploads/products/seed-tryon-top.png
--      cp src/main/resources/seed/tryon/bottom.png uploads/products/seed-tryon-bottom.png
-- (chỉ đúng với app.storage.provider=local + public-base-url mặc định
--  http://localhost:8080/files - đổi URL ở bước 7 nếu bạn chạy cấu hình khác)
--
-- ------------------------------------------------------------------- ĐỊA CHỈ GHN
-- Hệ thống dùng danh mục địa chỉ GHN 2 CẤP (sau sáp nhập 01/07/2025): tỉnh/thành ->
-- phường/xã, KHÔNG còn quận/huyện. Mọi địa chỉ trong file này khai theo hệ mới
-- (is_new_address = true, district_name = NULL) và chỉ dùng tên phường/xã + tỉnh/thành
-- có trong danh mục GHN, vì đó là giá trị duy nhất tạo được vận đơn.
--
-- province_id của "Hồ Chí Minh" = 1000001 (docs/ghnapi.md, lấy từ một lần gọi thật
-- trên staging). province_id của Hà Nội và ward_code của mọi phường/xã để NULL: hai
-- mã đó chỉ lấy được khi gọi GHN bằng token thật, cả hai cột đều nullable và cũng
-- không tham gia tạo vận đơn (GHN hệ mới đi theo TÊN). Muốn điền cho đủ:
--      GET /address-units/provinces          -> _id của "Hà Nội"
--      GET /address-units/wards?provinceId=  -> _id của từng phường/xã
-- rồi chạy phần "TUỲ CHỌN - điền mã GHN" ở cuối file.
-- =============================================================================


-- =============================================================================
-- 0. XOÁ SẠCH DỮ LIỆU CŨ
-- =============================================================================
TRUNCATE TABLE
    accounts, audit_logs, brands, campaign_warehouses, campaigns, cart_items, carts,
    categories, community_post_photos, community_posts, consignment_form_photos,
    consignment_forms, consignment_receipt_pdfs, consignment_receipts, conversations,
    counter_identity_confirmations, degraded_item_case_photos, degraded_item_cases,
    device_tokens, donation_notes, donations, email_otps, end_of_term_decisions, follows,
    item_withdrawal_requests, live_tryon_sessions, member_profiles, member_subscriptions,
    member_vouchers, membership_payments, membership_plans, message_image_urls, messages,
    moderator_profiles, notifications, order_items, order_rating_image_urls,
    order_tracking_events, orders, organization_documents,
    organization_legal_change_requests, organization_profiles, payments, payout_requests,
    platform_funds, post_comments, post_likes, price_change_logs, product_photos,
    product_price_changes, products, queue_items, refresh_tokens, return_request_photos,
    return_requests, shipping_addresss, system_configs, try_on_images, violation_reports,
    violation_words, voucher_template_tier_rules, voucher_templates, wallet_transactions,
    wallets, warehouses
RESTART IDENTITY CASCADE;


-- =============================================================================
-- 1. SEQUENCE SINH MÃ
-- =============================================================================
-- Hibernate KHÔNG tạo 5 sequence này (code gọi nextval() bằng SQL thô), bình thường
-- SequenceInitializer tạo lúc khởi động. Tạo luôn ở đây để chạy seed trên database
-- mới dựng cũng không lỗi. setval về đúng số tiếp theo nằm ở BƯỚC 14.
CREATE SEQUENCE IF NOT EXISTS member_code_seq START 1;          -- MB000001
CREATE SEQUENCE IF NOT EXISTS moderator_code_seq START 1;       -- MOD000001
CREATE SEQUENCE IF NOT EXISTS product_sku_seq START 1;          -- <ma kho>-<ma danh muc>-000001
CREATE SEQUENCE IF NOT EXISTS receipt_no_seq START 1;           -- KG-2026-000001
CREATE SEQUENCE IF NOT EXISTS consignment_form_no_seq START 1;  -- KG-000001


-- =============================================================================
-- 2. HAI ĐỊA ĐIỂM KÝ GỬI (KHO)
-- =============================================================================
-- code = tiền tố SKU sản phẩm của kho đó (ProductServiceImpl.generateSku).
-- is_new_address = true -> khối địa chỉ khai theo hệ 2 cấp, district_name phải NULL.
INSERT INTO warehouses (id, name, code, address, phone, latitude, longitude, opening_hours,
                        status, ward_name, ward_code, district_name, province_name, province_id,
                        is_new_address, created_at) VALUES
('1b000000-0000-4000-8000-000000000001', 'Kho ReHome TP.HCM', 'HCM',
 '123 Nguyễn Thị Thập, Phường Tân Mỹ', '02838225566', 10.7329000, 106.7240000,
 '08:00 - 20:00 (Thứ 2 - Chủ nhật)', 'ACTIVE',
 'Phường Tân Mỹ', NULL, NULL, 'Hồ Chí Minh', 1000001, true, now() - interval '400 days'),
('1b000000-0000-4000-8000-000000000002', 'Kho ReHome Hà Nội', 'HN',
 '45 Xuân Thủy, Phường Cầu Giấy', '02437912345', 21.0368000, 105.7828000,
 '08:00 - 20:00 (Thứ 2 - Chủ nhật)', 'ACTIVE',
 'Phường Cầu Giấy', NULL, NULL, 'Hà Nội', NULL, true, now() - interval '400 days');


-- =============================================================================
-- 3. TÀI KHOẢN (mật khẩu chung: 123456)
-- =============================================================================
-- 1 ADMIN + 3 MODERATOR (2 kho HCM, 1 kho Hà Nội) + 10 MEMBER + 3 ORGANIZATION.
-- accounts.warehouse_id chỉ có nghĩa với MODERATOR (kho phụ trách).
INSERT INTO accounts (id, email, password_hash, phone, role, status, email_verified,
                      ban_reason, warehouse_id, created_at, updated_at) VALUES
('2a000000-0000-4000-8000-000000000001', 'admin@rehome.com',
 '$2a$10$9oLul/HMTIiG0xkAyG6zQuL9VlDY1gj6f8.Faz1CdLL/4PdfX2dXS', '0900000001',
 'ADMIN', 'ACTIVE', true, NULL, NULL, now() - interval '400 days', now() - interval '400 days'),
('2a000000-0000-4000-8000-000000000002', 'moderator1@rehome.com',
 '$2a$10$9oLul/HMTIiG0xkAyG6zQuL9VlDY1gj6f8.Faz1CdLL/4PdfX2dXS', '0900000002',
 'MODERATOR', 'ACTIVE', true, NULL, '1b000000-0000-4000-8000-000000000001',
 now() - interval '399 days', now() - interval '399 days'),
('2a000000-0000-4000-8000-000000000003', 'moderator2@rehome.com',
 '$2a$10$9oLul/HMTIiG0xkAyG6zQuL9VlDY1gj6f8.Faz1CdLL/4PdfX2dXS', '0900000003',
 'MODERATOR', 'ACTIVE', true, NULL, '1b000000-0000-4000-8000-000000000001',
 now() - interval '399 days', now() - interval '399 days'),
('2a000000-0000-4000-8000-000000000004', 'moderator3@rehome.com',
 '$2a$10$9oLul/HMTIiG0xkAyG6zQuL9VlDY1gj6f8.Faz1CdLL/4PdfX2dXS', '0900000004',
 'MODERATOR', 'ACTIVE', true, NULL, '1b000000-0000-4000-8000-000000000002',
 now() - interval '399 days', now() - interval '399 days'),
('2a000000-0000-4000-8000-000000000011', 'member1@rehome.com',
 '$2a$10$9oLul/HMTIiG0xkAyG6zQuL9VlDY1gj6f8.Faz1CdLL/4PdfX2dXS', '0900000011',
 'MEMBER', 'ACTIVE', true, NULL, NULL, now() - interval '120 days', now() - interval '1 day'),
('2a000000-0000-4000-8000-000000000012', 'member2@rehome.com',
 '$2a$10$9oLul/HMTIiG0xkAyG6zQuL9VlDY1gj6f8.Faz1CdLL/4PdfX2dXS', '0900000012',
 'MEMBER', 'ACTIVE', true, NULL, NULL, now() - interval '110 days', now() - interval '3 days'),
('2a000000-0000-4000-8000-000000000013', 'member3@rehome.com',
 '$2a$10$9oLul/HMTIiG0xkAyG6zQuL9VlDY1gj6f8.Faz1CdLL/4PdfX2dXS', '0900000013',
 'MEMBER', 'ACTIVE', true, NULL, NULL, now() - interval '100 days', now() - interval '2 days'),
('2a000000-0000-4000-8000-000000000014', 'member4@rehome.com',
 '$2a$10$9oLul/HMTIiG0xkAyG6zQuL9VlDY1gj6f8.Faz1CdLL/4PdfX2dXS', '0900000014',
 'MEMBER', 'ACTIVE', true, NULL, NULL, now() - interval '95 days', now() - interval '2 days'),
('2a000000-0000-4000-8000-000000000015', 'member5@rehome.com',
 '$2a$10$9oLul/HMTIiG0xkAyG6zQuL9VlDY1gj6f8.Faz1CdLL/4PdfX2dXS', '0900000015',
 'MEMBER', 'ACTIVE', true, NULL, NULL, now() - interval '90 days', now() - interval '1 day'),
('2a000000-0000-4000-8000-000000000016', 'member6@rehome.com',
 '$2a$10$9oLul/HMTIiG0xkAyG6zQuL9VlDY1gj6f8.Faz1CdLL/4PdfX2dXS', '0900000016',
 'MEMBER', 'ACTIVE', true, NULL, NULL, now() - interval '85 days', now() - interval '1 day'),
('2a000000-0000-4000-8000-000000000017', 'member7@rehome.com',
 '$2a$10$9oLul/HMTIiG0xkAyG6zQuL9VlDY1gj6f8.Faz1CdLL/4PdfX2dXS', '0900000017',
 'MEMBER', 'ACTIVE', true, NULL, NULL, now() - interval '80 days', now() - interval '2 days'),
('2a000000-0000-4000-8000-000000000018', 'member8@rehome.com',
 '$2a$10$9oLul/HMTIiG0xkAyG6zQuL9VlDY1gj6f8.Faz1CdLL/4PdfX2dXS', '0900000018',
 'MEMBER', 'ACTIVE', true, NULL, NULL, now() - interval '55 days', now() - interval '2 hours'),
('2a000000-0000-4000-8000-000000000019', 'member9@rehome.com',
 '$2a$10$9oLul/HMTIiG0xkAyG6zQuL9VlDY1gj6f8.Faz1CdLL/4PdfX2dXS', '0900000019',
 'MEMBER', 'ACTIVE', true, NULL, NULL, now() - interval '75 days', now() - interval '2 days'),
-- MEMBER bị khoá: test màn Admin quản lý tài khoản + chặn đăng nhập (AccountBannedException -> 403)
('2a000000-0000-4000-8000-000000000020', 'member10@rehome.com',
 '$2a$10$9oLul/HMTIiG0xkAyG6zQuL9VlDY1gj6f8.Faz1CdLL/4PdfX2dXS', '0900000020',
 'MEMBER', 'BANNED', true,
 'Nhiều lần đăng bán hàng giả mạo thương hiệu, đã bị cảnh báo 2 lần.', NULL,
 now() - interval '70 days', now() - interval '10 days'),
('2a000000-0000-4000-8000-000000000031', 'org1@rehome.com',
 '$2a$10$9oLul/HMTIiG0xkAyG6zQuL9VlDY1gj6f8.Faz1CdLL/4PdfX2dXS', '0900000031',
 'ORGANIZATION', 'ACTIVE', true, NULL, NULL, now() - interval '60 days', now() - interval '50 days'),
('2a000000-0000-4000-8000-000000000032', 'org2@rehome.com',
 '$2a$10$9oLul/HMTIiG0xkAyG6zQuL9VlDY1gj6f8.Faz1CdLL/4PdfX2dXS', '0900000032',
 'ORGANIZATION', 'ACTIVE', true, NULL, NULL, now() - interval '6 days', now() - interval '6 days'),
('2a000000-0000-4000-8000-000000000033', 'org3@rehome.com',
 '$2a$10$9oLul/HMTIiG0xkAyG6zQuL9VlDY1gj6f8.Faz1CdLL/4PdfX2dXS', '0900000033',
 'ORGANIZATION', 'ACTIVE', true, NULL, NULL, now() - interval '30 days', now() - interval '25 days');

-- Hồ sơ Moderator (moderator_code do ModeratorCodeGenerator sinh dạng MOD000001)
INSERT INTO moderator_profiles (account_id, full_name, moderator_code, warehouse_id) VALUES
('2a000000-0000-4000-8000-000000000002', 'Phạm Thị Thu Hà',  'MOD000001', '1b000000-0000-4000-8000-000000000001'),
('2a000000-0000-4000-8000-000000000003', 'Ngô Văn Khánh',    'MOD000002', '1b000000-0000-4000-8000-000000000001'),
('2a000000-0000-4000-8000-000000000004', 'Bùi Thị Mai Liên', 'MOD000003', '1b000000-0000-4000-8000-000000000002');

-- Hồ sơ Member (member_code do MemberCodeGenerator sinh dạng MB000001, dùng cho QR tra cứu tại quầy)
INSERT INTO member_profiles (account_id, full_name, member_code, avatar_url) VALUES
('2a000000-0000-4000-8000-000000000011', 'Nguyễn Văn An',   'MB000001', NULL),
('2a000000-0000-4000-8000-000000000012', 'Trần Thị Bình',   'MB000002', NULL),
('2a000000-0000-4000-8000-000000000013', 'Lê Hoàng Cường',  'MB000003', NULL),
('2a000000-0000-4000-8000-000000000014', 'Phạm Thu Dung',   'MB000004', NULL),
('2a000000-0000-4000-8000-000000000015', 'Tạ Thị Ngọc Anh', 'MB000005', NULL),
('2a000000-0000-4000-8000-000000000016', 'Võ Minh Đức',     'MB000006', NULL),
('2a000000-0000-4000-8000-000000000017', 'Đỗ Thị Hạnh',     'MB000007', NULL),
('2a000000-0000-4000-8000-000000000018', 'Bùi Quang Huy',   'MB000008', NULL),
('2a000000-0000-4000-8000-000000000019', 'Ngô Thị Lan',     'MB000009', NULL),
('2a000000-0000-4000-8000-000000000020', 'Hoàng Văn Nam',   'MB000010', NULL);

-- Ví của 10 Member. Số dư KHÔNG gõ tay: bước 14 tính lại từ chính sổ wallet_transactions
-- nên ví và sổ không bao giờ lệch nhau.
INSERT INTO wallets (id, member_id, balance, locked_balance, updated_at)
SELECT ('3c000000-0000-4000-8000-' || lpad((10 + g)::text, 12, '0'))::uuid,
       ('2a000000-0000-4000-8000-' || lpad((10 + g)::text, 12, '0'))::uuid,
       0, 0, now()
FROM generate_series(1, 10) AS g;

-- Quỹ nền tảng (hệ thống chỉ có đúng 1 dòng). Số dư cũng tính lại ở bước 14.
INSERT INTO platform_funds (id, balance, updated_at)
VALUES ('80000000-0000-4000-8000-000000000001', 0, now());


-- =============================================================================
-- 4. HỒ SƠ TỔ CHỨC TỪ THIỆN (3 hồ sơ = 3 trạng thái duyệt)
-- =============================================================================
-- cccd_front_url / cccd_back_url / organization_documents.file_url là ảnh giấy tờ -
-- chỉ Moderator xem được, và KHÔNG API nào trả URL gốc nữa (F-139): response chỉ
-- trả đường dẫn /api/organizations/{id}/legal-files/... do LegalFileLinks sinh.
-- Seed để NULL cho CCCD vì không có file thật; đường dẫn tải vẫn hoạt động và trả 404.
INSERT INTO organization_profiles (
    account_id, legal_name, legal_name_normalized, display_name, display_name_normalized,
    org_type, tax_code, operation_scope, address, contact_phone, contact_email, website,
    description, logo_url, verification_status, rejection_reason, verified_by, verified_at,
    submitted_at, rejected_at, donation_point_name, donation_point_address,
    donation_point_latitude, donation_point_longitude, donation_point_receiving_hours,
    donation_point_contact_phone, full_name, position, cccd_number,
    cccd_front_url, cccd_back_url) VALUES
-- org1: ĐÃ DUYỆT - tổ chức này có chiến dịch, bài viết, người theo dõi, nhận quyên góp
('2a000000-0000-4000-8000-000000000031',
 'Trung tâm Bảo trợ Xã hội Ánh Dương', 'trung tam bao tro xa hoi anh duong',
 'Ánh Dương', 'anh duong',
 'Tổ chức phi lợi nhuận', '0312345678', 'TP. Hồ Chí Minh',
 '10 Nguyễn Huệ, Phường Sài Gòn, TP. Hồ Chí Minh', '0900000031', 'org1@rehome.com',
 'https://anhduong.org.vn',
 'Tiếp nhận quần áo cũ còn sử dụng được, phân loại và chuyển tới trẻ em vùng cao, '
 'người lao động khó khăn tại TP.HCM và các tỉnh miền Trung.',
 NULL, 'VERIFIED', NULL, '2a000000-0000-4000-8000-000000000002',
 now() - interval '50 days', now() - interval '58 days', NULL,
 'Điểm nhận đồ Ánh Dương - Quận 1', '10 Nguyễn Huệ, Phường Sài Gòn, TP. Hồ Chí Minh',
 10.7745000, 106.7030000, '08:00 - 17:00 (Thứ 2 - Thứ 6)', '0900000031',
 'Hoàng Văn Đại', 'Giám đốc', '079123456789', NULL, NULL),
-- org2: ĐANG CHỜ DUYỆT - sinh ra 1 việc ORG_VERIFICATION trong hàng đợi Moderator
('2a000000-0000-4000-8000-000000000032',
 'Câu lạc bộ Thiện nguyện Sinh viên Hà Nội', 'cau lac bo thien nguyen sinh vien ha noi',
 'CLB Thiện nguyện Sinh viên', 'clb thien nguyen sinh vien',
 'Câu lạc bộ / Nhóm tự phát', NULL, 'Hà Nội',
 '30 Tràng Tiền, Phường Cửa Nam, Hà Nội', '0900000032', 'org2@rehome.com', NULL,
 'Nhóm sinh viên tổ chức các đợt thu gom quần áo mùa đông cho học sinh vùng núi phía Bắc.',
 NULL, 'PENDING', NULL, NULL, NULL, now() - interval '6 days', NULL,
 'Điểm nhận đồ CLB - Cầu Giấy', '45 Xuân Thủy, Phường Cầu Giấy, Hà Nội',
 21.0368000, 105.7828000, '17:00 - 21:00 (Thứ 2 - Chủ nhật)', '0900000032',
 'Nguyễn Thị Thanh Thảo', 'Chủ nhiệm', '001301234567', NULL, NULL),
-- org3: BỊ TỪ CHỐI - test xem lý do từ chối + nộp lại hồ sơ (API 128/137/138)
('2a000000-0000-4000-8000-000000000033',
 'Quỹ Từ thiện Hy Vọng Mới', 'quy tu thien hy vong moi',
 'Hy Vọng Mới', 'hy vong moi',
 'Quỹ từ thiện', '0398765432', 'Toàn quốc',
 '88 Lê Lợi, Phường Bến Thành, TP. Hồ Chí Minh', '0900000033', 'org3@rehome.com', NULL,
 'Quỹ từ thiện hoạt động trong lĩnh vực hỗ trợ đồ dùng, quần áo cho người vô gia cư.',
 NULL, 'REJECTED',
 'Giấy phép hoạt động đã hết hiệu lực từ 01/2026. Vui lòng nộp lại giấy phép còn hiệu lực '
 'và ảnh CCCD mặt sau rõ nét hơn.',
 '2a000000-0000-4000-8000-000000000002', NULL,
 now() - interval '30 days', now() - interval '25 days',
 'Điểm nhận đồ Hy Vọng Mới', '88 Lê Lợi, Phường Bến Thành, TP. Hồ Chí Minh',
 10.7720000, 106.6980000, '09:00 - 16:00 (Thứ 3 - Thứ 7)', '0900000033',
 'Trần Quốc Bảo', 'Chủ tịch Hội đồng quản lý', '079987654321', NULL, NULL);

-- Giấy tờ pháp lý kèm theo hồ sơ (accepted = kết luận của Moderator cho từng giấy tờ)
INSERT INTO organization_documents (id, organization_id, doc_type, doc_number, issuing_authority,
                                    issue_date, file_url, accepted) VALUES
('2b000000-0000-4000-8000-000000000001', '2a000000-0000-4000-8000-000000000031',
 'Giấy phép hoạt động', 'GP-2023/0456', 'Sở Nội vụ TP. Hồ Chí Minh', DATE '2023-04-18', NULL, true),
('2b000000-0000-4000-8000-000000000002', '2a000000-0000-4000-8000-000000000031',
 'Quyết định thành lập', 'QD-2023/112', 'UBND TP. Hồ Chí Minh', DATE '2023-03-02', NULL, true),
('2b000000-0000-4000-8000-000000000003', '2a000000-0000-4000-8000-000000000032',
 'Giấy xác nhận của Đoàn trường', 'XN-2026/77', 'Đoàn TNCS Trường ĐH Quốc gia Hà Nội',
 DATE '2026-09-10', NULL, NULL),
('2b000000-0000-4000-8000-000000000004', '2a000000-0000-4000-8000-000000000033',
 'Giấy phép hoạt động', 'GP-2019/0921', 'Bộ Nội vụ', DATE '2019-01-15', NULL, false);

-- Tổ chức đã duyệt xin đổi thông tin pháp lý -> việc LEGAL_CHANGE trong hàng đợi Moderator
INSERT INTO organization_legal_change_requests (id, organization_id, proposed_changes, status,
                                                reject_reason, reviewed_by, reviewed_at, created_at) VALUES
('2c000000-0000-4000-8000-000000000001', '2a000000-0000-4000-8000-000000000031',
 '{"legalName": "Trung tâm Bảo trợ Xã hội Ánh Dương", "address": "25 Lê Duẩn, Phường Bến Thành, TP. Hồ Chí Minh", "contactPhone": "0900000131"}'::jsonb,
 'PENDING', NULL, NULL, NULL, now() - interval '2 days');


-- =============================================================================
-- 5. ĐỊA CHỈ GIAO HÀNG CỦA MEMBER
-- =============================================================================
-- ward_name + province_name phải là TÊN trong danh mục GHN hệ 2 cấp - đây là giá trị
-- duy nhất tạo được vận đơn với is_new_to_address = true (gửi "TP. Hồ Chí Minh" sẽ bị
-- GHN trả TO_ADDRESS_CONFLICT).
INSERT INTO shipping_addresss (id, member_id, recipient_name, phone, address_detail,
                               ward_name, ward_code, province_name, province_id, is_default) VALUES
('90000000-0000-4000-8000-000000000001', '2a000000-0000-4000-8000-000000000011',
 'Nguyễn Văn An', '0900000011', '12 Nguyễn Huệ, Căn hộ 8A',
 'Phường Sài Gòn', NULL, 'Hồ Chí Minh', 1000001, true),
('90000000-0000-4000-8000-000000000002', '2a000000-0000-4000-8000-000000000011',
 'Nguyễn Văn An (giao tới công ty)', '0912345678', '45 Lê Lợi, Toà nhà Saigon Centre, tầng 9',
 'Phường Bến Thành', NULL, 'Hồ Chí Minh', 1000001, false),
('90000000-0000-4000-8000-000000000003', '2a000000-0000-4000-8000-000000000012',
 'Trần Thị Bình', '0900000012', '78 Trần Thái Tông, Toà N03',
 'Phường Cầu Giấy', NULL, 'Hà Nội', NULL, true),
('90000000-0000-4000-8000-000000000004', '2a000000-0000-4000-8000-000000000015',
 'Tạ Thị Ngọc Anh', '0900000015', '102 Nguyễn Thị Thập, Chung cư Era Town',
 'Phường Tân Mỹ', NULL, 'Hồ Chí Minh', 1000001, true),
('90000000-0000-4000-8000-000000000005', '2a000000-0000-4000-8000-000000000018',
 'Bùi Quang Huy', '0900000018', '25 Điện Biên Phủ',
 'Phường Sài Gòn', NULL, 'Hồ Chí Minh', 1000001, true),
('90000000-0000-4000-8000-000000000006', '2a000000-0000-4000-8000-000000000018',
 'Bùi Quang Huy (nhà bố mẹ)', '0900000118', '9 Nguyễn Chí Thanh',
 'Phường Cửa Nam', NULL, 'Hà Nội', NULL, false),
('90000000-0000-4000-8000-000000000007', '2a000000-0000-4000-8000-000000000013',
 'Lê Hoàng Cường', '0900000013', '55 Nguyễn Thị Thập',
 'Phường Tân Mỹ', NULL, 'Hồ Chí Minh', 1000001, true),
('90000000-0000-4000-8000-000000000008', '2a000000-0000-4000-8000-000000000014',
 'Phạm Thu Dung', '0900000014', '120 Xuân Thủy',
 'Phường Cầu Giấy', NULL, 'Hà Nội', NULL, true);


-- =============================================================================
-- 6. DANH MỤC - THƯƠNG HIỆU - THAM SỐ HỆ THỐNG - GÓI VIP - MẪU VOUCHER
-- =============================================================================
-- 6.1 DANH MỤC (18 danh mục, trùng khớp CategoryInitializer)
-- tryon_slot QUYẾT ĐỊNH sản phẩm được thử đồ ở vị trí nào: áo -> TOP, quần/chân váy ->
-- BOTTOM, đầm/váy liền/jumpsuit/áo dài -> DRESS (mặc nguyên bộ, không ghép thêm).
-- code = tiền tố giữa của SKU, sinh theo CodeGenerator.fromName (chữ cái đầu mỗi từ,
-- bỏ dấu); "Áo kiểu" trùng "Áo khoác" (AK) nên thành AK2 đúng như makeUnique() làm.
INSERT INTO categories (id, name, code, tryon_slot, status) VALUES
('4d000000-0000-4000-8000-000000000001', 'Áo thun',                     'AT',    'TOP',    'ACTIVE'),
('4d000000-0000-4000-8000-000000000002', 'Áo sơ mi',                    'ASM',   'TOP',    'ACTIVE'),
('4d000000-0000-4000-8000-000000000003', 'Áo polo',                     'AP',    'TOP',    'ACTIVE'),
('4d000000-0000-4000-8000-000000000004', 'Áo len',                      'AL',    'TOP',    'ACTIVE'),
('4d000000-0000-4000-8000-000000000005', 'Áo hoodie / Áo nỉ',           'AHAN',  'TOP',    'ACTIVE'),
('4d000000-0000-4000-8000-000000000006', 'Áo khoác',                    'AK',    'TOP',    'ACTIVE'),
('4d000000-0000-4000-8000-000000000007', 'Áo blazer',                   'AB',    'TOP',    'ACTIVE'),
('4d000000-0000-4000-8000-000000000008', 'Áo kiểu',                     'AK2',   'TOP',    'ACTIVE'),
('4d000000-0000-4000-8000-000000000009', 'Quần jeans',                  'QJ',    'BOTTOM', 'ACTIVE'),
('4d000000-0000-4000-8000-000000000010', 'Quần tây',                    'QT',    'BOTTOM', 'ACTIVE'),
('4d000000-0000-4000-8000-000000000011', 'Quần kaki',                   'QK',    'BOTTOM', 'ACTIVE'),
('4d000000-0000-4000-8000-000000000012', 'Quần short',                  'QS',    'BOTTOM', 'ACTIVE'),
('4d000000-0000-4000-8000-000000000013', 'Quần jogger / Quần thể thao', 'QJQTT', 'BOTTOM', 'ACTIVE'),
('4d000000-0000-4000-8000-000000000014', 'Chân váy',                    'CV',    'BOTTOM', 'ACTIVE'),
('4d000000-0000-4000-8000-000000000015', 'Đầm',                         'D',     'DRESS',  'ACTIVE'),
('4d000000-0000-4000-8000-000000000016', 'Váy liền',                    'VL',    'DRESS',  'ACTIVE'),
('4d000000-0000-4000-8000-000000000017', 'Jumpsuit',                    'J',     'DRESS',  'ACTIVE'),
('4d000000-0000-4000-8000-000000000018', 'Áo dài',                      'AD',    'DRESS',  'ACTIVE');

-- 6.2 THƯƠNG HIỆU (20 thương hiệu, trùng khớp BrandInitializer)
INSERT INTO brands (id, name, status) VALUES
('5e000000-0000-4000-8000-000000000001', 'Uniqlo',          'ACTIVE'),
('5e000000-0000-4000-8000-000000000002', 'Zara',            'ACTIVE'),
('5e000000-0000-4000-8000-000000000003', 'H&M',             'ACTIVE'),
('5e000000-0000-4000-8000-000000000004', 'Mango',           'ACTIVE'),
('5e000000-0000-4000-8000-000000000005', 'Levi''s',         'ACTIVE'),
('5e000000-0000-4000-8000-000000000006', 'Nike',            'ACTIVE'),
('5e000000-0000-4000-8000-000000000007', 'Adidas',          'ACTIVE'),
('5e000000-0000-4000-8000-000000000008', 'Puma',            'ACTIVE'),
('5e000000-0000-4000-8000-000000000009', 'Converse',        'ACTIVE'),
('5e000000-0000-4000-8000-000000000010', 'Champion',        'ACTIVE'),
('5e000000-0000-4000-8000-000000000011', 'Routine',         'ACTIVE'),
('5e000000-0000-4000-8000-000000000012', 'Coolmate',        'ACTIVE'),
('5e000000-0000-4000-8000-000000000013', 'Canifa',          'ACTIVE'),
('5e000000-0000-4000-8000-000000000014', 'Owen',            'ACTIVE'),
('5e000000-0000-4000-8000-000000000015', 'Yame',            'ACTIVE'),
('5e000000-0000-4000-8000-000000000016', 'IVY moda',        'ACTIVE'),
('5e000000-0000-4000-8000-000000000017', 'Elise',           'ACTIVE'),
('5e000000-0000-4000-8000-000000000018', 'The Blue Tshirt', 'ACTIVE'),
('5e000000-0000-4000-8000-000000000019', 'Biluxury',        'ACTIVE'),
('5e000000-0000-4000-8000-000000000020', 'Khác',            'ACTIVE');

-- 6.3 THAM SỐ HỆ THỐNG (đủ 26 key trong SystemConfigCatalog.defaults(), Admin sửa ở F-157)
-- Toàn bộ là giá trị mặc định của catalog, TRỪ withdrawal_fee: đặt 50.000đ để demo được
-- luồng trả phí rút hàng (mặc định của code là 0 = miễn phí, lúc đó luồng không có gì xảy ra).
-- Giá trị này phải khớp consignment_receipts.withdrawal_fee ở bước 7.
INSERT INTO system_configs (id, "key", "value", updated_by, updated_at) VALUES
('5f000000-0000-4000-8000-000000000001', 'term_length_days',               '30',      '2a000000-0000-4000-8000-000000000001', now() - interval '399 days'),
('5f000000-0000-4000-8000-000000000002', 'max_renewals',                   '2',       '2a000000-0000-4000-8000-000000000001', now() - interval '399 days'),
('5f000000-0000-4000-8000-000000000003', 'free_withdrawal_window_days',    '7',       '2a000000-0000-4000-8000-000000000001', now() - interval '399 days'),
('5f000000-0000-4000-8000-000000000004', 'commission_rate_term1',          '0.10',    '2a000000-0000-4000-8000-000000000001', now() - interval '399 days'),
('5f000000-0000-4000-8000-000000000005', 'commission_rate_term2',          '0.15',    '2a000000-0000-4000-8000-000000000001', now() - interval '399 days'),
('5f000000-0000-4000-8000-000000000006', 'commission_rate_term3',          '0.20',    '2a000000-0000-4000-8000-000000000001', now() - interval '399 days'),
('5f000000-0000-4000-8000-000000000007', 'degraded_compensation_rate',     '0.50',    '2a000000-0000-4000-8000-000000000001', now() - interval '399 days'),
('5f000000-0000-4000-8000-000000000008', 'floor_price_ratio',              '0.50',    '2a000000-0000-4000-8000-000000000001', now() - interval '399 days'),
('5f000000-0000-4000-8000-000000000009', 'withdrawal_fee',                 '50000',   '2a000000-0000-4000-8000-000000000001', now() - interval '120 days'),
('5f000000-0000-4000-8000-000000000010', 'max_concurrent_campaigns',       '3',       '2a000000-0000-4000-8000-000000000001', now() - interval '399 days'),
('5f000000-0000-4000-8000-000000000011', 'legal_image_retention_days',     '30',      '2a000000-0000-4000-8000-000000000001', now() - interval '399 days'),
('5f000000-0000-4000-8000-000000000012', 'overdue_task_hours',             '48',      '2a000000-0000-4000-8000-000000000001', now() - interval '399 days'),
('5f000000-0000-4000-8000-000000000013', 'min_payout_amount',              '50000',   '2a000000-0000-4000-8000-000000000001', now() - interval '399 days'),
('5f000000-0000-4000-8000-000000000014', 'max_payout_requests_per_day',    '3',       '2a000000-0000-4000-8000-000000000001', now() - interval '399 days'),
('5f000000-0000-4000-8000-000000000015', 'end_of_term_decision_days',      '30',      '2a000000-0000-4000-8000-000000000001', now() - interval '399 days'),
('5f000000-0000-4000-8000-000000000016', 'degraded_item_decision_days',    '30',      '2a000000-0000-4000-8000-000000000001', now() - interval '399 days'),
('5f000000-0000-4000-8000-000000000017', 'bring_in_deadline_days',         '7',       '2a000000-0000-4000-8000-000000000001', now() - interval '399 days'),
('5f000000-0000-4000-8000-000000000018', 'max_pending_forms_per_member',   '5',       '2a000000-0000-4000-8000-000000000001', now() - interval '399 days'),
('5f000000-0000-4000-8000-000000000019', 'max_price_changes_per_day',      '1',       '2a000000-0000-4000-8000-000000000001', now() - interval '399 days'),
('5f000000-0000-4000-8000-000000000020', 'order_payment_timeout_minutes',  '15',      '2a000000-0000-4000-8000-000000000001', now() - interval '399 days'),
('5f000000-0000-4000-8000-000000000021', 'order_auto_complete_days',       '3',       '2a000000-0000-4000-8000-000000000001', now() - interval '399 days'),
('5f000000-0000-4000-8000-000000000022', 'return_request_window_days',     '3',       '2a000000-0000-4000-8000-000000000001', now() - interval '399 days'),
('5f000000-0000-4000-8000-000000000023', 'return_handover_carrier_days',   '3',       '2a000000-0000-4000-8000-000000000001', now() - interval '399 days'),
('5f000000-0000-4000-8000-000000000024', 'counter_pickup_days',            '7',       '2a000000-0000-4000-8000-000000000001', now() - interval '399 days'),
('5f000000-0000-4000-8000-000000000025', 'membership_expiry_reminder_days','3',       '2a000000-0000-4000-8000-000000000001', now() - interval '399 days'),
('5f000000-0000-4000-8000-000000000026', 'voucher_max_ratio_of_commission','1.00',    '2a000000-0000-4000-8000-000000000001', now() - interval '399 days');

-- 6.4 GÓI THÀNH VIÊN VIP (4 hạng, trùng khớp MembershipPlanInitializer)
-- Mọi hạn mức là DỮ LIỆU: Admin sửa ở màn W-01, code không hard-code con nào.
-- live_tryon_per_month / ai_tryon_per_month = NULL nghĩa là không chặn theo tháng.
INSERT INTO membership_plans (id, tier, name, description, price_per_month, price_per_year,
                              live_tryon_per_day, live_tryon_per_month, live_tryon_max_seconds,
                              ai_tryon_per_day, ai_tryon_per_month, featured_listing,
                              max_pending_consignment_forms, purchasable, active, sort_order,
                              created_at, updated_at) VALUES
('6f000000-0000-4000-8000-000000000001', 'FREE', 'Thành viên thường',
 'Hạn mức cơ bản, miễn phí cho mọi thành viên', 0, NULL,
 2, NULL, 10, 5, NULL, false, 5, false, true, 0,
 now() - interval '399 days', now() - interval '399 days'),
('6f000000-0000-4000-8000-000000000002', 'SILVER', 'Thành viên Bạc',
 'Gói nhập môn, dùng thử đồ thoải mái hơn', 29000, 290000,
 5, NULL, 10, 15, NULL, true, 10, true, true, 1,
 now() - interval '399 days', now() - interval '399 days'),
('6f000000-0000-4000-8000-000000000003', 'GOLD', 'Thành viên Vàng',
 'Gói phổ thông, có voucher đều đặn', 59000, 590000,
 10, NULL, 15, 40, NULL, true, 15, true, true, 2,
 now() - interval '399 days', now() - interval '399 days'),
('6f000000-0000-4000-8000-000000000004', 'DIAMOND', 'Thành viên Kim cương',
 'Gói cao cấp, gần như không giới hạn', 99000, 990000,
 20, NULL, 20, 60, NULL, true, 30, true, true, 3,
 now() - interval '399 days', now() - interval '399 days');

-- 6.5 MẪU VOUCHER + SỐ MÃ MỖI THÁNG THEO HẠNG (trùng khớp VoucherTemplateInitializer)
-- SHIPPING là FIXED 15.000đ: phí ship thật thấp hơn thì chỉ giảm bằng đúng phí ship,
-- không hoàn phần thừa. ORDER_DISCOUNT là PERCENT 10% trần 50.000đ, nhưng trần THẬT
-- thường là hoa hồng của kỳ ký gửi -> cùng một mã cho số tiền khác nhau ở kỳ 1 và kỳ 3.
INSERT INTO voucher_templates (id, name, description, type, discount_kind, discount_value,
                               max_discount_amount, min_order_amount, valid_days, active,
                               created_at, updated_at) VALUES
('70000000-0000-4000-8000-000000000001', 'Miễn phí vận chuyển',
 'Giảm tối đa 15.000đ phí vận chuyển', 'SHIPPING', 'FIXED', 15000, NULL, 0, 30, true,
 now() - interval '399 days', now() - interval '399 days'),
('70000000-0000-4000-8000-000000000002', 'Giảm giá đơn hàng',
 'Giảm 10% tiền hàng, tối đa 50.000đ', 'ORDER_DISCOUNT', 'PERCENT', 0.10, 50000, 0, 30, true,
 now() - interval '399 days', now() - interval '399 days');

-- Không có dòng FREE: hạng miễn phí không nhận mã nào.
INSERT INTO voucher_template_tier_rules (id, voucher_template_id, tier, quantity_per_month) VALUES
('71000000-0000-4000-8000-000000000001', '70000000-0000-4000-8000-000000000001', 'SILVER',  2),
('71000000-0000-4000-8000-000000000002', '70000000-0000-4000-8000-000000000001', 'GOLD',    4),
('71000000-0000-4000-8000-000000000003', '70000000-0000-4000-8000-000000000001', 'DIAMOND', 6),
('71000000-0000-4000-8000-000000000004', '70000000-0000-4000-8000-000000000002', 'SILVER',  1),
('71000000-0000-4000-8000-000000000005', '70000000-0000-4000-8000-000000000002', 'GOLD',    2),
('71000000-0000-4000-8000-000000000006', '70000000-0000-4000-8000-000000000002', 'DIAMOND', 4);

-- 6.6 TỪ KHOÁ VI PHẠM (lọc nội dung bài viết/bình luận của tổ chức - không có initializer)
INSERT INTO violation_words (id, word, category, severity, action, is_active, note,
                             created_at, updated_at) VALUES
('72000000-0000-4000-8000-000000000001', 'hàng fake',     'COUNTERFEIT',   'BLOCKED', 'AUTO_REJECT',  true,
 'Rao bán hàng giả thương hiệu', now() - interval '399 days', now() - interval '399 days'),
('72000000-0000-4000-8000-000000000002', 'hàng super fake','COUNTERFEIT',  'BLOCKED', 'AUTO_REJECT',  true,
 NULL, now() - interval '399 days', now() - interval '399 days'),
('72000000-0000-4000-8000-000000000003', 'lừa đảo',       'FRAUD',         'BLOCKED', 'NOTIFY_ADMIN', true,
 'Tố cáo/vu khống cần Admin xem', now() - interval '399 days', now() - interval '399 days'),
('72000000-0000-4000-8000-000000000004', 'chuyển khoản riêng', 'FRAUD',    'WARNING', 'AUTO_FLAG',    true,
 'Dấu hiệu giao dịch ngoài sàn', now() - interval '399 days', now() - interval '399 days'),
('72000000-0000-4000-8000-000000000005', 'vay nhanh',     'SPAM',          'WARNING', 'AUTO_FLAG',    true,
 NULL, now() - interval '399 days', now() - interval '399 days'),
('72000000-0000-4000-8000-000000000006', 'đồ cấm',        'INAPPROPRIATE', 'BLOCKED', 'AUTO_REJECT',  true,
 NULL, now() - interval '399 days', now() - interval '399 days');


-- =============================================================================
-- 7. SẢN PHẨM (73 món) + PHIẾU KÝ GỬI + BIÊN NHẬN
-- =============================================================================
-- Mỗi sản phẩm bắt buộc đi theo đúng chuỗi nghiệp vụ của hệ thống:
--     consignment_form (Member gửi, Moderator duyệt)
--  -> consignment_receipt (Moderator nhận hàng tại kho, chốt giá + điều khoản)
--  -> product (được đăng bán, SKU = <mã kho>-<mã danh mục>-<số thứ tự>)
--  -> product_photos
--
-- Phân bổ 73 món:
--     53 ON_SALE  (#1-#50 + #62 vừa được nhả ra từ đơn bị huỷ + #69/#70 ảnh nội bộ)
--      5 SOLD     (#51-#55, đã bán xong qua 5 đơn COMPLETED)
--      4 RESERVED (#58-#61, #71 - đang nằm trong đơn chưa xong)
--      2 RETURNED / PLATFORM_OWNED (#56, #57 - trả hàng)
--      1 DESTROYED (#68 - trả hàng hợp lệ, Moderator quyết định tiêu huỷ)
--      2 WAREHOUSE (#63, #64 - hết 90 ngày, chờ Member chọn cách xử lý)
--      3 WITHDRAWN (#65 lấy về cuối kỳ, #72/#73 đang có yêu cầu rút hàng)
--      1 UNCLAIMED (#66 - quá hạn quyết định, hàng vô thừa nhận)
--      1 DONATED   (#67 - cuối kỳ Member chọn tặng từ thiện)
--
-- Kỳ ký gửi (F-029 / Điều 8 hợp đồng): mỗi kỳ 30 ngày, tối đa 3 kỳ = 90 ngày.
-- current_term và term_started_at suy ra từ số ngày đã ký gửi, nên dữ liệu khớp
-- đúng với EndOfTermLifecycleServiceImpl và job sẽ KHÔNG đổi gì khi app chạy lên.
-- Hoa hồng theo kỳ: kỳ 1 = 10%, kỳ 2 = 15%, kỳ 3 = 20% (snapshot trên biên nhận).
DO $seed_products$
DECLARE
    r               record;
    v_now           timestamptz := now();
    v_consigned     timestamptz;
    v_term          smallint;
    v_term_start    timestamptz;
    v_eot           timestamptz;
    v_unclaimed     timestamptz;
    v_form_id       uuid;
    v_receipt_id    uuid;
    v_wh_code       text;
    v_wh_id         uuid;
    v_cat_id        uuid;
    v_brand_id      uuid;
    v_member_id     uuid;
    v_mod_id        uuid;
    v_mod1          uuid := '2a000000-0000-4000-8000-000000000002';
    v_mod2          uuid := '2a000000-0000-4000-8000-000000000003';
    v_mod3          uuid := '2a000000-0000-4000-8000-000000000004';
    v_photos        jsonb := jsonb_build_object(
        -- TOP: áo sơ mi / áo thun / áo khoác
        'T1', 'https://cdn.dummyjson.com/product-images/mens-shirts/blue-&-black-check-shirt/1.webp',
        'T2', 'https://cdn.dummyjson.com/product-images/mens-shirts/gigabyte-aorus-men-tshirt/1.webp',
        'T3', 'https://cdn.dummyjson.com/product-images/mens-shirts/man-plaid-shirt/1.webp',
        'T4', 'https://cdn.dummyjson.com/product-images/mens-shirts/man-short-sleeve-shirt/1.webp',
        'T5', 'https://cdn.dummyjson.com/product-images/mens-shirts/men-check-shirt/1.webp',
        'T6', 'https://fakestoreapi.com/img/71li-ujtlUL._AC_UX679_t.png',
        'T7', 'https://fakestoreapi.com/img/51Y5NI-I5jL._AC_UX679_t.png',
        'T8', 'https://fakestoreapi.com/img/71-3HjGNDUL._AC_SY879._SX._UX._SY._UY_t.png',
        'T9', 'https://fakestoreapi.com/img/71z3kpMAYsL._AC_UY879_t.png',
        -- BOTTOM: quần jeans / quần tây / chân váy
        'B1', 'https://images.unsplash.com/photo-1708523842501-1619478cea1f?rect=316,0,2598,3745&w=800&fit=crop&q=80',
        'B2', 'https://images.unsplash.com/photo-1718252540511-e958742e4165?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=NHwxMjA3fDA&ixlib=rb-4.1.0&q=80&w=1080',
        'B3', 'https://upload.wikimedia.org/wikipedia/commons/thumb/c/c7/501xx-front.jpg/500px-501xx-front.jpg',
        'B4', 'https://upload.wikimedia.org/wikipedia/commons/thumb/b/b3/Back_side_Chino_trousers_with_welt_pocket.jpg/960px-Back_side_Chino_trousers_with_welt_pocket.jpg',
        'B5', 'https://cdn.dummyjson.com/product-images/womens-dresses/corset-leather-with-skirt/1.webp',
        'B6', 'https://cdn.dummyjson.com/product-images/womens-dresses/corset-with-black-skirt/1.webp',
        'B7', 'https://upload.wikimedia.org/wikipedia/commons/f/f9/Pleated_skirt.jpg',
        -- DRESS: đầm / váy liền / jumpsuit
        'D1', 'https://cdn.dummyjson.com/product-images/tops/blue-frock/1.webp',
        'D2', 'https://cdn.dummyjson.com/product-images/tops/girl-summer-dress/1.webp',
        'D3', 'https://cdn.dummyjson.com/product-images/tops/gray-dress/1.webp',
        'D4', 'https://cdn.dummyjson.com/product-images/tops/short-frock/1.webp',
        'D5', 'https://cdn.dummyjson.com/product-images/tops/tartan-dress/1.webp',
        'D6', 'https://cdn.dummyjson.com/product-images/womens-dresses/black-women''s-gown/1.webp',
        'D7', 'https://cdn.dummyjson.com/product-images/womens-dresses/dress-pea/1.webp',
        'D8', 'https://cdn.dummyjson.com/product-images/womens-dresses/marni-red-&-black-suit/1.webp',
        -- Ảnh NỘI BỘ (uploads/products/...) - chỉ 2 ảnh này thử đồ AI mới chạy được
        'LT', 'http://localhost:8080/files/products/seed-tryon-top.png',
        'LB', 'http://localhost:8080/files/products/seed-tryon-bottom.png');
BEGIN
FOR r IN
    SELECT * FROM (VALUES
    -- ord, sku, tên sản phẩm, danh mục, thương hiệu, size, giá chốt, giá hiện tại,
    --   người ký gửi (11..20 = member1..member10), số ngày đã ký gửi, kỳ ép (NULL = tự tính),
    --   trạng thái, ảnh 1, ảnh 2, tình trạng hàng
    -- ---------------------------------------------------------------- ÁO THUN (TOP)
    (  1, 'HCM-AT-000001',  'Áo thun Uniqlo cotton trắng trơn',        'Áo thun',  'Uniqlo',           'M',  150000, 150000, 13, 12, NULL, 'ON_SALE', 'T2', 'T8',  'Như mới, chỉ mặc 2 lần, không giãn cổ'),
    (  2, 'HCM-AT-000002',  'Áo thun Coolmate Excool đen',             'Áo thun',  'Coolmate',         'L',  120000, 120000, 15,  8, NULL, 'ON_SALE', 'T8', NULL,  'Còn 90%, vải mát, không xù lông'),
    (  3, 'HN-AT-000003',   'Áo thun Routine in họa tiết',             'Áo thun',  'Routine',          'M',  135000, 135000, 14, 20, NULL, 'ON_SALE', 'T9', NULL,  'Còn 85%, hình in còn rõ, không bong'),
    (  4, 'HCM-AT-000004',  'Áo thun The Blue Tshirt form oversize',   'Áo thun',  'The Blue Tshirt',  'XL', 180000, 180000, 16,  5, NULL, 'ON_SALE', 'T2', 'T9',  'Như mới, còn nguyên tem'),
    (  5, 'HN-AT-000005',   'Áo thun Nike Dri-FIT xanh navy',          'Áo thun',  'Nike',             'S',  230000, 210000, 17, 33, NULL, 'ON_SALE', 'T8', NULL,  'Còn 90%, đã giảm giá sang kỳ 2'),
    -- -------------------------------------------------------------- ÁO SƠ MI (TOP)
    (  6, 'HCM-ASM-000006', 'Áo sơ mi Zara trắng tay dài',             'Áo sơ mi', 'Zara',             'M',  320000, 320000, 13, 15, NULL, 'ON_SALE', 'T1', 'T5',  'Như mới, không vết bẩn ở cổ và tay'),
    (  7, 'HCM-ASM-000007', 'Áo sơ mi Owen kẻ sọc xanh',               'Áo sơ mi', 'Owen',             'L',  280000, 280000, 15, 25, NULL, 'ON_SALE', 'T3', NULL,  'Còn 90%, vải không nhăn, cúc đủ'),
    (  8, 'HN-ASM-000008',  'Áo sơ mi Uniqlo Oxford xanh nhạt',        'Áo sơ mi', 'Uniqlo',           'M',  250000, 250000, 14, 40, NULL, 'ON_SALE', 'T4', NULL,  'Còn 85%, bạc nhẹ ở cổ tay, đang ở kỳ 2'),
    (  9, 'HN-ASM-000009',  'Áo sơ mi Biluxury dài tay đen',           'Áo sơ mi', 'Biluxury',         'XL', 230000, 230000, 17, 10, NULL, 'ON_SALE', 'T5', NULL,  'Như mới, mua sai size nên chưa mặc'),
    -- --------------------------------------------------------------- ÁO POLO (TOP)
    ( 10, 'HCM-AP-000010',  'Áo polo Yame xanh rêu',                   'Áo polo',  'Yame',             'L',  190000, 190000, 16, 18, NULL, 'ON_SALE', 'T5', NULL,  'Còn 90%, cổ áo còn đứng'),
    ( 11, 'HN-AP-000011',   'Áo polo Adidas trắng viền đen',           'Áo polo',  'Adidas',           'M',  220000, 220000, 19,  6, NULL, 'ON_SALE', 'T4', 'T1',  'Như mới, còn nguyên logo thêu'),
    -- ---------------------------------------------------------------- ÁO LEN (TOP)
    ( 12, 'HCM-AL-000012',  'Áo len H&M cổ tròn màu be',               'Áo len',   'H&M',              'M',  240000, 240000, 13, 28, NULL, 'ON_SALE', 'T7', NULL,  'Còn 85%, xù lông nhẹ ở vùng nách'),
    ( 13, 'HN-AL-000013',   'Áo len Canifa dệt kim xám',               'Áo len',   'Canifa',           'S',  290000, 260000, 14, 44, NULL, 'ON_SALE', 'T7', NULL,  'Còn 85%, đã giảm giá, dáng vẫn đẹp'),
    -- ----------------------------------------------------- ÁO HOODIE / ÁO NỈ (TOP)
    ( 14, 'HCM-AHAN-000014','Áo hoodie Champion nỉ bông xám',          'Áo hoodie / Áo nỉ', 'Champion','L',  350000, 350000, 15,  9, NULL, 'ON_SALE', 'T6', 'T7',  'Như mới, bông còn dày, dây mũ đủ'),
    ( 15, 'HCM-AHAN-000015','Áo nỉ Puma cổ tròn đen',                  'Áo hoodie / Áo nỉ', 'Puma',    'M',  290000, 290000, 16, 22, NULL, 'ON_SALE', 'T7', NULL,  'Còn 90%, không bai bo tay'),
    ( 16, 'HN-AHAN-000016', 'Áo hoodie Converse in logo trước',        'Áo hoodie / Áo nỉ', 'Converse','XL', 350000, 310000, 17, 65, NULL, 'ON_SALE', 'T6', NULL,  'Còn 80%, đã sang kỳ 3 nên giảm giá'),
    -- -------------------------------------------------------------- ÁO KHOÁC (TOP)
    ( 17, 'HCM-AK-000017',  'Áo khoác jean Levi''s trucker',           'Áo khoác', 'Levi''s',          'M',  520000, 520000, 13,  3, NULL, 'ON_SALE', 'T6', 'T7',  'Như mới, cúc đồng còn sáng'),
    ( 18, 'HCM-AK-000018',  'Áo khoác gió Uniqlo chống nước',          'Áo khoác', 'Uniqlo',           'L',  450000, 450000, 11, 14, NULL, 'ON_SALE', 'T7', NULL,  'Còn 90%, khoá kéo mượt'),
    ( 19, 'HN-AK-000019',   'Áo khoác bomber Zara đen',                'Áo khoác', 'Zara',             'M',  520000, 480000, 14, 31, NULL, 'ON_SALE', 'T6', NULL,  'Còn 90%, đã giảm giá sang kỳ 2'),
    ( 20, 'HN-AK-000020',   'Áo khoác phao Canifa lông vũ',            'Áo khoác', 'Canifa',           'XL', 650000, 650000, 19,  7, NULL, 'ON_SALE', 'T7', 'T6',  'Như mới, mặc 1 mùa đông, không bục chỉ'),
    -- ------------------------------------------------------------- ÁO BLAZER (TOP)
    ( 21, 'HCM-AB-000021',  'Áo blazer Mango xám công sở',             'Áo blazer','Mango',            'M',  540000, 540000, 15, 11, NULL, 'ON_SALE', 'T6', NULL,  'Như mới, đã giặt khô, không nhăn'),
    ( 22, 'HN-AB-000022',   'Áo blazer IVY moda dáng lửng',            'Áo blazer','IVY moda',         'S',  580000, 580000, 17, 26, NULL, 'ON_SALE', 'T6', NULL,  'Còn 90%, lớp lót nguyên vẹn'),
    -- -------------------------------------------------------------- ÁO KIỂU (TOP)
    ( 23, 'HCM-AK2-000023', 'Áo kiểu Elise tay bồng trắng',            'Áo kiểu',  'Elise',            'S',  330000, 330000, 15, 16, NULL, 'ON_SALE', 'T1', NULL,  'Như mới, vải lụa không rút'),
    ( 24, 'HN-AK2-000024',  'Áo kiểu Mango cổ vuông hồng',             'Áo kiểu',  'Mango',            'M',  300000, 300000, 14,  4, NULL, 'ON_SALE', 'T3', NULL,  'Như mới, còn nguyên tem giá'),
    -- ---------------------------------------------------------- QUẦN JEANS (BOTTOM)
    ( 25, 'HCM-QJ-000025',  'Quần jeans Levi''s 501 ống đứng',         'Quần jeans','Levi''s',         '32', 420000, 420000, 13, 13, NULL, 'ON_SALE', 'B3', 'B1',  'Còn 90%, form đứng, đường chỉ nguyên'),
    ( 26, 'HCM-QJ-000026',  'Quần jeans H&M slim fit xanh đậm',        'Quần jeans','H&M',             '30', 320000, 320000, 16, 19, NULL, 'ON_SALE', 'B1', NULL,  'Còn 85%, bạc nhẹ ở đầu gối'),
    ( 27, 'HN-QJ-000027',   'Quần jeans Uniqlo ống suông',             'Quần jeans','Uniqlo',          '31', 390000, 350000, 14, 36, NULL, 'ON_SALE', 'B2', NULL,  'Còn 90%, đã giảm giá sang kỳ 2'),
    ( 28, 'HCM-QJ-000028',  'Quần jeans Zara baggy xanh nhạt',         'Quần jeans','Zara',            '29', 380000, 380000, 15,  2, NULL, 'ON_SALE', 'B1', 'B3',  'Như mới, chưa qua sử dụng'),
    ( 29, 'HN-QJ-000029',   'Quần jeans Routine rách gối',             'Quần jeans','Routine',         '32', 330000, 290000, 17, 72, NULL, 'ON_SALE', 'B2', NULL,  'Còn 80%, rách gối là thiết kế, kỳ 3'),
    -- ----------------------------------------------------------- QUẦN TÂY (BOTTOM)
    ( 30, 'HCM-QT-000030',  'Quần tây Owen đen ống côn',               'Quần tây', 'Owen',             '32', 280000, 280000, 16, 21, NULL, 'ON_SALE', 'B4', NULL,  'Còn 90%, không bóng vải, gấu nguyên'),
    ( 31, 'HN-QT-000031',   'Quần tây Biluxury xám chì',               'Quần tây', 'Biluxury',         '30', 260000, 260000, 19,  9, NULL, 'ON_SALE', 'B4', NULL,  'Như mới, mặc 3 lần đi làm'),
    ( 32, 'HCM-QT-000032',  'Quần tây Mango nữ ống rộng',              'Quần tây', 'Mango',            'M',  310000, 310000, 15, 29, NULL, 'ON_SALE', 'B4', NULL,  'Còn 90%, cạp chun còn tốt'),
    -- ---------------------------------------------------------- QUẦN KAKI (BOTTOM)
    ( 33, 'HCM-QK-000033',  'Quần kaki Uniqlo be ống đứng',            'Quần kaki','Uniqlo',           '31', 250000, 250000, 13, 17, NULL, 'ON_SALE', 'B4', NULL,  'Còn 90%, màu chưa phai'),
    ( 34, 'HN-QK-000034',   'Quần kaki Coolmate xanh rêu',             'Quần kaki','Coolmate',         '32', 220000, 220000, 14,  1, NULL, 'ON_SALE', 'B2', NULL,  'Như mới, vừa nhập kho hôm nay'),
    -- --------------------------------------------------------- QUẦN SHORT (BOTTOM)
    ( 35, 'HCM-QS-000035',  'Quần short jeans Zara lưng cao',          'Quần short','Zara',            '28', 190000, 190000, 15, 24, NULL, 'ON_SALE', 'B1', NULL,  'Còn 90%, không sờn gấu'),
    ( 36, 'HN-QS-000036',   'Quần short thể thao Nike',                'Quần short','Nike',            'L',  160000, 160000, 17, 12, NULL, 'ON_SALE', 'B1', NULL,  'Còn 85%, chun lưng còn đàn hồi'),
    -- ------------------------------------------------ QUẦN JOGGER/THỂ THAO (BOTTOM)
    ( 37, 'HCM-QJQTT-000037','Quần jogger Adidas 3 sọc đen',           'Quần jogger / Quần thể thao','Adidas','M', 280000, 280000, 16,  6, NULL, 'ON_SALE', 'B4', NULL, 'Như mới, 3 sọc còn nguyên'),
    ( 38, 'HN-QJQTT-000038', 'Quần thể thao Puma co giãn',             'Quần jogger / Quần thể thao','Puma',  'L', 270000, 240000, 19, 61, NULL, 'ON_SALE', 'B4', NULL, 'Còn 80%, đã giảm giá, kỳ 3'),
    -- ----------------------------------------------------------- CHÂN VÁY (BOTTOM)
    ( 39, 'HCM-CV-000039',  'Chân váy xếp ly Elise đen',               'Chân váy', 'Elise',            'S',  270000, 270000, 15, 10, NULL, 'ON_SALE', 'B7', 'B5',  'Như mới, ly còn sắc nét'),
    ( 40, 'HCM-CV-000040',  'Chân váy jeans H&M chữ A',                'Chân váy', 'H&M',              'M',  230000, 230000, 11, 23, NULL, 'ON_SALE', 'B5', NULL,  'Còn 90%, khoá sau trơn'),
    ( 41, 'HN-CV-000041',   'Chân váy dài IVY moda hoa nhí',           'Chân váy', 'IVY moda',         'M',  320000, 320000, 14,  5, NULL, 'ON_SALE', 'B6', NULL,  'Như mới, vải mềm không xước'),
    -- ------------------------------------------------------------------ ĐẦM (DRESS)
    ( 42, 'HCM-D-000042',   'Đầm dạ hội Elise đen dài',                'Đầm',      'Elise',            'M',  780000, 780000, 15,  7, NULL, 'ON_SALE', 'D6', 'D3',  'Như mới, mặc 1 lần dự tiệc'),
    ( 43, 'HCM-D-000043',   'Đầm suông Zara hoa nhí',                  'Đầm',      'Zara',             'S',  420000, 420000, 13, 27, NULL, 'ON_SALE', 'D2', NULL,  'Còn 90%, hoa văn còn tươi'),
    ( 44, 'HN-D-000044',    'Đầm công sở Mango xám',                   'Đầm',      'Mango',            'M',  450000, 450000, 14, 15, NULL, 'ON_SALE', 'D3', NULL,  'Còn 90%, không bai vai'),
    ( 45, 'HN-D-000045',    'Đầm maxi IVY moda họa tiết',              'Đầm',      'IVY moda',         'L',  620000, 560000, 17, 81, NULL, 'ON_SALE', 'D5', NULL,  'Còn 80%, đã giảm giá, kỳ cuối'),
    -- ------------------------------------------------------------ VÁY LIỀN (DRESS)
    ( 46, 'HCM-VL-000046',  'Váy liền thân H&M cổ vuông',              'Váy liền', 'H&M',              'S',  340000, 340000, 15,  4, NULL, 'ON_SALE', 'D1', NULL,  'Như mới, còn nguyên tem'),
    ( 47, 'HCM-VL-000047',  'Váy liền Mango xanh pastel',              'Váy liền', 'Mango',            'M',  390000, 390000, 11, 20, NULL, 'ON_SALE', 'D4', 'D1',  'Còn 90%, không vết loang'),
    ( 48, 'HN-VL-000048',   'Váy liền Elise tay ngắn đỏ',              'Váy liền', 'Elise',            'M',  410000, 410000, 19, 11, NULL, 'ON_SALE', 'D8', NULL,  'Còn 90%, màu còn đậm'),
    -- ----------------------------------------------------------- JUMPSUIT (DRESS)
    ( 49, 'HCM-J-000049',   'Jumpsuit Zara ống rộng đen',              'Jumpsuit', 'Zara',             'M',  490000, 490000, 15,  8, NULL, 'ON_SALE', 'D7', NULL,  'Như mới, vải đứng dáng'),
    -- ------------------------------------------------------------- ÁO DÀI (DRESS)
    ( 50, 'HN-AD-000050',   'Áo dài truyền thống Elise lụa',           'Áo dài',   'Elise',            'M',  850000, 850000, 14, 18, NULL, 'ON_SALE', 'D5', NULL,  'Như mới, lụa chưa phai, mặc 1 dịp Tết'),

    -- ======================= 5 MÓN ĐÃ BÁN (đi với 5 đơn COMPLETED ở bước 9) =======
    -- Kỳ ép = kỳ TẠI THỜI ĐIỂM BÁN, vì món đã bán thì job không tăng kỳ nữa.
    ( 51, 'HCM-AK-000051',  'Áo khoác dạ Uniqlo nữ cổ lọ',             'Áo khoác', 'Uniqlo',           'M',  520000, 520000, 11, 60,    1, 'SOLD',    'T7', NULL,  'Còn 90%, dạ không bị xù'),
    ( 52, 'HCM-AT-000052',  'Áo thun Zara basic trắng',                'Áo thun',  'Zara',             'M',  290000, 290000, 11, 70,    2, 'SOLD',    'T9', NULL,  'Còn 90%, cổ chưa giãn'),
    ( 53, 'HCM-ASM-000053', 'Áo sơ mi Zara lụa trắng',                 'Áo sơ mi', 'Zara',             'M',  450000, 450000, 13, 25,    1, 'SOLD',    'T1', NULL,  'Như mới, không lỗi'),
    ( 54, 'HN-QJ-000054',   'Quần jeans Uniqlo nữ ống đứng',           'Quần jeans','Uniqlo',          '29', 320000, 320000, 14, 50, NULL, 'SOLD',    'B2', NULL,  'Còn 90%, co giãn tốt'),
    ( 55, 'HCM-D-000055',   'Đầm Elise hoa nhí tay ngắn',              'Đầm',      'Elise',            'S',  380000, 380000, 15,  9, NULL, 'SOLD',    'D2', NULL,  'Như mới, mặc 1 lần'),

    -- ===================== TRẢ HÀNG (F-132/F-136/F-036, bước 9) ===================
    ( 56, 'HCM-QT-000056',  'Quần tây Owen xám ống đứng',              'Quần tây', 'Owen',             '31', 310000, 310000, 16, 20,    1, 'RETURNED','B4', NULL,  'Còn 90%, chưa sửa gấu'),
    ( 57, 'HN-CV-000057',   'Chân váy IVY moda dáng dài',              'Chân váy', 'IVY moda',         'M',  260000, 260000, 17, 16,    1, 'PLATFORM_OWNED','B6', NULL, 'Còn 85%, đã tuột chỉ ở lưng sau khi trả về'),
    ( 68, 'HCM-AT-000068',  'Áo thun Nike cổ tròn bạc màu',            'Áo thun',  'Nike',             'L',   90000,  90000, 19, 45,    1, 'DESTROYED','T8', NULL, 'Hàng đã trả về, vải mục ở vai, không thể bán lại'),

    -- ===================== ĐANG NẰM TRONG ĐƠN CHƯA XONG (RESERVED) ================
    ( 58, 'HCM-AB-000058',  'Áo blazer Mango đen một khuy',            'Áo blazer','Mango',            'M',  560000, 560000, 13,  4, NULL, 'RESERVED','T6', NULL,  'Như mới, chưa cắt chỉ vai'),
    ( 59, 'HN-VL-000059',   'Váy liền Elise xanh cổ tim',              'Váy liền', 'Elise',            'S',  370000, 370000, 19,  6, NULL, 'RESERVED','D1', NULL,  'Như mới, còn nguyên tem'),
    ( 60, 'HN-AL-000060',   'Áo len Canifa cổ tim nâu',                'Áo len',   'Canifa',           'M',  240000, 240000, 14,  7, NULL, 'RESERVED','T7', NULL,  'Còn 90%, không xù'),
    ( 61, 'HCM-AT-000061',  'Áo thun Uniqlo nữ cổ tim',                'Áo thun',  'Uniqlo',           'S',  120000, 120000, 19,  5, NULL, 'RESERVED','T2', NULL,  'Còn 90%, mặc vài lần'),
    ( 71, 'HN-AP-000071',   'Áo polo Owen trắng viền xanh',            'Áo polo',  'Owen',             'M',  200000, 200000, 14,  4, NULL, 'RESERVED','T4', NULL,  'Như mới, chưa sử dụng'),

    -- ===================== ĐƠN BỊ HUỶ -> HÀNG ĐÃ ĐƯỢC NHẢ VỀ BÁN LẠI ==============
    ( 62, 'HCM-AK-000062',  'Áo khoác gió Nike xanh đen',              'Áo khoác', 'Nike',             'L',  420000, 420000, 13,  6, NULL, 'ON_SALE','T6', NULL,   'Như mới, mặc 1 lần'),

    -- ======== HẾT 90 NGÀY: GỠ KHỎI SÀN, CHỜ MEMBER CHỌN CÁCH XỬ LÝ (F-029) ========
    ( 63, 'HCM-QK-000063',  'Quần kaki Uniqlo xanh than',              'Quần kaki','Uniqlo',           '32', 230000, 230000, 16, 95, NULL, 'WAREHOUSE','B4', NULL, 'Còn 85%, hết kỳ chưa bán được'),
    ( 64, 'HN-D-000064',    'Đầm Zara dự tiệc vai ngang',              'Đầm',      'Zara',             'M',  480000, 480000, 17, 98, NULL, 'WAREHOUSE','D6', NULL, 'Còn 90%, hết kỳ, Member xin ký gửi lại'),
    ( 65, 'HCM-ASM-000065', 'Áo sơ mi Owen trắng dài tay',             'Áo sơ mi', 'Owen',             'L',  240000, 240000, 16,100, NULL, 'WITHDRAWN','T4', NULL,  'Còn 85%, Member chọn lấy về'),
    ( 66, 'HN-AT-000066',   'Áo thun Routine xanh cổ tròn',            'Áo thun',  'Routine',          'M',  150000, 150000, 19,135, NULL, 'UNCLAIMED','T9', NULL,  'Còn 80%, Member không phản hồi'),
    ( 67, 'HCM-QS-000067',  'Quần short Puma đen',                     'Quần short','Puma',            'M',  140000, 140000, 16,105, NULL, 'DONATED',  'B1', NULL,  'Còn 85%, Member tặng lại cho từ thiện'),

    -- ===================== ĐANG CÓ YÊU CẦU RÚT HÀNG (F-032) =======================
    -- Sản phẩm sang WITHDRAWN NGAY khi Member gửi yêu cầu rút, không chờ tới lúc bàn giao.
    ( 72, 'HCM-AK2-000072', 'Áo kiểu Elise hoa xanh tay lỡ',           'Áo kiểu',  'Elise',            'M',  310000, 310000, 16, 25, NULL, 'WITHDRAWN','T3', NULL,  'Như mới, Member xin rút về để tặng'),
    ( 73, 'HN-QT-000073',   'Quần tây Biluxury đen ống suông',         'Quần tây', 'Biluxury',         '31', 270000, 270000, 19, 40, NULL, 'WITHDRAWN','B4', NULL,  'Còn 90%, Member cần dùng lại'),

    -- =========== 2 MÓN DÙNG ẢNH NỘI BỘ -> DEMO THỬ ĐỒ AI (TOP + BOTTOM) ===========
    -- Thử đồ AI đọc ảnh bằng FileStorageService.loadByUrl và chỉ nhận URL do chính
    -- storage sinh ra, nên ảnh CDN của 71 món trên KHÔNG thử đồ được - chỉ 2 món này.
    ( 69, 'HCM-ASM-000069', 'Áo sơ mi Uniqlo Oxford xanh tay ngắn',    'Áo sơ mi', 'Uniqlo',           'L',  200000, 200000, 13,  2, NULL, 'ON_SALE','LT', NULL,   'Như mới, không lỗi'),
    ( 70, 'HCM-QT-000070',  'Quần tây Owen đen ống đứng',              'Quần tây', 'Owen',             '32', 250000, 250000, 13,  2, NULL, 'ON_SALE','LB', NULL,   'Như mới, còn nguyên form')
    ) AS t(ord, sku, name, cat, brand, size, agreed, price, seller, days_ago, term_force,
           status, photo, photo2, cond)
    ORDER BY ord
LOOP
    v_wh_code   := split_part(r.sku, '-', 1);
    SELECT id INTO v_wh_id    FROM warehouses WHERE code = v_wh_code;
    SELECT id INTO v_cat_id   FROM categories WHERE name = r.cat;
    SELECT id INTO v_brand_id FROM brands     WHERE name = r.brand;

    v_member_id := ('2a000000-0000-4000-8000-' || lpad(r.seller::text, 12, '0'))::uuid;
    -- Kho Hà Nội chỉ có 1 Moderator; kho HCM có 2, chia đều theo số thứ tự
    v_mod_id    := CASE WHEN v_wh_code = 'HN' THEN v_mod3
                        WHEN r.ord % 2 = 1    THEN v_mod1
                        ELSE v_mod2 END;

    v_consigned  := v_now - (r.days_ago || ' days')::interval;
    v_term       := COALESCE(r.term_force, LEAST(3, (r.days_ago / 30) + 1))::smallint;
    v_term_start := v_consigned + (((v_term - 1) * 30) || ' days')::interval;
    v_eot        := v_consigned + interval '90 days';
    -- Hết 90 ngày thì mở hạn 30 ngày để Member chọn lấy về / ký gửi lại (F-029)
    v_unclaimed  := CASE WHEN v_eot < v_now THEN v_eot + interval '30 days' ELSE NULL END;

    -- Phiếu ký gửi: đã APPROVED vì món đã vào kho. Member mang hàng tới trong hạn 7 ngày.
    INSERT INTO consignment_forms (id, form_no, member_id, warehouse_id, category_id, brand_id,
                                   product_name, description, desired_price, size, item_condition,
                                   status, reject_reason, bring_in_deadline, created_at, updated_at)
    VALUES (gen_random_uuid(), 'KG-' || lpad(r.ord::text, 6, '0'), v_member_id, v_wh_id,
            v_cat_id, v_brand_id, r.name, r.cond, r.agreed, r.size,
            CASE WHEN r.cond LIKE 'Như mới%' THEN 'LIKE_NEW'
                 WHEN r.cond LIKE '%nguyên tem%' THEN 'NEW_TAG'
                 WHEN r.cond LIKE '%80%' THEN 'FLAW'
                 ELSE 'GOOD' END,
            'APPROVED', NULL, (v_consigned - interval '2 days')::date + 7,
            v_consigned - interval '2 days', v_consigned)
    RETURNING id INTO v_form_id;

    -- Biên nhận: snapshot toàn bộ điều khoản tại thời điểm nhận hàng (giá sàn, hoa hồng
    -- từng kỳ, phí rút hàng, tỷ lệ đền bù) - sau này Admin đổi tham số cũng không ảnh hưởng.
    INSERT INTO consignment_receipts (id, receipt_no, consignment_form_id, member_id, moderator_id,
                                      warehouse_id, agreed_price, floor_price, term_length_days,
                                      max_renewals, free_withdrawal_window_days,
                                      commission_rate_term1, commission_rate_term2, commission_rate_term3,
                                      withdrawal_fee, degraded_compensation_rate, condition_note,
                                      moderator_note, confirmed_at, created_at)
    VALUES (gen_random_uuid(), 'KG-2026-' || lpad(r.ord::text, 6, '0'), v_form_id, v_member_id,
            v_mod_id, v_wh_id, r.agreed, round(r.agreed * 0.5), 30, 2, 7,
            0.10, 0.15, 0.20, 50000, 0.50, r.cond, NULL, v_consigned, v_consigned)
    RETURNING id INTO v_receipt_id;

    INSERT INTO products (id, consignment_receipt_id, member_id, warehouse_id, category_id, brand_id,
                          name, name_normalized, size, condition_note, current_price, status,
                          current_term, term_started_at, end_of_term_deadline,
                          unclaimed_decision_deadline, last_price_changed_at, last_price_reduced_on,
                          featured_until, version, created_at, updated_at)
    VALUES (r.sku, v_receipt_id, v_member_id, v_wh_id, v_cat_id, v_brand_id,
            r.name,
            -- name_normalized là cột GET /products?search= so khớp (gõ "ao khoac" ra "Áo khoác").
            -- App sinh cột này trong @PrePersist của entity Product; seed ghi thẳng vào DB nên
            -- phải tự điền, không thì sản phẩm BIẾN MẤT khỏi kết quả tìm kiếm.
            -- Công thức khớp VietnameseText.normalize: bỏ dấu (gồm dấu móc của ơ/ư) rồi hạ
            -- chữ thường, riêng đ/Đ -> d vì đ là ký tự độc lập không tách dấu được.
            --
            -- Phải TRANSLATE TRƯỚC rồi mới lower(), và bảng chữ phải có cả CHỮ HOA có dấu:
            -- lower() của Postgres chỉ xử lý được ký tự nhiều byte khi database dùng locale
            -- hỗ trợ Unicode. Database tạo với locale C sẽ để nguyên 'Á', và nếu lower() chạy
            -- trước thì translate không khớp -> cột normalized còn dấu -> tìm kiếm không ra gì.
            -- Làm theo thứ tự này thì lower() chỉ còn phải xử lý ASCII, đúng với mọi locale.
            lower(translate(r.name,
                      'àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ'
                   || 'ÀÁẢÃẠĂẰẮẲẴẶÂẦẤẨẪẬÈÉẺẼẸÊỀẾỂỄỆÌÍỈĨỊÒÓỎÕỌÔỒỐỔỖỘƠỜỚỞỠỢÙÚỦŨỤƯỪỨỬỮỰỲÝỶỸỴĐ',
                      'aaaaaaaaaaaaaaaaaeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyyd'
                   || 'AAAAAAAAAAAAAAAAAEEEEEEEEEEEIIIIIOOOOOOOOOOOOOOOOOUUUUUUUUUUUYYYYYD')),
            r.size, r.cond, r.price, r.status, v_term, v_term_start, v_eot, v_unclaimed,
            CASE WHEN r.price < r.agreed THEN v_term_start + interval '3 days' ELSE NULL END,
            CASE WHEN r.price < r.agreed THEN (v_term_start + interval '3 days')::date ELSE NULL END,
            NULL, 0, v_consigned, v_now);

    INSERT INTO product_photos (id, product_id, image_url, is_background_removed, display_order)
    VALUES (gen_random_uuid(), r.sku, v_photos->>r.photo, false, 0);
    IF r.photo2 IS NOT NULL THEN
        INSERT INTO product_photos (id, product_id, image_url, is_background_removed, display_order)
        VALUES (gen_random_uuid(), r.sku, v_photos->>r.photo2, false, 1);
    END IF;

    -- Member tự giảm giá món của mình (F-027, tối đa 1 lần/ngày, không dưới giá sàn).
    -- Chỉ bảng product_price_changes được dùng; price_change_logs là bảng cũ không ai đọc.
    IF r.price < r.agreed THEN
        INSERT INTO product_price_changes (id, product_id, old_price, new_price, changed_at)
        VALUES (gen_random_uuid(), r.sku, r.agreed, r.price, v_term_start + interval '3 days');
    END IF;
END LOOP;
END $seed_products$;


-- =============================================================================
-- 8. GIỎ HÀNG
-- =============================================================================
-- Giỏ của member1 cố ý có 2 món ở 2 KHO KHÁC NHAU: bấm đặt hàng sẽ tách thành
-- 2 đơn (mỗi kho 1 đơn) nhưng chỉ MỘT lần thanh toán (F-053 / §12.6).
INSERT INTO carts (id, member_id) VALUES
('a0000000-0000-4000-8000-000000000001', '2a000000-0000-4000-8000-000000000011'),
('a0000000-0000-4000-8000-000000000002', '2a000000-0000-4000-8000-000000000012'),
('a0000000-0000-4000-8000-000000000003', '2a000000-0000-4000-8000-000000000018');

INSERT INTO cart_items (id, cart_id, product_id, added_at) VALUES
('a1000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000001', 'HCM-AK-000017', now() - interval '2 hours'),
('a1000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000001', 'HN-AK-000020',  now() - interval '90 minutes'),
('a1000000-0000-4000-8000-000000000003', 'a0000000-0000-4000-8000-000000000002', 'HN-D-000044',   now() - interval '1 day'),
('a1000000-0000-4000-8000-000000000004', 'a0000000-0000-4000-8000-000000000003', 'HCM-ASM-000006',now() - interval '30 minutes'),
('a1000000-0000-4000-8000-000000000005', 'a0000000-0000-4000-8000-000000000003', 'HCM-QJ-000025', now() - interval '20 minutes');


-- =============================================================================
-- 9. GÓI THÀNH VIÊN VIP ĐÃ MUA + VOUCHER ĐÃ PHÁT
-- =============================================================================
-- member_subscriptions SNAPSHOT toàn bộ hạn mức lúc mua: Admin nâng hạn mức thì áp
-- ngay cho dòng ACTIVE, nhưng HẠ hạn mức chỉ áp từ lần gia hạn sau (người mua đã trả
-- tiền cho mức cũ). Mỗi Member chỉ được 1 dòng ACTIVE - không ràng buộc DB nào ép
-- được (một Member có nhiều dòng EXPIRED), chính findActiveByMemberIdForUpdate giữ.
--
-- 6 dòng = 6 tình huống khác nhau:
--   member1  SILVER -> REPLACED (đổi lên GOLD giữa kỳ, KHÔNG hoàn tiền phần còn lại)
--   member1  GOLD     ACTIVE  - trả bằng VÍ  -> có bút toán MEMBERSHIP_PURCHASE
--   member2  SILVER   ACTIVE  - trả VNPay
--   member3  DIAMOND  ACTIVE  - gói NĂM, trả MoMo
--   member5  SILVER   EXPIRED - hết hạn 20 ngày trước, mất quyền lợi
--   member4  GOLD     PENDING_PAYMENT - mở cổng thanh toán rồi bỏ giữa đường
INSERT INTO member_subscriptions (id, member_id, plan_id, tier, billing_cycle, price_paid,
                                  live_tryon_per_day, live_tryon_per_month, live_tryon_max_seconds,
                                  ai_tryon_per_day, ai_tryon_per_month, featured_listing,
                                  max_pending_consignment_forms, status, started_at, expires_at,
                                  cancelled_at, cancel_reason, expiry_reminder_sent_at,
                                  last_voucher_issued_at, granted_by_admin_id,
                                  created_at, updated_at) VALUES
('b0000000-0000-4000-8000-000000000001', '2a000000-0000-4000-8000-000000000011',
 '6f000000-0000-4000-8000-000000000002', 'SILVER', 'MONTHLY', 29000,
 5, NULL, 10, 15, NULL, true, 10, 'REPLACED',
 now() - interval '40 days', now() - interval '10 days',
 now() - interval '10 days', 'Thay thế bởi gói Thành viên Vàng', NULL,
 now() - interval '40 days', NULL, now() - interval '40 days', now() - interval '10 days'),
('b0000000-0000-4000-8000-000000000002', '2a000000-0000-4000-8000-000000000011',
 '6f000000-0000-4000-8000-000000000003', 'GOLD', 'MONTHLY', 59000,
 10, NULL, 15, 40, NULL, true, 15, 'ACTIVE',
 now() - interval '10 days', now() + interval '20 days',
 NULL, NULL, NULL, now() - interval '10 days', NULL,
 now() - interval '10 days', now() - interval '10 days'),
('b0000000-0000-4000-8000-000000000003', '2a000000-0000-4000-8000-000000000012',
 '6f000000-0000-4000-8000-000000000002', 'SILVER', 'MONTHLY', 29000,
 5, NULL, 10, 15, NULL, true, 10, 'ACTIVE',
 now() - interval '5 days', now() + interval '25 days',
 NULL, NULL, NULL, now() - interval '5 days', NULL,
 now() - interval '5 days', now() - interval '5 days'),
('b0000000-0000-4000-8000-000000000004', '2a000000-0000-4000-8000-000000000013',
 '6f000000-0000-4000-8000-000000000004', 'DIAMOND', 'YEARLY', 990000,
 20, NULL, 20, 60, NULL, true, 30, 'ACTIVE',
 now() - interval '20 days', now() + interval '345 days',
 NULL, NULL, NULL, now() - interval '20 days', NULL,
 now() - interval '20 days', now() - interval '20 days'),
('b0000000-0000-4000-8000-000000000005', '2a000000-0000-4000-8000-000000000015',
 '6f000000-0000-4000-8000-000000000002', 'SILVER', 'MONTHLY', 29000,
 5, NULL, 10, 15, NULL, true, 10, 'EXPIRED',
 now() - interval '50 days', now() - interval '20 days',
 NULL, NULL, now() - interval '23 days', now() - interval '50 days', NULL,
 now() - interval '50 days', now() - interval '20 days'),
('b0000000-0000-4000-8000-000000000006', '2a000000-0000-4000-8000-000000000014',
 '6f000000-0000-4000-8000-000000000003', 'GOLD', 'MONTHLY', 59000,
 10, NULL, 15, 40, NULL, true, 15, 'PENDING_PAYMENT',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL,
 now() - interval '30 minutes', now() - interval '30 minutes');

-- Lần thanh toán gói. Mã tham chiếu cổng dạng SUB-<uuid không gạch>-<millis> để
-- MomoServiceImpl/VnPayServiceImpl nhận ra IPN này là của gói thành viên (không phải
-- đơn hàng) nhờ tiền tố SUB-. Dấu gạch BẮT BUỘC bị bỏ: MoMo chặn orderId quá 50 ký tự
-- và 4 + 32 + 14 đúng bằng 50.
INSERT INTO membership_payments (id, subscription_id, member_id, amount, method, status, paid_at,
                                 momo_order_id, momo_request_id, momo_transaction_id,
                                 vnp_txn_ref, vnp_transaction_no, vnp_create_date, created_at) VALUES
('b1000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000001',
 '2a000000-0000-4000-8000-000000000011', 29000, 'VNPAY', 'SUCCESS', now() - interval '40 days',
 NULL, NULL, NULL, 'SUB-b0000000000040008000000000000001-1762000000001', '14521001',
 to_char(now() - interval '40 days', 'YYYYMMDDHH24MISS'), now() - interval '40 days'),
('b1000000-0000-4000-8000-000000000002', 'b0000000-0000-4000-8000-000000000002',
 '2a000000-0000-4000-8000-000000000011', 59000, 'WALLET', 'SUCCESS', now() - interval '10 days',
 NULL, NULL, NULL, NULL, NULL, NULL, now() - interval '10 days'),
('b1000000-0000-4000-8000-000000000003', 'b0000000-0000-4000-8000-000000000003',
 '2a000000-0000-4000-8000-000000000012', 29000, 'VNPAY', 'SUCCESS', now() - interval '5 days',
 NULL, NULL, NULL, 'SUB-b0000000000040008000000000000003-1762000000003', '14521003',
 to_char(now() - interval '5 days', 'YYYYMMDDHH24MISS'), now() - interval '5 days'),
('b1000000-0000-4000-8000-000000000004', 'b0000000-0000-4000-8000-000000000004',
 '2a000000-0000-4000-8000-000000000013', 990000, 'MOMO', 'SUCCESS', now() - interval '20 days',
 'SUB-b0000000000040008000000000000004-1762000000004',
 'SUB-b0000000000040008000000000000004-1762000000004', '2841523004',
 NULL, NULL, NULL, now() - interval '20 days'),
('b1000000-0000-4000-8000-000000000005', 'b0000000-0000-4000-8000-000000000005',
 '2a000000-0000-4000-8000-000000000015', 29000, 'VNPAY', 'SUCCESS', now() - interval '50 days',
 NULL, NULL, NULL, 'SUB-b0000000000040008000000000000005-1762000000005', '14521005',
 to_char(now() - interval '50 days', 'YYYYMMDDHH24MISS'), now() - interval '50 days'),
-- Bỏ giữa đường: chưa có paid_at, chưa có mã giao dịch của cổng
('b1000000-0000-4000-8000-000000000006', 'b0000000-0000-4000-8000-000000000006',
 '2a000000-0000-4000-8000-000000000014', 59000, 'MOMO', 'PENDING', NULL,
 'SUB-b0000000000040008000000000000006-1762000000006',
 'SUB-b0000000000040008000000000000006-1762000000006', NULL,
 NULL, NULL, NULL, now() - interval '30 minutes');

-- Voucher trong ví người dùng. Mỗi mã SNAPSHOT luật của mẫu lúc phát, nên Admin sửa
-- mẫu về sau KHÔNG đổi các mã đã phát. Mã dạng SHIP-xxxxxx / SALE-xxxxxx để người mua
-- nhìn là biết loại. expires_at rơi vào CUỐI NGÀY thứ 30 (giờ VN), không phải đúng
-- giờ phút lúc phát - nếu không người mua mất gần một ngày hiệu lực.
INSERT INTO member_vouchers (id, code, member_id, voucher_template_id, source_subscription_id,
                             type, discount_kind, discount_value, max_discount_amount,
                             min_order_amount, status, issued_at, expires_at, used_at) VALUES
-- member1 (GOLD): 4 mã/tháng SHIPPING + 2 mã/tháng ORDER_DISCOUNT
('b2000000-0000-4000-8000-000000000001', 'SHIP-7K2M9A', '2a000000-0000-4000-8000-000000000011',
 '70000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000002',
 'SHIPPING', 'FIXED', 15000, NULL, 0, 'UNUSED',
 now() - interval '10 days',
 date_trunc('day', now() - interval '10 days') + interval '31 days' - interval '1 second', NULL),
('b2000000-0000-4000-8000-000000000002', 'SHIP-4T8P2C', '2a000000-0000-4000-8000-000000000011',
 '70000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000002',
 'SHIPPING', 'FIXED', 15000, NULL, 0, 'UNUSED',
 now() - interval '10 days',
 date_trunc('day', now() - interval '10 days') + interval '31 days' - interval '1 second', NULL),
('b2000000-0000-4000-8000-000000000003', 'SALE-3B8Q1Z', '2a000000-0000-4000-8000-000000000011',
 '70000000-0000-4000-8000-000000000002', 'b0000000-0000-4000-8000-000000000002',
 'ORDER_DISCOUNT', 'PERCENT', 0.10, 50000, 0, 'UNUSED',
 now() - interval '10 days',
 date_trunc('day', now() - interval '10 days') + interval '31 days' - interval '1 second', NULL),
-- Mã ĐÃ DÙNG cho đơn OD-2026-000005 (giảm 15.000đ phí ship)
('b2000000-0000-4000-8000-000000000004', 'SHIP-9M5X7D', '2a000000-0000-4000-8000-000000000011',
 '70000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000002',
 'SHIPPING', 'FIXED', 15000, NULL, 0, 'USED',
 now() - interval '10 days',
 date_trunc('day', now() - interval '10 days') + interval '31 days' - interval '1 second',
 now() - interval '7 days'),
-- Mã HẾT HẠN (phát từ gói SILVER cũ đã bị thay thế)
('b2000000-0000-4000-8000-000000000005', 'SALE-2H6K4N', '2a000000-0000-4000-8000-000000000011',
 '70000000-0000-4000-8000-000000000002', 'b0000000-0000-4000-8000-000000000001',
 'ORDER_DISCOUNT', 'PERCENT', 0.10, 50000, 0, 'EXPIRED',
 now() - interval '40 days',
 date_trunc('day', now() - interval '40 days') + interval '31 days' - interval '1 second', NULL),
-- member2 (SILVER): 2 mã SHIPPING + 1 mã ORDER_DISCOUNT
('b2000000-0000-4000-8000-000000000006', 'SHIP-6R3W8F', '2a000000-0000-4000-8000-000000000012',
 '70000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000003',
 'SHIPPING', 'FIXED', 15000, NULL, 0, 'UNUSED',
 now() - interval '5 days',
 date_trunc('day', now() - interval '5 days') + interval '31 days' - interval '1 second', NULL),
('b2000000-0000-4000-8000-000000000007', 'SALE-5J9V2M', '2a000000-0000-4000-8000-000000000012',
 '70000000-0000-4000-8000-000000000002', 'b0000000-0000-4000-8000-000000000003',
 'ORDER_DISCOUNT', 'PERCENT', 0.10, 50000, 0, 'UNUSED',
 now() - interval '5 days',
 date_trunc('day', now() - interval '5 days') + interval '31 days' - interval '1 second', NULL),
-- member3 (DIAMOND): 6 mã SHIPPING + 4 mã ORDER_DISCOUNT mỗi tháng (seed 2 mã đại diện)
('b2000000-0000-4000-8000-000000000008', 'SHIP-8D4L6Q', '2a000000-0000-4000-8000-000000000013',
 '70000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000004',
 'SHIPPING', 'FIXED', 15000, NULL, 0, 'UNUSED',
 now() - interval '20 days',
 date_trunc('day', now() - interval '20 days') + interval '31 days' - interval '1 second', NULL),
('b2000000-0000-4000-8000-000000000009', 'SALE-7C2N5B', '2a000000-0000-4000-8000-000000000013',
 '70000000-0000-4000-8000-000000000002', 'b0000000-0000-4000-8000-000000000004',
 'ORDER_DISCOUNT', 'PERCENT', 0.10, 50000, 0, 'UNUSED',
 now() - interval '20 days',
 date_trunc('day', now() - interval '20 days') + interval '31 days' - interval '1 second', NULL),
-- member5: mã của gói đã hết hạn -> cũng hết hạn theo
('b2000000-0000-4000-8000-000000000010', 'SHIP-1F7G3K', '2a000000-0000-4000-8000-000000000015',
 '70000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000005',
 'SHIPPING', 'FIXED', 15000, NULL, 0, 'EXPIRED',
 now() - interval '50 days',
 date_trunc('day', now() - interval '50 days') + interval '31 days' - interval '1 second', NULL);


-- =============================================================================
-- 10. ĐƠN HÀNG (14 đơn - phủ đủ 8 trạng thái của OrderStatus)
-- =============================================================================
--   OD-2026-000001  COMPLETED        MoMo    - đã đánh giá 5 sao
--   OD-2026-000002  COMPLETED        VNPay   - món ở KỲ 2 nên hoa hồng 15%
--   OD-2026-000003  COMPLETED        VÍ      - KHÔNG có dòng payments (bảng đó chỉ dành cho cổng)
--   OD-2026-000004  COMPLETED        MoMo    - món ở KỲ 2
--   OD-2026-000005  COMPLETED        MoMo    - DÙNG VOUCHER giảm 15.000đ phí ship
--   OD-2026-000006  RETURNED         VNPay   - trả hàng THẤT LẠC (LOST), đã hoàn tiền
--   OD-2026-000007  RETURNED         MoMo    - trả hàng hợp lệ + hàng XUỐNG CẤP -> mở ca đền bù
--   OD-2026-000008  PAID             VNPay   - chờ kho đóng gói (có việc PACKING trong hàng đợi)
--   OD-2026-000009  SHIPPING         MoMo    - có mã vận đơn GHN + 2 sự kiện hành trình
--   OD-2026-000010  CANCELLED        -       - quá hạn 15 phút, tự huỷ, hàng về lại sàn
--   OD-2026-000011  DELIVERED        VNPay   - chờ tự hoàn thành, người mua vừa gửi yêu cầu trả hàng
--   OD-2026-000012  PACKING          VÍ      - kho đang đóng gói
--   OD-2026-000013  PENDING_PAYMENT  -       - chờ thanh toán (demo MoMo/VNPay/ví)
--   OD-2026-000014  RETURNED         VNPay   - trả hàng hợp lệ, Moderator quyết định TIÊU HUỶ
--
-- Ghi chú về tiền:
--   total_amount = subtotal + shipping_fee - discount_amount. Hoàn tiền LUÔN hoàn
--   total_amount (đã trừ giảm giá) - hoàn subtotal + shipping_fee thì người mua lãi
--   tiền mỗi lần đặt rồi huỷ.
--   order_items.price_at_order KHÔNG trừ voucher: người ký gửi nhận tiền trên giá bán
--   đầy đủ, phần giảm giá do nền tảng chịu.
--   payment_method của đơn chưa thanh toán là WALLET vì đó là giá trị mặc định lúc
--   tạo đơn (OrderServiceImpl.createOrder), cổng thật sẽ ghi lại khi IPN về.
INSERT INTO orders (id, order_no, buyer_id, warehouse_id, shipping_address_id,
                    shipping_recipient_name, shipping_phone, shipping_address_detail,
                    shipping_ward_name, shipping_district_name, shipping_province_name,
                    shipping_is_new_address, address_changed_at, scanned_count,
                    subtotal, shipping_fee, discount_amount, total_amount, voucher_id,
                    payment_method, status, ghn_tracking_code, expected_delivery_date,
                    payment_deadline, delivered_at, auto_complete_deadline, return_deadline,
                    cancel_reason, cancelled_at, refund_amount, completed_at, payout_at,
                    service_rating, rated_at, rating_comment, buyer_note,
                    created_at, updated_at) VALUES
-- 1. COMPLETED - MoMo, đã đánh giá
('aa000000-0000-4000-8000-000000000001', 'OD-2026-000001',
 '2a000000-0000-4000-8000-000000000018', '1b000000-0000-4000-8000-000000000001',
 '90000000-0000-4000-8000-000000000005',
 'Bùi Quang Huy', '0900000018', '25 Điện Biên Phủ',
 'Phường Sài Gòn', NULL, 'Hồ Chí Minh', true, NULL, 1,
 520000, 25000, 0, 545000, NULL, 'MOMO', 'COMPLETED', 'GHN00000001',
 now() - interval '42 days', now() - interval '45 days' + interval '15 minutes',
 now() - interval '41 days', now() - interval '38 days', now() - interval '38 days',
 NULL, NULL, NULL, now() - interval '38 days', now() - interval '38 days',
 5, now() - interval '37 days', 'Áo đẹp hơn mong đợi, đóng gói cẩn thận.', NULL,
 now() - interval '45 days', now() - interval '37 days'),
-- 2. COMPLETED - VNPay, món đang ở KỲ 2 (hoa hồng 15%), giao liên tỉnh nên ship 35.000đ
('aa000000-0000-4000-8000-000000000002', 'OD-2026-000002',
 '2a000000-0000-4000-8000-000000000012', '1b000000-0000-4000-8000-000000000001',
 '90000000-0000-4000-8000-000000000003',
 'Trần Thị Bình', '0900000012', '78 Trần Thái Tông, Toà N03',
 'Phường Cầu Giấy', NULL, 'Hà Nội', true, NULL, 1,
 290000, 35000, 0, 325000, NULL, 'VNPAY', 'COMPLETED', 'GHN00000002',
 now() - interval '35 days', now() - interval '38 days' + interval '15 minutes',
 now() - interval '34 days', now() - interval '31 days', now() - interval '31 days',
 NULL, NULL, NULL, now() - interval '31 days', now() - interval '31 days',
 4, now() - interval '30 days', 'Hàng giống ảnh, giao hơi chậm một chút.', NULL,
 now() - interval '38 days', now() - interval '30 days'),
-- 3. COMPLETED - trả bằng VÍ. Chú ý: KHÔNG có dòng trong bảng payments.
('aa000000-0000-4000-8000-000000000003', 'OD-2026-000003',
 '2a000000-0000-4000-8000-000000000011', '1b000000-0000-4000-8000-000000000001',
 '90000000-0000-4000-8000-000000000001',
 'Nguyễn Văn An', '0900000011', '12 Nguyễn Huệ, Căn hộ 8A',
 'Phường Sài Gòn', NULL, 'Hồ Chí Minh', true, NULL, 1,
 450000, 25000, 0, 475000, NULL, 'WALLET', 'COMPLETED', 'GHN00000003',
 now() - interval '17 days', now() - interval '20 days' + interval '15 minutes',
 now() - interval '16 days', now() - interval '13 days', now() - interval '13 days',
 NULL, NULL, NULL, now() - interval '13 days', now() - interval '13 days',
 5, now() - interval '12 days', 'Áo lụa còn mới y như mô tả, rất đáng tiền.',
 'Giao giờ hành chính giúp mình nhé.',
 now() - interval '20 days', now() - interval '12 days'),
-- 4. COMPLETED - MoMo, kho Hà Nội, món ở KỲ 2
('aa000000-0000-4000-8000-000000000004', 'OD-2026-000004',
 '2a000000-0000-4000-8000-000000000012', '1b000000-0000-4000-8000-000000000002',
 '90000000-0000-4000-8000-000000000003',
 'Trần Thị Bình', '0900000012', '78 Trần Thái Tông, Toà N03',
 'Phường Cầu Giấy', NULL, 'Hà Nội', true, NULL, 1,
 320000, 25000, 0, 345000, NULL, 'MOMO', 'COMPLETED', 'GHN00000004',
 now() - interval '9 days', now() - interval '12 days' + interval '15 minutes',
 now() - interval '8 days', now() - interval '5 days', now() - interval '5 days',
 NULL, NULL, NULL, now() - interval '5 days', now() - interval '5 days',
 NULL, NULL, NULL, NULL,
 now() - interval '12 days', now() - interval '5 days'),
-- 5. COMPLETED - CÓ VOUCHER: phí ship 25.000đ, mã SHIPPING giảm 15.000đ -> trả 390.000đ.
--    price_at_order của món vẫn là 380.000đ nguyên giá (người ký gửi không bị trừ).
('aa000000-0000-4000-8000-000000000005', 'OD-2026-000005',
 '2a000000-0000-4000-8000-000000000011', '1b000000-0000-4000-8000-000000000001',
 '90000000-0000-4000-8000-000000000002',
 'Nguyễn Văn An (giao tới công ty)', '0912345678', '45 Lê Lợi, Toà nhà Saigon Centre, tầng 9',
 'Phường Bến Thành', NULL, 'Hồ Chí Minh', true, NULL, 1,
 380000, 25000, 15000, 390000, 'b2000000-0000-4000-8000-000000000004',
 'MOMO', 'COMPLETED', 'GHN00000005',
 now() - interval '4 days', now() - interval '7 days' + interval '15 minutes',
 now() - interval '3 days', now() - interval '1 day' + interval '1 hour', now() - interval '1 day' + interval '1 hour',
 NULL, NULL, NULL, now() - interval '1 day', now() - interval '1 day',
 NULL, NULL, NULL, NULL,
 now() - interval '7 days', now() - interval '1 day'),
-- 6. RETURNED - hàng THẤT LẠC trên đường về kho (F-136). Đã hoàn 335.000đ vào ví người mua,
--    người ký gửi KHÔNG được trả tiền (payout_at = NULL).
('aa000000-0000-4000-8000-000000000006', 'OD-2026-000006',
 '2a000000-0000-4000-8000-000000000011', '1b000000-0000-4000-8000-000000000001',
 '90000000-0000-4000-8000-000000000001',
 'Nguyễn Văn An', '0900000011', '12 Nguyễn Huệ, Căn hộ 8A',
 'Phường Sài Gòn', NULL, 'Hồ Chí Minh', true, NULL, 1,
 310000, 25000, 0, 335000, NULL, 'VNPAY', 'RETURNED', 'GHN00000006',
 now() - interval '15 days', now() - interval '18 days' + interval '15 minutes',
 now() - interval '14 days', now() - interval '11 days', now() - interval '11 days',
 NULL, NULL, 335000, now() - interval '11 days', NULL,
 NULL, NULL, NULL, NULL,
 now() - interval '18 days', now() - interval '6 days'),
-- 7. RETURNED - trả hàng hợp lệ nhưng hàng XUỐNG CẤP so với lúc giao (F-036) -> mở ca đền bù
('aa000000-0000-4000-8000-000000000007', 'OD-2026-000007',
 '2a000000-0000-4000-8000-000000000012', '1b000000-0000-4000-8000-000000000002',
 '90000000-0000-4000-8000-000000000003',
 'Trần Thị Bình', '0900000012', '78 Trần Thái Tông, Toà N03',
 'Phường Cầu Giấy', NULL, 'Hà Nội', true, NULL, 1,
 260000, 25000, 0, 285000, NULL, 'MOMO', 'RETURNED', 'GHN00000007',
 now() - interval '12 days', now() - interval '15 days' + interval '15 minutes',
 now() - interval '11 days', now() - interval '8 days', now() - interval '8 days',
 NULL, NULL, 285000, now() - interval '8 days', NULL,
 NULL, NULL, NULL, NULL,
 now() - interval '15 days', now() - interval '3 days'),
-- 8. PAID - vừa trả xong, chờ kho đóng gói. Có việc PACKING trong hàng đợi Moderator.
('aa000000-0000-4000-8000-000000000008', 'OD-2026-000008',
 '2a000000-0000-4000-8000-000000000018', '1b000000-0000-4000-8000-000000000001',
 '90000000-0000-4000-8000-000000000005',
 'Bùi Quang Huy', '0900000018', '25 Điện Biên Phủ',
 'Phường Sài Gòn', NULL, 'Hồ Chí Minh', true, NULL, 0,
 560000, 25000, 0, 585000, NULL, 'VNPAY', 'PAID', NULL, NULL,
 now() - interval '2 hours' + interval '15 minutes', NULL, NULL, NULL,
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
 now() - interval '2 hours', now() - interval '2 hours' + interval '3 minutes'),
-- 9. SHIPPING - đã tạo vận đơn GHN, đang trên đường giao
('aa000000-0000-4000-8000-000000000009', 'OD-2026-000009',
 '2a000000-0000-4000-8000-000000000012', '1b000000-0000-4000-8000-000000000002',
 '90000000-0000-4000-8000-000000000003',
 'Trần Thị Bình', '0900000012', '78 Trần Thái Tông, Toà N03',
 'Phường Cầu Giấy', NULL, 'Hà Nội', true, NULL, 1,
 370000, 25000, 0, 395000, NULL, 'MOMO', 'SHIPPING', 'GHN00000009',
 now() + interval '1 day', now() - interval '3 days' + interval '15 minutes',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
 now() - interval '3 days', now() - interval '18 hours'),
-- 10. CANCELLED - quá hạn thanh toán 15 phút, job tự huỷ và NHẢ HÀNG VỀ SÀN.
--     refund_amount = NULL: đơn chưa thanh toán thì không hoàn đồng nào.
('aa000000-0000-4000-8000-000000000010', 'OD-2026-000010',
 '2a000000-0000-4000-8000-000000000015', '1b000000-0000-4000-8000-000000000001',
 '90000000-0000-4000-8000-000000000004',
 'Tạ Thị Ngọc Anh', '0900000015', '102 Nguyễn Thị Thập, Chung cư Era Town',
 'Phường Tân Mỹ', NULL, 'Hồ Chí Minh', true, NULL, 0,
 420000, 25000, 0, 445000, NULL, 'WALLET', 'CANCELLED', NULL, NULL,
 now() - interval '4 days' + interval '15 minutes', NULL, NULL, NULL,
 'PAYMENT_EXPIRED', now() - interval '4 days' + interval '16 minutes', NULL, NULL, NULL,
 NULL, NULL, NULL, NULL,
 now() - interval '4 days', now() - interval '4 days' + interval '16 minutes'),
-- 11. DELIVERED - chờ tự hoàn thành sau 3 ngày; người mua vừa gửi yêu cầu TRẢ HÀNG (đang PENDING)
('aa000000-0000-4000-8000-000000000011', 'OD-2026-000011',
 '2a000000-0000-4000-8000-000000000018', '1b000000-0000-4000-8000-000000000002',
 '90000000-0000-4000-8000-000000000006',
 'Bùi Quang Huy (nhà bố mẹ)', '0900000118', '9 Nguyễn Chí Thanh',
 'Phường Cửa Nam', NULL, 'Hà Nội', true, NULL, 1,
 240000, 25000, 0, 265000, NULL, 'VNPAY', 'DELIVERED', 'GHN00000011',
 now() - interval '2 days', now() - interval '5 days' + interval '15 minutes',
 now() - interval '1 day', now() + interval '2 days', now() + interval '2 days',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
 now() - interval '5 days', now() - interval '12 hours'),
-- 12. PACKING - kho đang quét mã đóng gói, trả bằng VÍ
('aa000000-0000-4000-8000-000000000012', 'OD-2026-000012',
 '2a000000-0000-4000-8000-000000000011', '1b000000-0000-4000-8000-000000000001',
 '90000000-0000-4000-8000-000000000001',
 'Nguyễn Văn An', '0900000011', '12 Nguyễn Huệ, Căn hộ 8A',
 'Phường Sài Gòn', NULL, 'Hồ Chí Minh', true, NULL, 0,
 120000, 25000, 0, 145000, NULL, 'WALLET', 'PACKING', NULL, NULL,
 now() - interval '5 hours' + interval '15 minutes', NULL, NULL, NULL,
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
 now() - interval '5 hours', now() - interval '4 hours'),
-- 13. PENDING_PAYMENT - dùng để demo lại luồng thanh toán từ đầu:
--       POST /api/momo/create   {"orderIds":["aa000000-0000-4000-8000-000000000013"], ...}
--       POST /api/vnpay/create  {"orderIds":["aa000000-0000-4000-8000-000000000013"], ...}
--       POST /orders/pay        {"orderIds":[...], "method":"WALLET"}
--     payment_deadline cố ý để +1 ngày (thay vì 15 phút như cấu hình) để job tự huỷ
--     KHÔNG xoá mất tình huống này giữa buổi demo.
('aa000000-0000-4000-8000-000000000013', 'OD-2026-000013',
 '2a000000-0000-4000-8000-000000000018', '1b000000-0000-4000-8000-000000000002',
 '90000000-0000-4000-8000-000000000006',
 'Bùi Quang Huy (nhà bố mẹ)', '0900000118', '9 Nguyễn Chí Thanh',
 'Phường Cửa Nam', NULL, 'Hà Nội', true, NULL, 0,
 200000, 25000, 0, 225000, NULL, 'WALLET', 'PENDING_PAYMENT', NULL, NULL,
 now() + interval '1 day', NULL, NULL, NULL,
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
 now() - interval '2 minutes', now() - interval '2 minutes'),
-- 14. RETURNED - trả hàng hợp lệ, hàng hỏng nặng -> Moderator chọn TIÊU HUỶ (F-133)
('aa000000-0000-4000-8000-000000000014', 'OD-2026-000014',
 '2a000000-0000-4000-8000-000000000018', '1b000000-0000-4000-8000-000000000001',
 '90000000-0000-4000-8000-000000000005',
 'Bùi Quang Huy', '0900000018', '25 Điện Biên Phủ',
 'Phường Sài Gòn', NULL, 'Hồ Chí Minh', true, NULL, 1,
 90000, 25000, 0, 115000, NULL, 'VNPAY', 'RETURNED', 'GHN00000014',
 now() - interval '17 days', now() - interval '20 days' + interval '15 minutes',
 now() - interval '16 days', now() - interval '13 days', now() - interval '13 days',
 NULL, NULL, 115000, now() - interval '13 days', NULL,
 NULL, NULL, NULL, NULL,
 now() - interval '20 days', now() - interval '5 days');

-- Ảnh người mua gửi kèm khi đánh giá đơn OD-2026-000003
INSERT INTO order_rating_image_urls (order_id, position, image_url) VALUES
('aa000000-0000-4000-8000-000000000003', 0,
 'https://cdn.dummyjson.com/product-images/mens-shirts/blue-&-black-check-shirt/2.webp');

-- Dòng hàng của từng đơn.
--   commission_rate_applied + term_applied là SNAPSHOT kỳ ký gửi lúc đặt hàng:
--   kỳ 1 = 10%, kỳ 2 = 15%, kỳ 3 = 20%. Tiền trả người ký gửi = price_at_order * (1 - rate).
--   tax_rate_applied = 0 theo cấu hình app.order.tax-rate.
--   scanned = đã quét mã lúc đóng gói hay chưa.
INSERT INTO order_items (id, order_id, product_id, price_at_order, commission_rate_applied,
                         tax_rate_applied, term_applied, scanned) VALUES
('ab000000-0000-4000-8000-000000000001', 'aa000000-0000-4000-8000-000000000001', 'HCM-AK-000051',  520000, 0.10, 0, 1, true),
('ab000000-0000-4000-8000-000000000002', 'aa000000-0000-4000-8000-000000000002', 'HCM-AT-000052',  290000, 0.15, 0, 2, true),
('ab000000-0000-4000-8000-000000000003', 'aa000000-0000-4000-8000-000000000003', 'HCM-ASM-000053', 450000, 0.10, 0, 1, true),
('ab000000-0000-4000-8000-000000000004', 'aa000000-0000-4000-8000-000000000004', 'HN-QJ-000054',   320000, 0.15, 0, 2, true),
('ab000000-0000-4000-8000-000000000005', 'aa000000-0000-4000-8000-000000000005', 'HCM-D-000055',   380000, 0.10, 0, 1, true),
('ab000000-0000-4000-8000-000000000006', 'aa000000-0000-4000-8000-000000000006', 'HCM-QT-000056',  310000, 0.10, 0, 1, true),
('ab000000-0000-4000-8000-000000000007', 'aa000000-0000-4000-8000-000000000007', 'HN-CV-000057',   260000, 0.10, 0, 1, true),
('ab000000-0000-4000-8000-000000000008', 'aa000000-0000-4000-8000-000000000008', 'HCM-AB-000058',  560000, 0.10, 0, 1, false),
('ab000000-0000-4000-8000-000000000009', 'aa000000-0000-4000-8000-000000000009', 'HN-VL-000059',   370000, 0.10, 0, 1, true),
('ab000000-0000-4000-8000-000000000010', 'aa000000-0000-4000-8000-000000000010', 'HCM-AK-000062',  420000, 0.10, 0, 1, false),
('ab000000-0000-4000-8000-000000000011', 'aa000000-0000-4000-8000-000000000011', 'HN-AL-000060',   240000, 0.10, 0, 1, true),
('ab000000-0000-4000-8000-000000000012', 'aa000000-0000-4000-8000-000000000012', 'HCM-AT-000061',  120000, 0.10, 0, 1, false),
('ab000000-0000-4000-8000-000000000013', 'aa000000-0000-4000-8000-000000000013', 'HN-AP-000071',   200000, 0.10, 0, 1, false),
('ab000000-0000-4000-8000-000000000014', 'aa000000-0000-4000-8000-000000000014', 'HCM-AT-000068',   90000, 0.10, 0, 1, true);

-- Giao dịch cổng thanh toán. Bảng payments CHỈ dành cho MoMo/VNPay - đơn trả bằng ví
-- (OD-2026-000003 và OD-2026-000012) KHÔNG có dòng nào ở đây.
-- momo_order_id / vnp_txn_ref KHÔNG unique: một lần thanh toán cho giỏ nhiều kho sẽ tạo
-- nhiều đơn dùng chung một mã giao dịch (xem scripts/drop-payment-gateway-ref-unique.sql).
INSERT INTO payments (id, order_id, amount, method, status, paid_at,
                      momo_order_id, momo_request_id, momo_transaction_id,
                      vnp_txn_ref, vnp_transaction_no, vnp_create_date) VALUES
('ac000000-0000-4000-8000-000000000001', 'aa000000-0000-4000-8000-000000000001', 545000, 'MOMO', 'SUCCESS',
 now() - interval '45 days' + interval '4 minutes',
 'OD-2026-000001-1761000000001', 'OD-2026-000001-1761000000001', '2841001001', NULL, NULL, NULL),
('ac000000-0000-4000-8000-000000000002', 'aa000000-0000-4000-8000-000000000002', 325000, 'VNPAY', 'SUCCESS',
 now() - interval '38 days' + interval '3 minutes',
 NULL, NULL, NULL, 'OD-2026-000002-1761000000002', '14001002',
 to_char(now() - interval '38 days', 'YYYYMMDDHH24MISS')),
('ac000000-0000-4000-8000-000000000004', 'aa000000-0000-4000-8000-000000000004', 345000, 'MOMO', 'SUCCESS',
 now() - interval '12 days' + interval '2 minutes',
 'OD-2026-000004-1761000000004', 'OD-2026-000004-1761000000004', '2841001004', NULL, NULL, NULL),
('ac000000-0000-4000-8000-000000000005', 'aa000000-0000-4000-8000-000000000005', 390000, 'MOMO', 'SUCCESS',
 now() - interval '7 days' + interval '5 minutes',
 'OD-2026-000005-1761000000005', 'OD-2026-000005-1761000000005', '2841001005', NULL, NULL, NULL),
('ac000000-0000-4000-8000-000000000006', 'aa000000-0000-4000-8000-000000000006', 335000, 'VNPAY', 'SUCCESS',
 now() - interval '18 days' + interval '3 minutes',
 NULL, NULL, NULL, 'OD-2026-000006-1761000000006', '14001006',
 to_char(now() - interval '18 days', 'YYYYMMDDHH24MISS')),
('ac000000-0000-4000-8000-000000000007', 'aa000000-0000-4000-8000-000000000007', 285000, 'MOMO', 'SUCCESS',
 now() - interval '15 days' + interval '2 minutes',
 'OD-2026-000007-1761000000007', 'OD-2026-000007-1761000000007', '2841001007', NULL, NULL, NULL),
('ac000000-0000-4000-8000-000000000008', 'aa000000-0000-4000-8000-000000000008', 585000, 'VNPAY', 'SUCCESS',
 now() - interval '2 hours' + interval '3 minutes',
 NULL, NULL, NULL, 'OD-2026-000008-1761000000008', '14001008',
 to_char(now() - interval '2 hours', 'YYYYMMDDHH24MISS')),
('ac000000-0000-4000-8000-000000000009', 'aa000000-0000-4000-8000-000000000009', 395000, 'MOMO', 'SUCCESS',
 now() - interval '3 days' + interval '2 minutes',
 'OD-2026-000009-1761000000009', 'OD-2026-000009-1761000000009', '2841001009', NULL, NULL, NULL),
-- Đơn quá hạn: mở cổng rồi không trả -> dòng payments đứng mãi ở PENDING
('ac000000-0000-4000-8000-000000000010', 'aa000000-0000-4000-8000-000000000010', 445000, 'MOMO', 'PENDING',
 NULL, 'OD-2026-000010-1761000000010', 'OD-2026-000010-1761000000010', NULL, NULL, NULL, NULL),
('ac000000-0000-4000-8000-000000000011', 'aa000000-0000-4000-8000-000000000011', 265000, 'VNPAY', 'SUCCESS',
 now() - interval '5 days' + interval '4 minutes',
 NULL, NULL, NULL, 'OD-2026-000011-1761000000011', '14001011',
 to_char(now() - interval '5 days', 'YYYYMMDDHH24MISS')),
('ac000000-0000-4000-8000-000000000014', 'aa000000-0000-4000-8000-000000000014', 115000, 'VNPAY', 'SUCCESS',
 now() - interval '20 days' + interval '3 minutes',
 NULL, NULL, NULL, 'OD-2026-000014-1761000000014', '14001014',
 to_char(now() - interval '20 days', 'YYYYMMDDHH24MISS'));

-- Hành trình vận đơn GHN (webhook POST /webhooks/ghn ghi vào đây).
-- Sự kiện MỚI NHẤT của OD-2026-000009 là 'delivering' (bình thường) nên
-- GET /orders/{id}/tracking trả hasIssue = false. Đổi thành 'delivery_fail' để test
-- nhánh có sự cố.
INSERT INTO order_tracking_events (id, order_id, ghn_status, description, location, occurred_at, created_at) VALUES
('ad000000-0000-4000-8000-000000000001', 'aa000000-0000-4000-8000-000000000009', 'ready_to_pick',
 'GHN đã nhận thông tin đơn, chờ lấy hàng', 'Kho ReHome Hà Nội',
 now() - interval '2 days 20 hours', now() - interval '2 days 20 hours'),
('ad000000-0000-4000-8000-000000000002', 'aa000000-0000-4000-8000-000000000009', 'delivering',
 'Đang giao cho người nhận', 'Bưu cục Cầu Giấy - Hà Nội',
 now() - interval '18 hours', now() - interval '18 hours'),
('ad000000-0000-4000-8000-000000000003', 'aa000000-0000-4000-8000-000000000011', 'ready_to_pick',
 'GHN đã nhận thông tin đơn, chờ lấy hàng', 'Kho ReHome Hà Nội',
 now() - interval '4 days 20 hours', now() - interval '4 days 20 hours'),
('ad000000-0000-4000-8000-000000000004', 'aa000000-0000-4000-8000-000000000011', 'delivering',
 'Đang giao cho người nhận', 'Bưu cục Cửa Nam - Hà Nội',
 now() - interval '1 day 4 hours', now() - interval '1 day 4 hours'),
('ad000000-0000-4000-8000-000000000005', 'aa000000-0000-4000-8000-000000000011', 'delivered',
 'Giao hàng thành công', 'Phường Cửa Nam - Hà Nội',
 now() - interval '1 day', now() - interval '1 day');


-- =============================================================================
-- 11. TRẢ HÀNG (F-067 -> F-071, F-132/F-136) + CA ĐỀN BÙ HÀNG XUỐNG CẤP (F-036)
-- =============================================================================
-- 4 yêu cầu = 4 kết luận khác nhau:
--   LOST    - hàng thất lạc trên đường về kho -> hoàn tiền người mua, món về RETURNED
--   VALID   - hợp lệ + hàng XUỐNG CẤP -> hoàn tiền + mở ca đền bù cho người ký gửi
--   VALID   - hợp lệ, hàng hỏng nặng -> hoàn tiền + Moderator chọn TIÊU HUỶ món
--   PENDING - vừa gửi, chờ Moderator duyệt (sinh việc RETURN trong hàng đợi)
INSERT INTO return_requests (id, order_id, reason, status, counterfeit_suspected,
                             counterfeit_conclusion, degraded, matches_original, condition_note,
                             item_action, invalid_return_choice, ghn_return_tracking_code,
                             handover_deadline, pickup_deadline, picked_up_at,
                             resolution_note, moderator_id, second_moderator_id,
                             created_at, resolved_at) VALUES
-- 1. THẤT LẠC (LOST): GHN mất kiện trên đường về kho, lỗi không thuộc người mua
('c0000000-0000-4000-8000-000000000001', 'aa000000-0000-4000-8000-000000000006',
 'Quần không đúng size như mô tả, mình đo lại thì rộng hơn 4cm so với thông tin đăng bán.',
 'LOST', false, NULL, NULL, NULL, NULL, NULL, NULL,
 'GHNRT000006', now() - interval '12 days' + interval '3 days', NULL, NULL,
 'GHN xác nhận kiện hàng trả về bị thất lạc, không tìm lại được. Hoàn tiền toàn bộ cho '
 'người mua theo F-136, món hàng chuyển trạng thái RETURNED chờ xử lý.',
 '2a000000-0000-4000-8000-000000000002', NULL,
 now() - interval '12 days', now() - interval '6 days'),
-- 2. HỢP LỆ + XUỐNG CẤP: hàng đúng món gốc nhưng tình trạng kém hơn lúc giao -> mở ca đền bù
('c0000000-0000-4000-8000-000000000002', 'aa000000-0000-4000-8000-000000000007',
 'Chân váy bị tuột chỉ ở lưng sau 1 lần mặc thử, không giống tình trạng lúc nhận hàng.',
 'VALID', false, NULL, true, true,
 'Đúng sản phẩm gốc (khớp SKU và ảnh ký gửi) nhưng đường chỉ lưng váy đã tuột khoảng 5cm, '
 'xuống cấp so với lúc giao cho người mua.',
 NULL, NULL, 'GHNRT000007', now() - interval '10 days' + interval '3 days', NULL, NULL,
 'Kết luận trả hàng hợp lệ, hoàn tiền người mua. Hàng xuống cấp nên mở ca đền bù cho '
 'người ký gửi theo F-036.',
 '2a000000-0000-4000-8000-000000000004', NULL,
 now() - interval '10 days', now() - interval '3 days'),
-- 3. HỢP LỆ, hàng hỏng nặng không bán lại được -> Moderator chọn TIÊU HUỶ (item_action = DISCARD)
('c0000000-0000-4000-8000-000000000003', 'aa000000-0000-4000-8000-000000000014',
 'Áo bị mục vải ở vai, mô tả ghi còn 85% nhưng thực tế không mặc được.',
 'VALID', false, NULL, false, true,
 'Vải vai đã mục, không còn giá trị thương mại. Không thể đăng bán lại.',
 'DISCARD', NULL, 'GHNRT000014', now() - interval '12 days' + interval '3 days', NULL, NULL,
 'Trả hàng hợp lệ, hoàn tiền người mua. Món hàng tiêu huỷ theo F-133, người ký gửi được '
 'thông báo và không bị tính phí.',
 '2a000000-0000-4000-8000-000000000003', NULL,
 now() - interval '12 days', now() - interval '5 days'),
-- 4. CHỜ DUYỆT: người mua vừa gửi yêu cầu trên đơn DELIVERED còn trong hạn 3 ngày
('c0000000-0000-4000-8000-000000000004', 'aa000000-0000-4000-8000-000000000011',
 'Áo len bị xù lông nhiều hơn ảnh, cổ áo có vết loang màu nhỏ.',
 'PENDING', false, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
 now() + interval '3 days', NULL, NULL, NULL, NULL, NULL,
 now() - interval '12 hours', NULL);

-- Ảnh người mua / Moderator gửi kèm yêu cầu trả hàng
INSERT INTO return_request_photos (id, return_request_id, image_url, source) VALUES
('c0100000-0000-4000-8000-000000000001', 'c0000000-0000-4000-8000-000000000002',
 'https://cdn.dummyjson.com/product-images/womens-dresses/corset-with-black-skirt/2.webp', 'MEMBER'),
('c0100000-0000-4000-8000-000000000002', 'c0000000-0000-4000-8000-000000000002',
 'https://cdn.dummyjson.com/product-images/womens-dresses/corset-with-black-skirt/3.webp', 'MODERATOR'),
('c0100000-0000-4000-8000-000000000003', 'c0000000-0000-4000-8000-000000000003',
 'https://fakestoreapi.com/img/71-3HjGNDUL._AC_SY879._SX._UX._SY._UY_t.png', 'MEMBER'),
('c0100000-0000-4000-8000-000000000004', 'c0000000-0000-4000-8000-000000000004',
 'https://fakestoreapi.com/img/51Y5NI-I5jL._AC_UX679_t.png', 'MEMBER');

-- Ca đền bù hàng xuống cấp: người ký gửi đã chọn FULL_COMPENSATION và ĐÃ ĐƯỢC TRẢ TIỀN.
-- compensation_amount = price_at_order * (1 - hoa hồng kỳ áp dụng) = 260.000 * 0.9 = 234.000đ,
-- snapshot lúc mở ca. Chọn FULL_COMPENSATION nghĩa là nhường món cho nền tảng, nên
-- sản phẩm HN-CV-000057 chuyển sang PLATFORM_OWNED.
INSERT INTO degraded_item_cases (id, product_id, return_request_id, moderator_id, choice,
                                 compensation_amount, paid_amount, decision_deadline, decided_at,
                                 picked_up_at, item_donated, last_reminder_sent_at, notes,
                                 version, created_at) VALUES
('c1000000-0000-4000-8000-000000000001', 'HN-CV-000057', 'c0000000-0000-4000-8000-000000000002',
 '2a000000-0000-4000-8000-000000000004', 'FULL_COMPENSATION', 234000, 234000,
 now() - interval '3 days' + interval '30 days', now() - interval '2 days', NULL, false, NULL,
 'Người ký gửi chọn nhận đền bù toàn bộ, nhường món hàng cho nền tảng thanh lý.',
 0, now() - interval '3 days');

INSERT INTO degraded_item_case_photos (degraded_item_case_id, position, image_url) VALUES
('c1000000-0000-4000-8000-000000000001', 0,
 'https://cdn.dummyjson.com/product-images/womens-dresses/corset-with-black-skirt/3.webp');


-- =============================================================================
-- 12. RÚT HÀNG VỀ (F-032/F-034) - RÚT TIỀN (ví -> ngân hàng) - QUYẾT ĐỊNH CUỐI KỲ (F-029)
-- =============================================================================
-- 12.1 YÊU CẦU RÚT HÀNG. Sản phẩm sang WITHDRAWN NGAY khi gửi yêu cầu (ngừng bán),
-- chứ không chờ tới lúc ra quầy nhận hàng. Phí rút hàng = withdrawal_fee trên biên
-- nhận (50.000đ), MIỄN PHÍ nếu rút trong 7 ngày đầu mỗi kỳ.
INSERT INTO item_withdrawal_requests (id, product_id, member_id, moderator_id, status,
                                      fee_charged, fee_payment_method, fee_paid_at,
                                      fee_payment_deadline, pickup_deadline, reviewed_at,
                                      completed_at, reject_reason, version,
                                      momo_order_id, momo_request_id,
                                      vnp_txn_ref, vnp_transaction_no, vnp_create_date,
                                      created_at) VALUES
-- Chờ Moderator duyệt -> có việc WITHDRAWAL trong hàng đợi. Chưa duyệt nên chưa có hạn nào.
('c2000000-0000-4000-8000-000000000001', 'HCM-AK2-000072', '2a000000-0000-4000-8000-000000000016',
 NULL, 'PENDING_APPROVAL', 50000, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0,
 NULL, NULL, NULL, NULL, NULL, now() - interval '1 day'),
-- Đã duyệt + ĐÃ TRẢ PHÍ qua MoMo, đang chờ Member ra kho nhận hàng (hạn 7 ngày)
('c2000000-0000-4000-8000-000000000002', 'HN-QT-000073', '2a000000-0000-4000-8000-000000000019',
 '2a000000-0000-4000-8000-000000000004', 'PENDING_PICKUP', 50000, 'MOMO',
 now() - interval '2 days' + interval '6 minutes', now() - interval '2 days' + interval '15 minutes',
 now() - interval '2 days' + interval '7 days', now() - interval '2 days', NULL, NULL, 0,
 'WD-c2000000000040008000000000000002-1762000000002',
 'WD-c2000000000040008000000000000002-1762000000002', NULL, NULL, NULL,
 now() - interval '3 days');

-- 12.2 YÊU CẦU RÚT TIỀN TỪ VÍ (tối thiểu 50.000đ, tối đa 3 lần/ngày)
-- RT-00001 đã chi xong -> có bút toán WITHDRAWAL trừ ví member3.
-- RT-00002 còn PENDING -> tiền bị KHOÁ trong ví member4 (locked_balance), chưa trừ số dư:
-- bước 14 sẽ set locked_balance = 200.000đ, nên member4 chỉ tiêu được 72.000đ còn lại.
INSERT INTO payout_requests (id, wallet_id, payout_no, amount, status, failure_reason,
                             processed_at, created_at) VALUES
('c3000000-0000-4000-8000-000000000001', '3c000000-0000-4000-8000-000000000013', 'RT-00001',
 300000, 'SUCCESS', NULL, now() - interval '5 days', now() - interval '6 days'),
('c3000000-0000-4000-8000-000000000002', '3c000000-0000-4000-8000-000000000014', 'RT-00002',
 200000, 'PENDING', NULL, NULL, now() - interval '2 days');

-- 12.3 QUYẾT ĐỊNH CUỐI KỲ - hết 90 ngày hàng bị gỡ khỏi sàn, Member có 30 ngày để chọn
-- lấy về / ký gửi lại / tặng từ thiện; quá hạn thành hàng vô thừa nhận.
-- 5 dòng = 5 bước khác nhau của luồng này.
INSERT INTO end_of_term_decisions (id, product_id, decision, new_receipt_id, decided_at,
                                   deadline, taken_back_at, version) VALUES
-- Chờ Member chọn (decision = NULL) - test GET /products/mine/{id} rồi POST .../decide
('c4000000-0000-4000-8000-000000000001', 'HCM-QK-000063', NULL, NULL, NULL,
 now() - interval '5 days' + interval '30 days', NULL, 0),
-- Member chọn KÝ GỬI LẠI, Moderator CHƯA chốt giá/điều khoản mới (new_receipt_id còn NULL)
-- -> test GET /end-of-term-decisions/pending-pricing rồi POST .../re-consign-terms
('c4000000-0000-4000-8000-000000000002', 'HN-D-000064', 'RE_CONSIGN', NULL,
 now() - interval '2 days', now() - interval '8 days' + interval '30 days', NULL, 0),
-- Member chọn LẤY VỀ, hàng đã WITHDRAWN nhưng chưa ra quầy nhận (taken_back_at = NULL)
-- -> test POST /counter/handover với context END_OF_TERM_TAKE_BACK
('c4000000-0000-4000-8000-000000000003', 'HCM-ASM-000065', 'TAKE_BACK', NULL,
 now() - interval '3 days', now() - interval '10 days' + interval '30 days', NULL, 0),
-- Member KHÔNG phản hồi trong 30 ngày -> hàng vô thừa nhận (decided_at = NULL vì Member
-- không tự chọn, job đánh dấu)
('c4000000-0000-4000-8000-000000000004', 'HN-AT-000066', 'UNCLAIMED', NULL, NULL,
 now() - interval '45 days' + interval '30 days', NULL, 0),
-- Member chọn TẶNG LẠI CHO TỪ THIỆN -> món thành DONATED và vào luồng quyên góp
('c4000000-0000-4000-8000-000000000005', 'HCM-QS-000067', 'DONATE', NULL,
 now() - interval '10 days', now() - interval '15 days' + interval '30 days', NULL, 0);


-- =============================================================================
-- 13. SỔ VÍ VÀ QUỸ NỀN TẢNG
-- =============================================================================
-- Nguyên tắc F-039: MỖI bút toán ví Member phải có MỘT bút toán đối ứng ở quỹ nền tảng,
-- ngược dấu (counterpart_transaction_id trỏ về dòng Member) để sổ sách luôn cân.
--
-- 3 loại dòng CHỈ-THỐNG-KÊ, không có dòng Member đối ứng và KHÔNG đổi số dư quỹ:
--   COMMISSION_REVENUE  - hoa hồng nền tảng giữ lại của từng món
--   MEMBERSHIP_REVENUE  - tiền bán gói VIP thu qua CỔNG (trả bằng ví thì ghi
--                         MEMBERSHIP_PURCHASE và có đối ứng thật)
--   VOUCHER_DISCOUNT    - chi phí khuyến mãi (âm), tiền này chưa bao giờ vào quỹ
--
-- transaction_no và balance_after KHÔNG gõ tay: bước 14 tính lại theo thứ tự thời gian
-- (WT-2026-000001, 000002, ...) và cộng dồn số dư, nên sổ luôn khớp với ví.
--
-- /!\ Số dư quỹ nền tảng sẽ ra số ÂM, và đó là ĐÚNG với mô hình hiện tại: tiền người
-- mua trả qua MoMo/VNPay KHÔNG sinh bút toán quỹ nào (applyGatewayPaymentSuccess chỉ
-- đổi trạng thái đơn), trong khi tiền trả cho người ký gửi lại trừ quỹ. Nói cách khác
-- platform_funds chỉ phản ánh phần tiền CHẠY QUA VÍ nội bộ, không phải tổng tiền mặt
-- của nền tảng. Muốn số này là tổng thu chi thật thì phải ghi thêm bút toán quỹ lúc
-- cổng báo thanh toán thành công (ngoài phạm vi file seed).
INSERT INTO wallet_transactions (id, transaction_no, wallet_type, wallet_id, type, amount,
                                 balance_after, status, note, counterpart_transaction_id,
                                 related_order_id, related_order_item_id, related_payout_request_id,
                                 related_degraded_item_case_id, related_item_withdrawal_request_id,
                                 created_at) VALUES
-- [1] Trả tiền người ký gửi của OD-2026-000001: 520.000 * (1 - 10%) = 468.000đ
('e0000000-0000-4000-8000-000000000001', NULL, 'MEMBER', '3c000000-0000-4000-8000-000000000011',
 'ORDER_PAYOUT', 468000, NULL, 'SUCCESS', 'Thanh toán đơn hàng OD-2026-000001', NULL,
 'aa000000-0000-4000-8000-000000000001', 'ab000000-0000-4000-8000-000000000001', NULL, NULL, NULL,
 now() - interval '38 days'),
('e0000000-0000-4000-8000-000000000002', NULL, 'PLATFORM', '80000000-0000-4000-8000-000000000001',
 'ORDER_PAYOUT', -468000, NULL, 'SUCCESS', 'Thanh toán đơn hàng OD-2026-000001',
 'e0000000-0000-4000-8000-000000000001',
 'aa000000-0000-4000-8000-000000000001', 'ab000000-0000-4000-8000-000000000001', NULL, NULL, NULL,
 now() - interval '38 days'),
('e0000000-0000-4000-8000-000000000003', NULL, 'PLATFORM', '80000000-0000-4000-8000-000000000001',
 'COMMISSION_REVENUE', 52000, NULL, 'SUCCESS', 'Hoa hồng đơn OD-2026-000001', NULL,
 'aa000000-0000-4000-8000-000000000001', 'ab000000-0000-4000-8000-000000000001', NULL, NULL, NULL,
 now() - interval '38 days'),
-- [2] OD-2026-000002: món ở KỲ 2 nên hoa hồng 15% -> 290.000 * 0.85 = 246.500đ
('e0000000-0000-4000-8000-000000000004', NULL, 'MEMBER', '3c000000-0000-4000-8000-000000000011',
 'ORDER_PAYOUT', 246500, NULL, 'SUCCESS', 'Thanh toán đơn hàng OD-2026-000002', NULL,
 'aa000000-0000-4000-8000-000000000002', 'ab000000-0000-4000-8000-000000000002', NULL, NULL, NULL,
 now() - interval '31 days'),
('e0000000-0000-4000-8000-000000000005', NULL, 'PLATFORM', '80000000-0000-4000-8000-000000000001',
 'ORDER_PAYOUT', -246500, NULL, 'SUCCESS', 'Thanh toán đơn hàng OD-2026-000002',
 'e0000000-0000-4000-8000-000000000004',
 'aa000000-0000-4000-8000-000000000002', 'ab000000-0000-4000-8000-000000000002', NULL, NULL, NULL,
 now() - interval '31 days'),
('e0000000-0000-4000-8000-000000000006', NULL, 'PLATFORM', '80000000-0000-4000-8000-000000000001',
 'COMMISSION_REVENUE', 43500, NULL, 'SUCCESS', 'Hoa hồng đơn OD-2026-000002', NULL,
 'aa000000-0000-4000-8000-000000000002', 'ab000000-0000-4000-8000-000000000002', NULL, NULL, NULL,
 now() - interval '31 days'),
-- [3] member1 TRẢ BẰNG VÍ cho OD-2026-000003 (475.000đ)
('e0000000-0000-4000-8000-000000000007', NULL, 'MEMBER', '3c000000-0000-4000-8000-000000000011',
 'ORDER_PAYMENT', -475000, NULL, 'SUCCESS', 'Thanh toán bằng ví cho đơn OD-2026-000003', NULL,
 'aa000000-0000-4000-8000-000000000003', NULL, NULL, NULL, NULL,
 now() - interval '20 days'),
('e0000000-0000-4000-8000-000000000008', NULL, 'PLATFORM', '80000000-0000-4000-8000-000000000001',
 'ORDER_PAYMENT', 475000, NULL, 'SUCCESS', 'Thanh toán bằng ví cho đơn OD-2026-000003',
 'e0000000-0000-4000-8000-000000000007',
 'aa000000-0000-4000-8000-000000000003', NULL, NULL, NULL, NULL,
 now() - interval '20 days'),
-- [4] Trả tiền người ký gửi của OD-2026-000003: 450.000 * 0.9 = 405.000đ cho member3
('e0000000-0000-4000-8000-000000000009', NULL, 'MEMBER', '3c000000-0000-4000-8000-000000000013',
 'ORDER_PAYOUT', 405000, NULL, 'SUCCESS', 'Thanh toán đơn hàng OD-2026-000003', NULL,
 'aa000000-0000-4000-8000-000000000003', 'ab000000-0000-4000-8000-000000000003', NULL, NULL, NULL,
 now() - interval '13 days'),
('e0000000-0000-4000-8000-000000000010', NULL, 'PLATFORM', '80000000-0000-4000-8000-000000000001',
 'ORDER_PAYOUT', -405000, NULL, 'SUCCESS', 'Thanh toán đơn hàng OD-2026-000003',
 'e0000000-0000-4000-8000-000000000009',
 'aa000000-0000-4000-8000-000000000003', 'ab000000-0000-4000-8000-000000000003', NULL, NULL, NULL,
 now() - interval '13 days'),
('e0000000-0000-4000-8000-000000000011', NULL, 'PLATFORM', '80000000-0000-4000-8000-000000000001',
 'COMMISSION_REVENUE', 45000, NULL, 'SUCCESS', 'Hoa hồng đơn OD-2026-000003', NULL,
 'aa000000-0000-4000-8000-000000000003', 'ab000000-0000-4000-8000-000000000003', NULL, NULL, NULL,
 now() - interval '13 days'),
-- [5] member1 mua gói Thành viên Vàng BẰNG VÍ -> có đối ứng thật, quỹ được cộng tiền
('e0000000-0000-4000-8000-000000000012', NULL, 'MEMBER', '3c000000-0000-4000-8000-000000000011',
 'MEMBERSHIP_PURCHASE', -59000, NULL, 'SUCCESS', 'Mua Thành viên Vàng gói tháng', NULL,
 NULL, NULL, NULL, NULL, NULL, now() - interval '10 days'),
('e0000000-0000-4000-8000-000000000013', NULL, 'PLATFORM', '80000000-0000-4000-8000-000000000001',
 'MEMBERSHIP_PURCHASE', 59000, NULL, 'SUCCESS', 'Mua Thành viên Vàng gói tháng',
 'e0000000-0000-4000-8000-000000000012',
 NULL, NULL, NULL, NULL, NULL, now() - interval '10 days'),
-- [6] 4 lần mua gói VIP qua CỔNG -> chỉ ghi nhận doanh thu, không đổi số dư quỹ
('e0000000-0000-4000-8000-000000000014', NULL, 'PLATFORM', '80000000-0000-4000-8000-000000000001',
 'MEMBERSHIP_REVENUE', 29000, NULL, 'SUCCESS', 'Doanh thu gói Thành viên Bạc (VNPay)', NULL,
 NULL, NULL, NULL, NULL, NULL, now() - interval '50 days'),
('e0000000-0000-4000-8000-000000000015', NULL, 'PLATFORM', '80000000-0000-4000-8000-000000000001',
 'MEMBERSHIP_REVENUE', 29000, NULL, 'SUCCESS', 'Doanh thu gói Thành viên Bạc (VNPay)', NULL,
 NULL, NULL, NULL, NULL, NULL, now() - interval '40 days'),
('e0000000-0000-4000-8000-000000000016', NULL, 'PLATFORM', '80000000-0000-4000-8000-000000000001',
 'MEMBERSHIP_REVENUE', 990000, NULL, 'SUCCESS', 'Doanh thu gói Thành viên Kim cương năm (MoMo)', NULL,
 NULL, NULL, NULL, NULL, NULL, now() - interval '20 days'),
('e0000000-0000-4000-8000-000000000017', NULL, 'PLATFORM', '80000000-0000-4000-8000-000000000001',
 'MEMBERSHIP_REVENUE', 29000, NULL, 'SUCCESS', 'Doanh thu gói Thành viên Bạc (VNPay)', NULL,
 NULL, NULL, NULL, NULL, NULL, now() - interval '5 days'),
-- [7] Hoàn tiền OD-2026-000006 (hàng thất lạc) cho member1
('e0000000-0000-4000-8000-000000000018', NULL, 'MEMBER', '3c000000-0000-4000-8000-000000000011',
 'REFUND', 335000, NULL, 'SUCCESS', 'Hoàn tiền trả hàng thất lạc - đơn OD-2026-000006', NULL,
 'aa000000-0000-4000-8000-000000000006', NULL, NULL, NULL, NULL, now() - interval '6 days'),
('e0000000-0000-4000-8000-000000000019', NULL, 'PLATFORM', '80000000-0000-4000-8000-000000000001',
 'REFUND', -335000, NULL, 'SUCCESS', 'Hoàn tiền trả hàng thất lạc - đơn OD-2026-000006',
 'e0000000-0000-4000-8000-000000000018',
 'aa000000-0000-4000-8000-000000000006', NULL, NULL, NULL, NULL, now() - interval '6 days'),
-- [8] member3 rút 300.000đ từ ví về ngân hàng (RT-00001)
('e0000000-0000-4000-8000-000000000020', NULL, 'MEMBER', '3c000000-0000-4000-8000-000000000013',
 'WITHDRAWAL', -300000, NULL, 'SUCCESS', 'Rút tiền về ngân hàng - RT-00001', NULL,
 NULL, NULL, 'c3000000-0000-4000-8000-000000000001', NULL, NULL, now() - interval '5 days'),
('e0000000-0000-4000-8000-000000000021', NULL, 'PLATFORM', '80000000-0000-4000-8000-000000000001',
 'WITHDRAWAL', 300000, NULL, 'SUCCESS', 'Rút tiền về ngân hàng - RT-00001',
 'e0000000-0000-4000-8000-000000000020',
 NULL, NULL, 'c3000000-0000-4000-8000-000000000001', NULL, NULL, now() - interval '5 days'),
-- [9] Trả tiền người ký gửi của OD-2026-000004: món KỲ 2 -> 320.000 * 0.85 = 272.000đ
('e0000000-0000-4000-8000-000000000022', NULL, 'MEMBER', '3c000000-0000-4000-8000-000000000014',
 'ORDER_PAYOUT', 272000, NULL, 'SUCCESS', 'Thanh toán đơn hàng OD-2026-000004', NULL,
 'aa000000-0000-4000-8000-000000000004', 'ab000000-0000-4000-8000-000000000004', NULL, NULL, NULL,
 now() - interval '5 days'),
('e0000000-0000-4000-8000-000000000023', NULL, 'PLATFORM', '80000000-0000-4000-8000-000000000001',
 'ORDER_PAYOUT', -272000, NULL, 'SUCCESS', 'Thanh toán đơn hàng OD-2026-000004',
 'e0000000-0000-4000-8000-000000000022',
 'aa000000-0000-4000-8000-000000000004', 'ab000000-0000-4000-8000-000000000004', NULL, NULL, NULL,
 now() - interval '5 days'),
('e0000000-0000-4000-8000-000000000024', NULL, 'PLATFORM', '80000000-0000-4000-8000-000000000001',
 'COMMISSION_REVENUE', 48000, NULL, 'SUCCESS', 'Hoa hồng đơn OD-2026-000004', NULL,
 'aa000000-0000-4000-8000-000000000004', 'ab000000-0000-4000-8000-000000000004', NULL, NULL, NULL,
 now() - interval '5 days'),
-- [10] Hoàn tiền OD-2026-000014 (trả hàng hợp lệ, món bị tiêu huỷ) cho member8
('e0000000-0000-4000-8000-000000000025', NULL, 'MEMBER', '3c000000-0000-4000-8000-000000000018',
 'REFUND', 115000, NULL, 'SUCCESS', 'Hoàn tiền trả hàng hợp lệ - đơn OD-2026-000014', NULL,
 'aa000000-0000-4000-8000-000000000014', NULL, NULL, NULL, NULL, now() - interval '5 days'),
('e0000000-0000-4000-8000-000000000026', NULL, 'PLATFORM', '80000000-0000-4000-8000-000000000001',
 'REFUND', -115000, NULL, 'SUCCESS', 'Hoàn tiền trả hàng hợp lệ - đơn OD-2026-000014',
 'e0000000-0000-4000-8000-000000000025',
 'aa000000-0000-4000-8000-000000000014', NULL, NULL, NULL, NULL, now() - interval '5 days'),
-- [11] Hoàn tiền OD-2026-000007 (trả hàng hợp lệ, hàng xuống cấp) cho member2
('e0000000-0000-4000-8000-000000000027', NULL, 'MEMBER', '3c000000-0000-4000-8000-000000000012',
 'REFUND', 285000, NULL, 'SUCCESS', 'Hoàn tiền trả hàng hợp lệ - đơn OD-2026-000007', NULL,
 'aa000000-0000-4000-8000-000000000007', NULL, NULL, NULL, NULL, now() - interval '3 days'),
('e0000000-0000-4000-8000-000000000028', NULL, 'PLATFORM', '80000000-0000-4000-8000-000000000001',
 'REFUND', -285000, NULL, 'SUCCESS', 'Hoàn tiền trả hàng hợp lệ - đơn OD-2026-000007',
 'e0000000-0000-4000-8000-000000000027',
 'aa000000-0000-4000-8000-000000000007', NULL, NULL, NULL, NULL, now() - interval '3 days'),
-- [12] Đền bù hàng xuống cấp cho người ký gửi member7: 234.000đ
('e0000000-0000-4000-8000-000000000029', NULL, 'MEMBER', '3c000000-0000-4000-8000-000000000017',
 'DEGRADED_COMPENSATION', 234000, NULL, 'SUCCESS',
 'Đền bù hàng xuống cấp - sản phẩm HN-CV-000057', NULL,
 NULL, NULL, NULL, 'c1000000-0000-4000-8000-000000000001', NULL, now() - interval '2 days'),
('e0000000-0000-4000-8000-000000000030', NULL, 'PLATFORM', '80000000-0000-4000-8000-000000000001',
 'DEGRADED_COMPENSATION', -234000, NULL, 'SUCCESS',
 'Đền bù hàng xuống cấp - sản phẩm HN-CV-000057', 'e0000000-0000-4000-8000-000000000029',
 NULL, NULL, NULL, 'c1000000-0000-4000-8000-000000000001', NULL, now() - interval '2 days'),
-- [13] Trả tiền người ký gửi của OD-2026-000005 + chi phí voucher của đơn đó.
--      Chú ý: người ký gửi nhận 380.000 * 0.9 = 342.000đ tính trên GIÁ BÁN ĐẦY ĐỦ,
--      không bị trừ 15.000đ giảm giá - nền tảng tự chịu phần đó.
('e0000000-0000-4000-8000-000000000031', NULL, 'MEMBER', '3c000000-0000-4000-8000-000000000015',
 'ORDER_PAYOUT', 342000, NULL, 'SUCCESS', 'Thanh toán đơn hàng OD-2026-000005', NULL,
 'aa000000-0000-4000-8000-000000000005', 'ab000000-0000-4000-8000-000000000005', NULL, NULL, NULL,
 now() - interval '1 day'),
('e0000000-0000-4000-8000-000000000032', NULL, 'PLATFORM', '80000000-0000-4000-8000-000000000001',
 'ORDER_PAYOUT', -342000, NULL, 'SUCCESS', 'Thanh toán đơn hàng OD-2026-000005',
 'e0000000-0000-4000-8000-000000000031',
 'aa000000-0000-4000-8000-000000000005', 'ab000000-0000-4000-8000-000000000005', NULL, NULL, NULL,
 now() - interval '1 day'),
('e0000000-0000-4000-8000-000000000033', NULL, 'PLATFORM', '80000000-0000-4000-8000-000000000001',
 'COMMISSION_REVENUE', 38000, NULL, 'SUCCESS', 'Hoa hồng đơn OD-2026-000005', NULL,
 'aa000000-0000-4000-8000-000000000005', 'ab000000-0000-4000-8000-000000000005', NULL, NULL, NULL,
 now() - interval '1 day'),
('e0000000-0000-4000-8000-000000000034', NULL, 'PLATFORM', '80000000-0000-4000-8000-000000000001',
 'VOUCHER_DISCOUNT', -15000, NULL, 'SUCCESS',
 'Khuyến mãi đơn OD-2026-000005 (SHIP-9M5X7D)', NULL,
 'aa000000-0000-4000-8000-000000000005', NULL, NULL, NULL, NULL, now() - interval '1 day'),
-- [14] member1 trả bằng ví cho OD-2026-000012 (145.000đ)
('e0000000-0000-4000-8000-000000000035', NULL, 'MEMBER', '3c000000-0000-4000-8000-000000000011',
 'ORDER_PAYMENT', -145000, NULL, 'SUCCESS', 'Thanh toán bằng ví cho đơn OD-2026-000012', NULL,
 'aa000000-0000-4000-8000-000000000012', NULL, NULL, NULL, NULL, now() - interval '5 hours'),
('e0000000-0000-4000-8000-000000000036', NULL, 'PLATFORM', '80000000-0000-4000-8000-000000000001',
 'ORDER_PAYMENT', 145000, NULL, 'SUCCESS', 'Thanh toán bằng ví cho đơn OD-2026-000012',
 'e0000000-0000-4000-8000-000000000035',
 'aa000000-0000-4000-8000-000000000012', NULL, NULL, NULL, NULL, now() - interval '5 hours');


-- =============================================================================
-- 14. PHIẾU KÝ GỬI CHƯA THÀNH SẢN PHẨM (phủ đủ 7 trạng thái ConsignmentFormStatus)
-- =============================================================================
-- 73 phiếu ở bước 7 đều đã APPROVED và đã thành sản phẩm. 8 phiếu dưới đây là các
-- tình huống còn lại của luồng ký gửi:
--   DRAFT      - Member lưu nháp, CHƯA gửi: warehouse_id và form_no đều NULL
--   PENDING    - đã gửi, chờ Moderator duyệt -> sinh việc FORM_REVIEW
--   APPROVED   - đã duyệt, Member chưa mang hàng tới kho (còn trong hạn 7 ngày)
--   INSPECTING - Member đã mang hàng tới, Moderator đang kiểm định -> việc INSPECTION
--   REJECTED   - bị từ chối kèm lý do
--   EXPIRED    - quá hạn mang hàng tới kho
--   CANCELLED  - Member tự huỷ
INSERT INTO consignment_forms (id, form_no, member_id, warehouse_id, category_id, brand_id,
                               product_name, description, desired_price, size, item_condition,
                               status, reject_reason, bring_in_deadline, created_at, updated_at) VALUES
('f0000000-0000-4000-8000-000000000001', NULL, '2a000000-0000-4000-8000-000000000011',
 NULL, '4d000000-0000-4000-8000-000000000001', '5e000000-0000-4000-8000-000000000001',
 'Áo thun Uniqlo AIRism xám', 'Mới mặc 2 lần, còn rất mới, định ký gửi nhưng chưa chọn kho.',
 160000, 'M', 'LIKE_NEW', 'DRAFT', NULL, NULL,
 now() - interval '3 hours', now() - interval '3 hours'),
('f0000000-0000-4000-8000-000000000002', 'KG-000074', '2a000000-0000-4000-8000-000000000015',
 '1b000000-0000-4000-8000-000000000001', '4d000000-0000-4000-8000-000000000015',
 '5e000000-0000-4000-8000-000000000017',
 'Đầm Elise voan hoa tay dài', 'Đầm mua năm ngoái, mặc 3 lần, không lỗi, còn thơm nước giặt.',
 450000, 'M', 'GOOD', 'PENDING', NULL, NULL,
 now() - interval '2 hours', now() - interval '2 hours'),
('f0000000-0000-4000-8000-000000000003', 'KG-000075', '2a000000-0000-4000-8000-000000000017',
 '1b000000-0000-4000-8000-000000000002', '4d000000-0000-4000-8000-000000000006',
 '5e000000-0000-4000-8000-000000000013',
 'Áo khoác Canifa dáng dài nữ', 'Áo khoác mùa đông, còn 90%, khoá kéo tốt.',
 520000, 'L', 'GOOD', 'PENDING', NULL, NULL,
 now() - interval '5 hours', now() - interval '5 hours'),
('f0000000-0000-4000-8000-000000000004', 'KG-000076', '2a000000-0000-4000-8000-000000000016',
 '1b000000-0000-4000-8000-000000000001', '4d000000-0000-4000-8000-000000000009',
 '5e000000-0000-4000-8000-000000000005',
 'Quần jeans Levi''s 511 xanh nhạt', 'Quần jeans nam, còn 85%, bạc nhẹ tự nhiên.',
 380000, '31', 'GOOD', 'APPROVED', NULL, (now() + interval '5 days')::date,
 now() - interval '2 days', now() - interval '2 days'),
('f0000000-0000-4000-8000-000000000005', 'KG-000077', '2a000000-0000-4000-8000-000000000013',
 '1b000000-0000-4000-8000-000000000001', '4d000000-0000-4000-8000-000000000007',
 '5e000000-0000-4000-8000-000000000004',
 'Áo blazer Mango kẻ sọc nhỏ', 'Blazer nữ công sở, mặc vài lần, còn mới.',
 600000, 'S', 'LIKE_NEW', 'INSPECTING', NULL, (now() + interval '4 days')::date,
 now() - interval '3 days', now() - interval '6 hours'),
('f0000000-0000-4000-8000-000000000006', 'KG-000078', '2a000000-0000-4000-8000-000000000019',
 '1b000000-0000-4000-8000-000000000002', '4d000000-0000-4000-8000-000000000001',
 '5e000000-0000-4000-8000-000000000020',
 'Áo thun không rõ thương hiệu', 'Áo thun cũ, có vài vết bẩn nhỏ ở tay.',
 80000, 'L', 'FLAW', 'REJECTED',
 'Ảnh chụp quá mờ không xác định được tình trạng, và sản phẩm có vết bẩn vượt mức tiếp nhận. '
 'Vui lòng chụp lại rõ nét hoặc chọn sản phẩm khác.',
 NULL, now() - interval '6 days', now() - interval '5 days'),
('f0000000-0000-4000-8000-000000000007', 'KG-000079', '2a000000-0000-4000-8000-000000000014',
 '1b000000-0000-4000-8000-000000000002', '4d000000-0000-4000-8000-000000000014',
 '5e000000-0000-4000-8000-000000000003',
 'Chân váy H&M xếp ly nâu', 'Chân váy còn 90%, mặc 2 lần.',
 240000, 'M', 'GOOD', 'EXPIRED', NULL, (now() - interval '3 days')::date,
 now() - interval '12 days', now() - interval '3 days'),
('f0000000-0000-4000-8000-000000000008', 'KG-000080', '2a000000-0000-4000-8000-000000000011',
 '1b000000-0000-4000-8000-000000000001', '4d000000-0000-4000-8000-000000000012',
 '5e000000-0000-4000-8000-000000000008',
 'Quần short Puma xanh navy', 'Đổi ý không ký gửi nữa, muốn để lại dùng.',
 150000, 'M', 'GOOD', 'CANCELLED', NULL, NULL,
 now() - interval '8 days', now() - interval '7 days');

-- Ảnh Member chụp sản phẩm lúc gửi phiếu ký gửi
INSERT INTO consignment_form_photos (id, form_id, image_url, display_order) VALUES
('f1000000-0000-4000-8000-000000000001', 'f0000000-0000-4000-8000-000000000002',
 'https://cdn.dummyjson.com/product-images/tops/blue-frock/1.webp', 0),
('f1000000-0000-4000-8000-000000000002', 'f0000000-0000-4000-8000-000000000002',
 'https://cdn.dummyjson.com/product-images/tops/blue-frock/2.webp', 1),
('f1000000-0000-4000-8000-000000000003', 'f0000000-0000-4000-8000-000000000003',
 'https://fakestoreapi.com/img/71li-ujtlUL._AC_UX679_t.png', 0),
('f1000000-0000-4000-8000-000000000004', 'f0000000-0000-4000-8000-000000000004',
 'https://upload.wikimedia.org/wikipedia/commons/thumb/c/c7/501xx-front.jpg/500px-501xx-front.jpg', 0),
('f1000000-0000-4000-8000-000000000005', 'f0000000-0000-4000-8000-000000000005',
 'https://fakestoreapi.com/img/51Y5NI-I5jL._AC_UX679_t.png', 0),
('f1000000-0000-4000-8000-000000000006', 'f0000000-0000-4000-8000-000000000006',
 'https://fakestoreapi.com/img/71z3kpMAYsL._AC_UY879_t.png', 0);


-- =============================================================================
-- 15. HÀNG ĐỢI CÔNG VIỆC CỦA MODERATOR (DG-01)
-- =============================================================================
-- ref_id trỏ tới nghiệp vụ gốc (phiếu, đơn, yêu cầu trả hàng...) nên không đặt FK.
-- warehouse_id = NULL nghĩa là MỌI kho đều thấy việc đó (duyệt tổ chức, đổi giấy tờ,
-- báo cáo vi phạm). claimed_by = NULL là chưa ai nhận việc.
INSERT INTO queue_items (id, type, ref_id, warehouse_id, claimed_by, claimed_at, created_at) VALUES
-- Duyệt phiếu ký gửi
('d0000000-0000-4000-8000-000000000001', 'FORM_REVIEW', 'f0000000-0000-4000-8000-000000000002',
 '1b000000-0000-4000-8000-000000000001', NULL, NULL, now() - interval '2 hours'),
('d0000000-0000-4000-8000-000000000002', 'FORM_REVIEW', 'f0000000-0000-4000-8000-000000000003',
 '1b000000-0000-4000-8000-000000000002', '2a000000-0000-4000-8000-000000000004',
 now() - interval '1 hour', now() - interval '5 hours'),
-- Kiểm định hàng Member vừa mang tới kho
('d0000000-0000-4000-8000-000000000003', 'INSPECTION', 'f0000000-0000-4000-8000-000000000005',
 '1b000000-0000-4000-8000-000000000001', '2a000000-0000-4000-8000-000000000002',
 now() - interval '6 hours', now() - interval '8 hours'),
-- Đóng gói đơn đã thanh toán
('d0000000-0000-4000-8000-000000000004', 'PACKING', 'aa000000-0000-4000-8000-000000000008',
 '1b000000-0000-4000-8000-000000000001', NULL, NULL, now() - interval '2 hours'),
('d0000000-0000-4000-8000-000000000005', 'PACKING', 'aa000000-0000-4000-8000-000000000012',
 '1b000000-0000-4000-8000-000000000001', '2a000000-0000-4000-8000-000000000003',
 now() - interval '4 hours', now() - interval '5 hours'),
-- Xử lý yêu cầu trả hàng mới
('d0000000-0000-4000-8000-000000000006', 'RETURN', 'c0000000-0000-4000-8000-000000000004',
 '1b000000-0000-4000-8000-000000000002', NULL, NULL, now() - interval '12 hours'),
-- Duyệt hồ sơ tổ chức + đổi giấy tờ pháp lý (mọi kho đều thấy)
('d0000000-0000-4000-8000-000000000007', 'ORG_VERIFICATION', '2a000000-0000-4000-8000-000000000032',
 NULL, NULL, NULL, now() - interval '6 days'),
('d0000000-0000-4000-8000-000000000008', 'LEGAL_CHANGE', '2c000000-0000-4000-8000-000000000001',
 NULL, NULL, NULL, now() - interval '2 days'),
-- Yêu cầu rút hàng về
('d0000000-0000-4000-8000-000000000009', 'WITHDRAWAL', 'c2000000-0000-4000-8000-000000000001',
 '1b000000-0000-4000-8000-000000000001', NULL, NULL, now() - interval '1 day'),
('d0000000-0000-4000-8000-000000000010', 'WITHDRAWAL', 'c2000000-0000-4000-8000-000000000002',
 '1b000000-0000-4000-8000-000000000002', '2a000000-0000-4000-8000-000000000004',
 now() - interval '2 days', now() - interval '3 days'),
-- Chuẩn bị và giao hàng quyên góp cho tổ chức
('d0000000-0000-4000-8000-000000000011', 'DONATION_PICKUP', 'd2000000-0000-4000-8000-000000000001',
 '1b000000-0000-4000-8000-000000000001', NULL, NULL, now() - interval '2 days'),
-- Báo cáo vi phạm chờ xử lý (mọi kho đều thấy)
('d0000000-0000-4000-8000-000000000012', 'VIOLATION_REPORT', 'd3000000-0000-4000-8000-000000000001',
 NULL, NULL, NULL, now() - interval '1 day');


-- =============================================================================
-- 16. CHIẾN DỊCH TỪ THIỆN - QUYÊN GÓP - BÀI VIẾT CỦA TỔ CHỨC
-- =============================================================================
-- Chỉ tổ chức ĐÃ DUYỆT (org1) được tạo chiến dịch/bài viết. Tối đa 3 chiến dịch đang
-- chạy cùng lúc (system_configs.max_concurrent_campaigns).
INSERT INTO campaigns (id, organization_id, title, title_normalized, description,
                       preferred_item_types, start_time, end_time, status,
                       receive_at_own_address, location_name, latitude, longitude,
                       cover_url, created_at) VALUES
('d1000000-0000-4000-8000-000000000001', '2a000000-0000-4000-8000-000000000031',
 'Áo ấm cho em - Mùa đông 2026', 'ao am cho em - mua dong 2026',
 'Thu gom áo khoác, áo len, áo hoodie còn sử dụng được để gửi tới học sinh các trường '
 'vùng cao Lào Cai, Hà Giang trước đợt rét đậm. Ưu tiên đồ trẻ em và size nhỏ.',
 'Áo khoác, Áo len, Áo hoodie / Áo nỉ',
 now() - interval '10 days', now() + interval '20 days', 'OPEN', true,
 'Điểm nhận đồ Ánh Dương - Quận 1', 10.7745000, 106.7030000,
 'https://cdn.dummyjson.com/product-images/mens-shirts/men-cotton-jacket/1.webp',
 now() - interval '12 days'),
('d1000000-0000-4000-8000-000000000002', '2a000000-0000-4000-8000-000000000031',
 'Tủ quần áo 0 đồng - Phường Tân Mỹ', 'tu quan ao 0 dong - phuong tan my',
 'Nhận mọi loại quần áo còn lành lặn để lập tủ quần áo 0 đồng cho người lao động '
 'quanh khu chế xuất. Nhận trực tiếp tại kho ReHome TP.HCM.',
 'Áo thun, Quần jeans, Quần tây', now() - interval '3 days', now() + interval '12 days',
 'OPEN', false, 'Kho ReHome TP.HCM', 10.7329000, 106.7240000, NULL,
 now() - interval '4 days'),
('d1000000-0000-4000-8000-000000000003', '2a000000-0000-4000-8000-000000000031',
 'Tết ấm 2026 - Chia sẻ áo dài', 'tet am 2026 - chia se ao dai',
 'Chiến dịch đã kết thúc: thu gom áo dài, váy, đầm còn mới tặng các cô giáo vùng xa '
 'mặc dịp Tết. Tổng kết nhận được 312 món.',
 'Áo dài, Đầm, Váy liền', now() - interval '60 days', now() - interval '30 days',
 'CLOSED', true, 'Điểm nhận đồ Ánh Dương - Quận 1', 10.7745000, 106.7030000, NULL,
 now() - interval '65 days');

-- Kho nhận hàng hộ cho từng chiến dịch (bảng nối nhiều-nhiều)
INSERT INTO campaign_warehouses (campaign_id, warehouse_id) VALUES
('d1000000-0000-4000-8000-000000000001', '1b000000-0000-4000-8000-000000000001'),
('d1000000-0000-4000-8000-000000000001', '1b000000-0000-4000-8000-000000000002'),
('d1000000-0000-4000-8000-000000000002', '1b000000-0000-4000-8000-000000000001'),
('d1000000-0000-4000-8000-000000000003', '1b000000-0000-4000-8000-000000000001'),
('d1000000-0000-4000-8000-000000000003', '1b000000-0000-4000-8000-000000000002');

-- Quyên góp. source = DIRECT (Member mang đồ tới kho tặng thẳng) hoặc CONSIGNED_ITEM
-- (món đang ký gửi được Member tặng lại, xem end_of_term_decisions decision = DONATE).
INSERT INTO donations (id, warehouse_id, campaign_id, product_id, source, status, donor_name,
                       is_anonymous, note, recorded_by_account_id,
                       handed_over_to_organization_id, handed_over_at, created_at) VALUES
-- Chờ tổ chức tới nhận -> có việc DONATION_PICKUP trong hàng đợi
('d2000000-0000-4000-8000-000000000001', '1b000000-0000-4000-8000-000000000001',
 'd1000000-0000-4000-8000-000000000001', NULL, 'DIRECT', 'PENDING_PICKUP',
 'Nguyễn Văn An', false, '1 túi gồm 4 áo khoác và 2 áo len size M-L, đã giặt sạch.',
 '2a000000-0000-4000-8000-000000000002', NULL, NULL, now() - interval '2 days'),
-- Đã giao cho tổ chức, người tặng ẩn danh
('d2000000-0000-4000-8000-000000000002', '1b000000-0000-4000-8000-000000000002',
 'd1000000-0000-4000-8000-000000000001', NULL, 'DIRECT', 'HANDED_OVER',
 NULL, true, '1 thùng 12 món áo ấm trẻ em.',
 '2a000000-0000-4000-8000-000000000004', '2a000000-0000-4000-8000-000000000031',
 now() - interval '5 days', now() - interval '8 days'),
-- Món ĐANG KÝ GỬI được Member tặng lại cuối kỳ (sản phẩm HCM-QS-000067 -> DONATED)
('d2000000-0000-4000-8000-000000000003', '1b000000-0000-4000-8000-000000000001',
 'd1000000-0000-4000-8000-000000000002', 'HCM-QS-000067', 'CONSIGNED_ITEM', 'HANDED_OVER',
 'Võ Minh Đức', false, 'Hết kỳ ký gửi, Member chọn tặng lại cho chiến dịch.',
 '2a000000-0000-4000-8000-000000000002', '2a000000-0000-4000-8000-000000000031',
 now() - interval '3 days', now() - interval '10 days');

-- Lời nhắn cảm ơn / ghi nhận của tổ chức
INSERT INTO donation_notes (id, organization_id, campaign_id, donor_name, is_anonymous, note, created_at) VALUES
('d2100000-0000-4000-8000-000000000001', '2a000000-0000-4000-8000-000000000031',
 'd1000000-0000-4000-8000-000000000001', 'Nguyễn Văn An', false,
 'Cảm ơn anh An đã gửi 6 món áo ấm chất lượng rất tốt, nhóm đã chuyển lên Lào Cai ngày 10/11.',
 now() - interval '1 day'),
('d2100000-0000-4000-8000-000000000002', '2a000000-0000-4000-8000-000000000031',
 'd1000000-0000-4000-8000-000000000001', NULL, true,
 'Cảm ơn nhà hảo tâm ẩn danh đã tặng 12 món áo ấm trẻ em.',
 now() - interval '4 days');

-- Bài viết cộng đồng của tổ chức (1 bài bị gỡ do báo cáo vi phạm)
INSERT INTO community_posts (id, organization_id, campaign_id, content, status, created_at, updated_at) VALUES
('d4000000-0000-4000-8000-000000000001', '2a000000-0000-4000-8000-000000000031',
 'd1000000-0000-4000-8000-000000000001',
 'Ngày đầu tiên của chiến dịch "Áo ấm cho em", nhóm đã nhận được hơn 80 món từ 23 nhà hảo tâm. '
 'Cảm ơn mọi người rất nhiều! Chiến dịch vẫn tiếp tục nhận đồ tới hết tháng này.',
 'VISIBLE', now() - interval '9 days', now() - interval '9 days'),
('d4000000-0000-4000-8000-000000000002', '2a000000-0000-4000-8000-000000000031',
 NULL,
 'Tổng kết chiến dịch Tết ấm 2026: 312 món quần áo đã đến tay 180 cô giáo và học sinh tại '
 '4 điểm trường. Toàn bộ hình ảnh và danh sách phân phối nhóm đã công khai ở website.',
 'VISIBLE', now() - interval '28 days', now() - interval '28 days'),
('d4000000-0000-4000-8000-000000000003', '2a000000-0000-4000-8000-000000000031',
 NULL,
 'Bài viết này đã bị gỡ sau khi Moderator xử lý báo cáo vi phạm (nội dung kêu gọi chuyển khoản '
 'riêng ngoài nền tảng).',
 'REMOVED', now() - interval '7 days', now() - interval '5 days');

INSERT INTO community_post_photos (id, post_id, image_url) VALUES
('d4100000-0000-4000-8000-000000000001', 'd4000000-0000-4000-8000-000000000001',
 'https://cdn.dummyjson.com/product-images/mens-shirts/men-cotton-jacket/2.webp'),
('d4100000-0000-4000-8000-000000000002', 'd4000000-0000-4000-8000-000000000001',
 'https://cdn.dummyjson.com/product-images/mens-shirts/men-cotton-jacket/3.webp'),
('d4100000-0000-4000-8000-000000000003', 'd4000000-0000-4000-8000-000000000002',
 'https://cdn.dummyjson.com/product-images/tops/tartan-dress/1.webp');

-- Bình luận (có 1 bình luận trả lời bình luận khác qua parent_id) và lượt thích
INSERT INTO post_comments (id, post_id, author_id, parent_id, content, status, created_at) VALUES
('d5000000-0000-4000-8000-000000000001', 'd4000000-0000-4000-8000-000000000001',
 '2a000000-0000-4000-8000-000000000011', NULL,
 'Mình còn 3 áo khoác nữa, cuối tuần mang qua kho Quận 7 được không ạ?', 'VISIBLE',
 now() - interval '8 days'),
('d5000000-0000-4000-8000-000000000002', 'd4000000-0000-4000-8000-000000000001',
 '2a000000-0000-4000-8000-000000000031', 'd5000000-0000-4000-8000-000000000001',
 'Được bạn nhé, kho nhận giúp nhóm từ 8h đến 20h cả tuần. Cảm ơn bạn nhiều!', 'VISIBLE',
 now() - interval '8 days' + interval '2 hours'),
('d5000000-0000-4000-8000-000000000003', 'd4000000-0000-4000-8000-000000000002',
 '2a000000-0000-4000-8000-000000000012', NULL,
 'Minh bạch quá, năm sau mình sẽ tham gia ngay từ đầu.', 'VISIBLE',
 now() - interval '27 days'),
('d5000000-0000-4000-8000-000000000004', 'd4000000-0000-4000-8000-000000000001',
 '2a000000-0000-4000-8000-000000000020', NULL,
 'Bình luận đã bị gỡ do chứa từ khoá vi phạm.', 'REMOVED',
 now() - interval '7 days');

INSERT INTO post_likes (id, post_id, member_id, created_at) VALUES
('d6000000-0000-4000-8000-000000000001', 'd4000000-0000-4000-8000-000000000001',
 '2a000000-0000-4000-8000-000000000011', now() - interval '8 days'),
('d6000000-0000-4000-8000-000000000002', 'd4000000-0000-4000-8000-000000000001',
 '2a000000-0000-4000-8000-000000000012', now() - interval '8 days'),
('d6000000-0000-4000-8000-000000000003', 'd4000000-0000-4000-8000-000000000001',
 '2a000000-0000-4000-8000-000000000015', now() - interval '7 days'),
('d6000000-0000-4000-8000-000000000004', 'd4000000-0000-4000-8000-000000000002',
 '2a000000-0000-4000-8000-000000000011', now() - interval '27 days');

-- Member theo dõi tổ chức (được nhận thông báo khi tổ chức mở chiến dịch / đăng bài)
INSERT INTO follows (id, member_id, organization_id, created_at) VALUES
('d7000000-0000-4000-8000-000000000001', '2a000000-0000-4000-8000-000000000011',
 '2a000000-0000-4000-8000-000000000031', now() - interval '20 days'),
('d7000000-0000-4000-8000-000000000002', '2a000000-0000-4000-8000-000000000012',
 '2a000000-0000-4000-8000-000000000031', now() - interval '15 days'),
('d7000000-0000-4000-8000-000000000003', '2a000000-0000-4000-8000-000000000015',
 '2a000000-0000-4000-8000-000000000031', now() - interval '9 days');

-- Báo cáo vi phạm: 1 chờ xử lý (có việc trong hàng đợi) + 1 đã xử lý (gỡ bài viết)
INSERT INTO violation_reports (id, reporter_member_id, target_type, target_id, reason, status,
                               resolution_action, resolution_reason, resolved_by, resolved_at,
                               created_at) VALUES
('d3000000-0000-4000-8000-000000000001', '2a000000-0000-4000-8000-000000000012',
 'CAMPAIGN', 'd1000000-0000-4000-8000-000000000002',
 'Mô tả chiến dịch hứa tặng quà cho người quyên góp, mình thấy giống trá hình thu gom thương mại.',
 'PENDING', NULL, NULL, NULL, NULL, now() - interval '1 day'),
('d3000000-0000-4000-8000-000000000002', '2a000000-0000-4000-8000-000000000011',
 'POST', 'd4000000-0000-4000-8000-000000000003',
 'Bài viết kêu gọi chuyển khoản vào số tài khoản cá nhân, không qua nền tảng.',
 'RESOLVED', 'REMOVE_POST',
 'Nội dung vi phạm quy định giao dịch ngoài nền tảng, đã gỡ bài và cảnh báo tổ chức lần 1.',
 '2a000000-0000-4000-8000-000000000002', now() - interval '5 days', now() - interval '6 days');


-- =============================================================================
-- 17. THỬ ĐỒ (AI ghép ảnh + thử đồ real-time) - THÔNG BÁO - CHAT - LỊCH SỬ ADMIN
-- =============================================================================
-- try_on_images có RÀNG BUỘC CHECK: hoặc (áo TOP + quần BOTTOM, dress NULL),
-- hoặc (chỉ dress, top/bottom NULL) - không ghép đầm với áo/quần được.
-- image_url trỏ tới ảnh kết quả do storage sinh ra (uploads/try-on/...).
INSERT INTO try_on_images (id, member_id, top_product_id, bottom_product_id, dress_product_id,
                           model_type, image_url, created_at) VALUES
('e1000000-0000-4000-8000-000000000001', '2a000000-0000-4000-8000-000000000011',
 'HCM-ASM-000069', 'HCM-QT-000070', NULL, 'MALE',
 'http://localhost:8080/files/try-on/226eff4c-4041-4c9e-8130-a9a83900cfc3.png',
 now() - interval '2 days'),
('e1000000-0000-4000-8000-000000000002', '2a000000-0000-4000-8000-000000000011',
 NULL, NULL, 'HCM-D-000042', 'FEMALE',
 'http://localhost:8080/files/try-on/2373914e-b046-4446-a963-d504e6fd618f.png',
 now() - interval '1 day'),
('e1000000-0000-4000-8000-000000000003', '2a000000-0000-4000-8000-000000000013',
 NULL, NULL, 'HCM-VL-000046', 'FEMALE',
 'http://localhost:8080/files/try-on/6319d20c-e099-48ca-9dc2-11bd38f3d266.png',
 now() - interval '6 hours');

-- Phiên thử đồ real-time qua webcam. model_id ghim đúng version như cấu hình
-- decart.vton.model-id, không dùng alias "latest".
INSERT INTO live_tryon_sessions (id, member_id, product_id, model_id, status,
                                 actual_duration_seconds, video_url, completed_at, created_at) VALUES
('e2000000-0000-4000-8000-000000000001', '2a000000-0000-4000-8000-000000000011',
 'HCM-ASM-000069', 'lucy-vton-3.5', 'COMPLETED', 14, NULL,
 now() - interval '2 days' + interval '14 seconds', now() - interval '2 days'),
('e2000000-0000-4000-8000-000000000002', '2a000000-0000-4000-8000-000000000013',
 'HCM-D-000042', 'lucy-vton-3.5', 'COMPLETED', 20, NULL,
 now() - interval '5 hours' + interval '20 seconds', now() - interval '5 hours'),
('e2000000-0000-4000-8000-000000000003', '2a000000-0000-4000-8000-000000000012',
 'HN-D-000044', 'lucy-vton-3.5', 'FAILED', NULL, NULL, NULL, now() - interval '3 hours');

-- Xác nhận danh tính tại quầy (Moderator quét QR của Member, hiệu lực trong ngày)
INSERT INTO counter_identity_confirmations (account_id, confirmed_by_moderator_id, confirmed_at, expires_at) VALUES
('2a000000-0000-4000-8000-000000000019', '2a000000-0000-4000-8000-000000000004',
 now() - interval '30 minutes', now() + interval '30 minutes');

-- Token thiết bị để đẩy thông báo đẩy
INSERT INTO device_tokens (id, account_id, token, platform, created_at) VALUES
('e3000000-0000-4000-8000-000000000001', '2a000000-0000-4000-8000-000000000011',
 'fcm-demo-token-member1-android-0001', 'ANDROID', now() - interval '30 days'),
('e3000000-0000-4000-8000-000000000002', '2a000000-0000-4000-8000-000000000012',
 'fcm-demo-token-member2-web-0002', 'WEB', now() - interval '20 days');

-- Thông báo trong app
INSERT INTO notifications (id, recipient_account_id, type, title, body, target_type, target_id,
                           warehouse_id, is_read, created_at) VALUES
('e4000000-0000-4000-8000-000000000001', '2a000000-0000-4000-8000-000000000011',
 'ORDER_UPDATE', 'Đơn hàng đã hoàn thành',
 'Đơn OD-2026-000003 đã hoàn thành. Bạn có thể đánh giá dịch vụ ngay bây giờ.',
 NULL, NULL, NULL, true, now() - interval '13 days'),
('e4000000-0000-4000-8000-000000000002', '2a000000-0000-4000-8000-000000000011',
 'WALLET_EVENT', 'Tiền đã về ví',
 'Bạn nhận được 468.000đ từ đơn OD-2026-000001 (đã trừ 10% hoa hồng).',
 NULL, NULL, NULL, true, now() - interval '38 days'),
('e4000000-0000-4000-8000-000000000003', '2a000000-0000-4000-8000-000000000011',
 'MEMBERSHIP_EVENT', 'Kích hoạt gói Thành viên Vàng',
 'Gói Thành viên Vàng của bạn có hiệu lực tới hết 30 ngày tới. Bạn đã nhận 6 voucher.',
 NULL, NULL, NULL, false, now() - interval '10 days'),
('e4000000-0000-4000-8000-000000000004', '2a000000-0000-4000-8000-000000000011',
 'VOUCHER_ISSUED', 'Bạn có voucher mới',
 '4 mã miễn phí vận chuyển và 2 mã giảm giá đơn hàng đã vào ví voucher của bạn.',
 NULL, NULL, NULL, false, now() - interval '10 days'),
('e4000000-0000-4000-8000-000000000005', '2a000000-0000-4000-8000-000000000016',
 'TERM_EXPIRY', 'Sản phẩm sắp hết kỳ ký gửi',
 'Sản phẩm HCM-QK-000063 đã hết 90 ngày ký gửi. Vui lòng chọn lấy về hoặc ký gửi lại '
 'trong 30 ngày.',
 'PRODUCT', 'HCM-QK-000063', NULL, false, now() - interval '5 days'),
('e4000000-0000-4000-8000-000000000006', '2a000000-0000-4000-8000-000000000017',
 'WALLET_EVENT', 'Đã nhận tiền đền bù',
 'Bạn nhận được 234.000đ đền bù cho sản phẩm HN-CV-000057 bị xuống cấp.',
 NULL, NULL, NULL, false, now() - interval '2 days'),
('e4000000-0000-4000-8000-000000000007', '2a000000-0000-4000-8000-000000000031',
 'VERIFICATION_RESULT', 'Hồ sơ tổ chức đã được duyệt',
 'Hồ sơ của Ánh Dương đã được xác minh. Bạn có thể tạo chiến dịch và đăng bài.',
 'ORGANIZATION', '2a000000-0000-4000-8000-000000000031', NULL, true, now() - interval '50 days'),
('e4000000-0000-4000-8000-000000000008', '2a000000-0000-4000-8000-000000000031',
 'VIOLATION_WARNING', 'Cảnh báo vi phạm lần 1',
 'Bài viết của bạn đã bị gỡ do kêu gọi giao dịch ngoài nền tảng. Vi phạm tiếp theo có thể '
 'dẫn tới khoá tài khoản.',
 'POST', 'd4000000-0000-4000-8000-000000000003', NULL, false, now() - interval '5 days'),
('e4000000-0000-4000-8000-000000000009', '2a000000-0000-4000-8000-000000000011',
 'FOLLOWED_ORG_CAMPAIGN', 'Tổ chức bạn theo dõi vừa mở chiến dịch',
 'Ánh Dương vừa mở chiến dịch "Tủ quần áo 0 đồng - Phường Tân Mỹ".',
 'CAMPAIGN', 'd1000000-0000-4000-8000-000000000002', NULL, false, now() - interval '3 days'),
('e4000000-0000-4000-8000-000000000010', '2a000000-0000-4000-8000-000000000002',
 'QUEUE_TASK', 'Có việc mới trong hàng đợi',
 'Một phiếu ký gửi mới đang chờ duyệt tại Kho ReHome TP.HCM.',
 NULL, NULL, '1b000000-0000-4000-8000-000000000001', false, now() - interval '2 hours'),
('e4000000-0000-4000-8000-000000000011', '2a000000-0000-4000-8000-000000000031',
 'DONATION_RECORDED', 'Có lô quyên góp mới chờ nhận',
 'Kho ReHome TP.HCM đã ghi nhận 1 lô quyên góp cho chiến dịch "Áo ấm cho em - Mùa đông 2026".',
 'CAMPAIGN', 'd1000000-0000-4000-8000-000000000001', NULL, false, now() - interval '2 days'),
('e4000000-0000-4000-8000-000000000012', '2a000000-0000-4000-8000-000000000015',
 'MEMBERSHIP_EVENT', 'Gói thành viên đã hết hạn',
 'Gói Thành viên Bạc của bạn đã hết hạn. Gia hạn để tiếp tục dùng hạn mức thử đồ cao hơn.',
 NULL, NULL, NULL, true, now() - interval '20 days');

-- Hội thoại: Member <-> Tổ chức và Member <-> Kho.
-- counterpart_id không đặt FK vì trỏ tới 2 loại khác nhau: tài khoản tổ chức hoặc kho.
INSERT INTO conversations (id, member_id, counterpart_id, type, created_at) VALUES
('e5000000-0000-4000-8000-000000000001', '2a000000-0000-4000-8000-000000000011',
 '2a000000-0000-4000-8000-000000000031', 'MEMBER_ORGANIZATION', now() - interval '8 days'),
('e5000000-0000-4000-8000-000000000002', '2a000000-0000-4000-8000-000000000011',
 '1b000000-0000-4000-8000-000000000001', 'MEMBER_WAREHOUSE', now() - interval '2 days');

INSERT INTO messages (id, conversation_id, sender_account_id, content, sent_at, read_at) VALUES
('e6000000-0000-4000-8000-000000000001', 'e5000000-0000-4000-8000-000000000001',
 '2a000000-0000-4000-8000-000000000011',
 'Chào nhóm, mình muốn gửi 3 áo khoác cho chiến dịch Áo ấm cho em thì mang tới đâu ạ?',
 now() - interval '8 days', now() - interval '8 days' + interval '20 minutes'),
('e6000000-0000-4000-8000-000000000002', 'e5000000-0000-4000-8000-000000000001',
 '2a000000-0000-4000-8000-000000000031',
 'Chào bạn, bạn mang tới điểm nhận ở 10 Nguyễn Huệ hoặc gửi tại kho ReHome TP.HCM đều được nhé.',
 now() - interval '8 days' + interval '25 minutes', now() - interval '8 days' + interval '30 minutes'),
('e6000000-0000-4000-8000-000000000003', 'e5000000-0000-4000-8000-000000000001',
 '2a000000-0000-4000-8000-000000000011',
 'Mình đã gửi ở kho Quận 7 sáng nay rồi nhé, cảm ơn nhóm!',
 now() - interval '2 days', NULL),
('e6000000-0000-4000-8000-000000000004', 'e5000000-0000-4000-8000-000000000002',
 '2a000000-0000-4000-8000-000000000011',
 'Đơn OD-2026-000012 của mình đang đóng gói, mình đổi địa chỉ giao sang công ty được không ạ?',
 now() - interval '4 hours', now() - interval '3 hours'),
('e6000000-0000-4000-8000-000000000005', 'e5000000-0000-4000-8000-000000000002',
 '2a000000-0000-4000-8000-000000000003',
 'Được bạn nhé, đơn chưa tạo vận đơn nên bạn đổi địa chỉ trong app rồi báo lại giúp mình.',
 now() - interval '3 hours', NULL);

-- Lịch sử thao tác của Admin / Moderator (action_type đúng như các service đang ghi)
INSERT INTO audit_logs (id, actor_account_id, action_type, target_type, target_id, detail, created_at) VALUES
('e7000000-0000-4000-8000-000000000001', '2a000000-0000-4000-8000-000000000001',
 'UPDATE_CONFIG', 'SYSTEM_CONFIG', 'withdrawal_fee',
 '{"key": "withdrawal_fee", "oldValue": "0", "newValue": "50000"}'::jsonb,
 now() - interval '120 days'),
('e7000000-0000-4000-8000-000000000002', '2a000000-0000-4000-8000-000000000001',
 'UPDATE_MEMBERSHIP_PLAN', 'MEMBERSHIP_PLAN', '6f000000-0000-4000-8000-000000000003',
 '{"tier": "GOLD", "aiTryonPerDay": {"old": 30, "new": 40}}'::jsonb,
 now() - interval '60 days'),
('e7000000-0000-4000-8000-000000000003', '2a000000-0000-4000-8000-000000000001',
 'CREATE_VIOLATION_WORD', 'VIOLATION_WORD', '72000000-0000-4000-8000-000000000004',
 '{"word": "chuyển khoản riêng", "category": "FRAUD", "severity": "WARNING", "action": "AUTO_FLAG"}'::jsonb,
 now() - interval '45 days'),
('e7000000-0000-4000-8000-000000000004', '2a000000-0000-4000-8000-000000000002',
 'REMOVE_POST', 'POST', 'd4000000-0000-4000-8000-000000000003',
 '{"reportId": "d3000000-0000-4000-8000-000000000002", "reason": "Giao dịch ngoài nền tảng"}'::jsonb,
 now() - interval '5 days'),
('e7000000-0000-4000-8000-000000000005', '2a000000-0000-4000-8000-000000000016',
 'DONATE_CONSIGNED_ITEM', 'PRODUCT', 'HCM-QS-000067',
 '{"campaignId": "d1000000-0000-4000-8000-000000000002", "decision": "DONATE"}'::jsonb,
 now() - interval '10 days');


-- =============================================================================
-- 18. CHỐT LẠI CÁC GIÁ TRỊ SUY RA TỪ DỮ LIỆU (không gõ tay -> không bao giờ lệch)
-- =============================================================================

-- 18.1 Số hiệu bút toán WT-2026-xxxxxx, đánh theo đúng thứ tự thời gian.
-- Code sinh số này bằng cách ĐẾM các dòng có tiền tố WT-2026- rồi +1, nên dãy phải
-- liền mạch từ 1, nếu không bút toán thật tiếp theo sẽ trùng số.
WITH ordered AS (
    SELECT id,
           row_number() OVER (ORDER BY created_at, wallet_type DESC, id) AS rn
    FROM wallet_transactions
)
UPDATE wallet_transactions t
SET transaction_no = 'WT-2026-' || lpad(o.rn::text, 6, '0')
FROM ordered o
WHERE t.id = o.id;

-- 18.2 balance_after của từng bút toán VÍ MEMBER = số dư cộng dồn theo thời gian
WITH running AS (
    SELECT id,
           SUM(amount) OVER (PARTITION BY wallet_id
                             ORDER BY created_at, id
                             ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS bal
    FROM wallet_transactions
    WHERE wallet_type = 'MEMBER' AND status = 'SUCCESS'
)
UPDATE wallet_transactions t SET balance_after = r.bal
FROM running r WHERE t.id = r.id;

-- 18.3 balance_after của bút toán QUỸ NỀN TẢNG. 3 loại chỉ-thống-kê
-- (COMMISSION_REVENUE / MEMBERSHIP_REVENUE / VOUCHER_DISCOUNT) KHÔNG làm đổi số dư quỹ,
-- nên chúng cộng 0 vào số cộng dồn và chỉ ghi lại số dư tại thời điểm đó.
WITH running AS (
    SELECT id,
           SUM(CASE WHEN type IN ('COMMISSION_REVENUE', 'MEMBERSHIP_REVENUE',
                                  'VOUCHER_DISCOUNT', 'WITHDRAWAL_FEE_REVENUE')
                    THEN 0 ELSE amount END)
               OVER (ORDER BY created_at, id
                     ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS bal
    FROM wallet_transactions
    WHERE wallet_type = 'PLATFORM' AND status = 'SUCCESS'
)
UPDATE wallet_transactions t SET balance_after = r.bal
FROM running r WHERE t.id = r.id;

-- 18.4 Số dư ví Member = tổng bút toán của ví đó
UPDATE wallets w
SET balance = COALESCE((SELECT SUM(t.amount) FROM wallet_transactions t
                        WHERE t.wallet_type = 'MEMBER' AND t.wallet_id = w.id
                          AND t.status = 'SUCCESS'), 0),
    updated_at = now();

-- 18.5 Tiền đang bị KHOÁ vì có yêu cầu rút tiền chưa chi xong (PENDING/PROCESSING).
-- Ví chỉ tiêu được balance - locked_balance, nên member4 còn 72.000đ khả dụng.
UPDATE wallets w
SET locked_balance = COALESCE((SELECT SUM(p.amount) FROM payout_requests p
                               WHERE p.wallet_id = w.id
                                 AND p.status IN ('PENDING', 'PROCESSING')), 0);

-- 18.6 Số dư quỹ nền tảng = tổng các bút toán quỹ CÓ ảnh hưởng số dư
UPDATE platform_funds
SET balance = COALESCE((SELECT SUM(t.amount) FROM wallet_transactions t
                        WHERE t.wallet_type = 'PLATFORM' AND t.status = 'SUCCESS'
                          AND t.type NOT IN ('COMMISSION_REVENUE', 'MEMBERSHIP_REVENUE',
                                             'VOUCHER_DISCOUNT', 'WITHDRAWAL_FEE_REVENUE')), 0),
    updated_at = now();

-- 18.7 Nhãn SẢN PHẨM NỔI BẬT (F-VIP §8.10). featured_until là cột tính sẵn trên products
-- (không join sang member_subscriptions) vì GET /products là truy vấn nóng nhất; cái giá
-- phải trả là mỗi lần gói thay đổi đều phải set/clear lại cột này.
-- Chỉ Member đang có gói ACTIVE với featured_listing = true, và chỉ món còn trong kho.
UPDATE products p
SET featured_until = s.expires_at
FROM member_subscriptions s
WHERE s.member_id = p.member_id
  AND s.status = 'ACTIVE'
  AND s.featured_listing = true
  AND s.expires_at > now()
  AND p.status IN ('ON_SALE', 'RESERVED', 'WAREHOUSE');

-- 18.8 Đẩy các sequence lên quá số đã dùng, nếu không mã do app sinh sau này sẽ TRÙNG
-- với dữ liệu seed và chết ở unique constraint (member_code, receipt_no, form_no, SKU).
SELECT setval('member_code_seq',
              (SELECT COALESCE(MAX(substring(member_code FROM 3)::bigint), 0)
               FROM member_profiles WHERE member_code ~ '^MB[0-9]+$'));
SELECT setval('moderator_code_seq',
              (SELECT COALESCE(MAX(substring(moderator_code FROM 4)::bigint), 0)
               FROM moderator_profiles WHERE moderator_code ~ '^MOD[0-9]+$'));
SELECT setval('receipt_no_seq',
              (SELECT COALESCE(MAX(right(receipt_no, 6)::bigint), 0)
               FROM consignment_receipts WHERE receipt_no ~ '^KG-[0-9]{4}-[0-9]{6}$'));
SELECT setval('consignment_form_no_seq',
              (SELECT COALESCE(MAX(right(form_no, 6)::bigint), 0)
               FROM consignment_forms WHERE form_no ~ '^KG-[0-9]{6}$'));
SELECT setval('product_sku_seq',
              (SELECT COALESCE(MAX(split_part(id, '-', 3)::bigint), 0) FROM products));


-- =============================================================================
-- 19. KIỂM TRA NHANH SAU KHI SEED (chạy riêng để xem kết quả)
-- =============================================================================
-- SELECT status, count(*) FROM products GROUP BY status ORDER BY 1;
-- SELECT status, count(*) FROM orders GROUP BY status ORDER BY 1;
-- SELECT a.email, mp.full_name, w.balance, w.locked_balance
--   FROM accounts a JOIN member_profiles mp ON mp.account_id = a.id
--   JOIN wallets w ON w.member_id = a.id ORDER BY a.email;
-- SELECT type, count(*), sum(amount) FROM wallet_transactions
--   GROUP BY type ORDER BY 1;
-- -- Ví và sổ phải khớp tuyệt đối (không trả về dòng nào):
-- SELECT w.id FROM wallets w WHERE w.balance <> COALESCE(
--   (SELECT sum(amount) FROM wallet_transactions t
--    WHERE t.wallet_type='MEMBER' AND t.wallet_id = w.id AND t.status='SUCCESS'), 0);


-- =============================================================================
-- 20. TUỲ CHỌN - ĐIỀN MÃ GHN (province_id của Hà Nội, ward_code của phường/xã)
-- =============================================================================
-- Hai cột này nullable và không tham gia tạo vận đơn (GHN hệ 2 cấp đi theo TÊN), nên
-- bỏ trống vẫn chạy đủ. Muốn đầy đủ thì lấy mã thật rồi bỏ comment và sửa số:
--   GET /address-units/provinces             -> tìm dòng name = "Hà Nội", lấy code
--   GET /address-units/wards?provinceId=...  -> lấy code của từng phường/xã
--
-- UPDATE warehouses      SET province_id = <id Hà Nội> WHERE province_name = 'Hà Nội';
-- UPDATE shipping_addresss SET province_id = <id Hà Nội> WHERE province_name = 'Hà Nội';
-- UPDATE warehouses        SET ward_code = '<code>' WHERE ward_name = 'Phường Tân Mỹ';
-- UPDATE warehouses        SET ward_code = '<code>' WHERE ward_name = 'Phường Cầu Giấy';
-- UPDATE shipping_addresss SET ward_code = '<code>' WHERE ward_name = 'Phường Sài Gòn';
-- UPDATE shipping_addresss SET ward_code = '<code>' WHERE ward_name = 'Phường Bến Thành';
-- UPDATE shipping_addresss SET ward_code = '<code>' WHERE ward_name = 'Phường Tân Mỹ';
-- UPDATE shipping_addresss SET ward_code = '<code>' WHERE ward_name = 'Phường Cầu Giấy';
-- UPDATE shipping_addresss SET ward_code = '<code>' WHERE ward_name = 'Phường Cửa Nam';


-- #############################################################################
-- #                      DANH SÁCH TÀI KHOẢN ĐỂ DEMO                          #
-- #############################################################################
-- MẬT KHẨU CỦA TẤT CẢ TÀI KHOẢN: 123456
--
-- ------------------------------------------------------------------------ ADMIN
--   admin@rehome.com                        - toàn quyền: tham số hệ thống, gói VIP,
--                                             mẫu voucher, từ khoá vi phạm, tài khoản,
--                                             kho, báo cáo doanh thu
--
-- -------------------------------------------------------------------- MODERATOR
--   moderator1@rehome.com   MOD000001       - Kho ReHome TP.HCM  (Phạm Thị Thu Hà)
--   moderator2@rehome.com   MOD000002       - Kho ReHome TP.HCM  (Ngô Văn Khánh)
--   moderator3@rehome.com   MOD000003       - Kho ReHome Hà Nội  (Bùi Thị Mai Liên)
--
-- ----------------------------------------------------------------------- MEMBER
--  Email                 Mã        Tên              Ví          Vai trò trong demo
--  member1@rehome.com    MB000001  Nguyễn Văn An      370.500đ  Nhân vật chính: vừa mua
--      vừa ký gửi. Gói THÀNH VIÊN VÀNG đang chạy (mua bằng VÍ), từng mua gói BẠC rồi
--      nâng hạng -> dòng REPLACED. Có 5 voucher (3 chưa dùng, 1 đã dùng, 1 hết hạn),
--      2 địa chỉ giao hàng, giỏ hàng 2 món Ở 2 KHO KHÁC NHAU, 5 đơn hàng, 2 ảnh thử đồ.
--  member2@rehome.com    MB000002  Trần Thị Bình      285.000đ  Người mua ở Hà Nội.
--      Gói BẠC đang chạy (VNPay), 2 voucher chưa dùng, 4 đơn, 1 đơn bị trả hàng do
--      hàng xuống cấp (đã được hoàn 285.000đ).
--  member3@rehome.com    MB000003  Lê Hoàng Cường     105.000đ  Người ký gửi lớn nhất
--      (11 món ở kho HCM). Gói KIM CƯƠNG gói NĂM (MoMo) -> sản phẩm có nhãn NỔI BẬT.
--      Đã rút 300.000đ về ngân hàng (RT-00001 thành công).
--  member4@rehome.com    MB000004  Phạm Thu Dung      272.000đ  Người ký gửi ở Hà Nội
--      (10 món). Đang có yêu cầu rút tiền 200.000đ CHỜ XỬ LÝ -> ví bị khoá 200.000đ,
--      chỉ tiêu được 72.000đ. Có 1 gói GOLD mở cổng rồi bỏ giữa đường (PENDING_PAYMENT).
--  member5@rehome.com    MB000005  Tạ Thị Ngọc Anh    342.000đ  Ký gửi 12 món ở HCM.
--      Gói BẠC ĐÃ HẾT HẠN 20 ngày trước. Có 1 đơn bị tự huỷ do quá hạn thanh toán.
--  member6@rehome.com    MB000006  Võ Minh Đức                0đ  Ký gửi 7 món.
--      Đang có YÊU CẦU RÚT HÀNG chờ duyệt (HCM-AK2-000072), 1 món hết kỳ chờ quyết định,
--      1 món đã tặng từ thiện.
--  member7@rehome.com    MB000007  Đỗ Thị Hạnh        234.000đ  Ký gửi 8 món ở Hà Nội.
--      Vừa nhận 234.000đ ĐỀN BÙ hàng xuống cấp (HN-CV-000057).
--  member8@rehome.com    MB000008  Bùi Quang Huy      115.000đ  Người mua thuần.
--      4 đơn, trong đó 1 đơn CHỜ THANH TOÁN (demo MoMo/VNPay), 1 đơn ĐANG CHỜ ĐÓNG GÓI,
--      1 đơn trả hàng đã tiêu huỷ (được hoàn 115.000đ). Giỏ hàng 2 món.
--  member9@rehome.com    MB000009  Ngô Thị Lan                0đ  Ký gửi 7 món.
--      Đang có YÊU CẦU RÚT HÀNG đã duyệt + ĐÃ TRẢ PHÍ 50.000đ qua MoMo, chờ ra kho nhận.
--      Đã được xác nhận danh tính tại quầy (còn hiệu lực 30 phút).
--  member10@rehome.com   MB000010  Hoàng Văn Nam              0đ  TÀI KHOẢN BỊ KHOÁ -
--      đăng nhập sẽ bị chặn (403), dùng để demo màn Admin quản lý tài khoản.
--
-- ----------------------------------------------------------------- ORGANIZATION
--   org1@rehome.com   Ánh Dương                   - ĐÃ DUYỆT: 3 chiến dịch (2 đang mở,
--       1 đã đóng), 3 bài viết (1 bị gỡ do vi phạm), 3 người theo dõi, 3 lô quyên góp,
--       1 yêu cầu ĐỔI GIẤY TỜ chờ duyệt.
--   org2@rehome.com   CLB Thiện nguyện Sinh viên  - CHỜ DUYỆT: có việc ORG_VERIFICATION
--       trong hàng đợi Moderator.
--   org3@rehome.com   Hy Vọng Mới                 - BỊ TỪ CHỐI kèm lý do, demo nộp lại.
--
-- ########################################################################### #
-- #                      SỐ LIỆU TỔNG QUAN SAU KHI SEED                       #
-- #############################################################################
--   2 kho (TP.HCM + Hà Nội)        18 danh mục       20 thương hiệu
--   17 tài khoản                   4 gói VIP         2 mẫu voucher + 6 luật theo hạng
--   26 tham số hệ thống            6 từ khoá vi phạm
--   73 sản phẩm, trong đó 53 ĐANG BÁN:
--        - 24 áo (tryon_slot = TOP)      áo thun, sơ mi, polo, len, hoodie, khoác,
--                                        blazer, áo kiểu
--        - 17 quần/chân váy (BOTTOM)     jeans, tây, kaki, short, jogger, chân váy
--        - 12 đầm/váy liền/jumpsuit/áo dài (DRESS)
--     (slot gắn theo DANH MỤC nên không bao giờ lệch: áo luôn là TOP, quần luôn là
--      BOTTOM, đầm luôn là DRESS và mặc nguyên bộ)
--   81 phiếu ký gửi (73 đã thành sản phẩm + 8 phiếu ở 7 trạng thái còn lại)
--   73 biên nhận ký gửi
--   14 đơn hàng phủ đủ 8 trạng thái; 11 giao dịch cổng (MoMo/VNPay); 5 sự kiện GHN
--   4 yêu cầu trả hàng (LOST / VALID+xuống cấp / VALID+tiêu huỷ / chờ duyệt)
--   36 bút toán ví + quỹ; 2 yêu cầu rút tiền; 2 yêu cầu rút hàng
--   5 quyết định cuối kỳ (chờ chọn / ký gửi lại / lấy về / vô thừa nhận / tặng)
--   12 việc trong hàng đợi Moderator phủ 9 loại QueueType
--   3 chiến dịch, 3 lô quyên góp, 3 bài viết, 4 bình luận, 4 lượt thích, 3 lượt theo dõi
--   2 báo cáo vi phạm; 12 thông báo; 2 hội thoại + 5 tin nhắn; 5 dòng lịch sử Admin
--   3 ảnh thử đồ AI + 3 phiên thử đồ real-time
-- #############################################################################
