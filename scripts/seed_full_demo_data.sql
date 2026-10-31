-- Seed dữ liệu demo "đủ dùng" để test thủ công (Postman/Swagger) toàn bộ luồng
-- đặt hàng -> thanh toán VNPay -> đóng gói -> vận đơn GHN -> giao -> hoàn tất,
-- cộng với CRUD cơ bản (giỏ hàng, địa chỉ, ký gửi, ví).
--
-- Điều kiện chạy: app đã chạy ít nhất 1 lần (ddl-auto=update đã tạo xong schema).
-- Script cũng tự tạo sequence member_code_seq (bước 0) — Hibernate KHÔNG tạo nó vì
-- MemberCodeGenerator gọi nextval() bằng SQL thô chứ không khai báo @SequenceGenerator,
-- nên DB mới dựng mà thiếu sequence này thì POST /api/auth/register sẽ lỗi.
-- Không phụ thuộc UserInitializer — toàn bộ account (admin/moderator/member) do
-- chính script này tạo, dùng domain @rehome.test để không bao giờ đụng với
-- admin@rehome.com / moderator1-3@rehome.com mà UserInitializer tự seed lúc khởi động.
--
-- Mật khẩu của TẤT CẢ account trong script này: 123456
-- (hash bcrypt đã verify bằng BCrypt.checkpw(), không phải placeholder giả).
--
-- Dữ liệu được ráp khớp nhau theo đúng chuỗi FK bắt buộc của schema:
--   warehouse  -> moderator_profiles.warehouse_id, products.warehouse_id, consignment_forms/receipts.warehouse_id
--   consignment_form -> consignment_receipt -> product -> product_photo
--   order -> order_item -> product ; order -> payment (VNPay) ; order -> order_tracking_event (GHN)
--
-- Chạy lại nhiều lần sẽ lỗi unique constraint (email/order_no/receipt_no/...) — muốn
-- reset thì xoá các dòng seed trước (where email like '%@rehome.test') rồi chạy lại,
-- ví dụ: DELETE FROM accounts WHERE email LIKE '%@rehome.test'; (cascade theo FK).

DO $$
DECLARE
    -- Kho
    v_wh1_id         uuid; -- Kho Quận 7 - TP.HCM
    v_wh2_id         uuid; -- Kho Cầu Giấy - Hà Nội

    -- Account
    v_admin_id       uuid;
    v_mod1_id        uuid; -- moderator1 -> kho 1
    v_mod2_id        uuid; -- moderator2 -> kho 1
    v_mod3_id        uuid; -- moderator3 -> kho 2
    v_buyer1_id      uuid; -- member, địa chỉ ở TP.HCM
    v_buyer2_id      uuid; -- member, địa chỉ ở Hà Nội
    v_seller1_id     uuid;
    v_seller2_id     uuid;
    v_seller3_id     uuid;
    v_org_id         uuid;

    -- Ví
    v_wallet_buyer1  uuid;
    v_wallet_buyer2  uuid;
    v_wallet_seller1 uuid;
    v_wallet_seller2 uuid;
    v_wallet_seller3 uuid;

    -- Địa chỉ giao hàng
    v_sa_buyer1_1    uuid; -- mặc định, TP.HCM
    v_sa_buyer1_2    uuid; -- phụ, TP.HCM
    v_sa_buyer2_1    uuid; -- mặc định, Hà Nội

    -- Thương hiệu / danh mục (mỗi loại 5, dùng chung cho 20 sản phẩm)
    v_brand_ids      uuid[5];
    v_cat_ids        uuid[5];
    v_cat_codes      text[5]  := ARRAY['AK', 'AT', 'QJ', 'CV', 'DM'];

    v_cart_id        uuid;

    v_now            timestamptz := now();
    v_pwd_hash       text := '$2a$10$9oLul/HMTIiG0xkAyG6zQuL9VlDY1gj6f8.Faz1CdLL/4PdfX2dXS'; -- "123456"

    -- 20 sản phẩm: 1-10 ở Kho Quận 7, 11-20 ở Kho Cầu Giấy (F-053: giỏ nhiều kho tách nhiều đơn)
    v_names          text[20] := ARRAY[
        'Áo khoác dạ Uniqlo', 'Áo thun Zara basic', 'Quần jean H&M slimfit', 'Chân váy Nike thể thao',
        'Đầm Adidas năng động', 'Áo khoác kaki Uniqlo', 'Áo thun Zara polo', 'Quần jean H&M ống suông',
        'Chân váy Nike xếp ly', 'Đầm Adidas dự tiệc', 'Áo khoác phao Uniqlo', 'Áo thun Zara basic trắng',
        'Quần jean H&M baggy', 'Chân váy Nike dáng dài', 'Đầm Adidas công sở', 'Áo khoác dạ Uniqlo nữ',
        'Áo thun Zara oversize', 'Quần jean H&M rách gối', 'Chân váy Nike chữ A', 'Đầm Adidas maxi'
    ];
    v_prices         numeric[20] := ARRAY[
        450000, 180000, 320000, 250000, 550000, 480000, 190000, 340000, 260000, 570000,
        430000, 170000, 300000, 270000, 530000, 500000, 200000, 360000, 280000, 600000
    ];
    -- Mặc định ON_SALE; #1 đã bán (Order A), #2/#3 đang RESERVED (Order B/C), #11 vừa đặt chờ thanh toán (Order E)
    v_statuses       text[20] := ARRAY[
        'SOLD', 'RESERVED', 'RESERVED', 'ON_SALE', 'ON_SALE', 'ON_SALE', 'ON_SALE', 'ON_SALE', 'ON_SALE', 'ON_SALE',
        'RESERVED', 'ON_SALE', 'ON_SALE', 'ON_SALE', 'ON_SALE', 'ON_SALE', 'ON_SALE', 'ON_SALE', 'ON_SALE', 'ON_SALE'
    ];
    v_days_ago       int[20]; -- ngày ký gửi, trải từ 29 đến 10 ngày trước, vẫn trong kỳ 1 (<=30 ngày)
    v_seller_ids     uuid[3]; -- gán sau khi tạo seller1/2/3, xoay vòng theo chỉ số sản phẩm

    v_tmp_id         uuid; -- biến tạm: PL/pgSQL không cho "RETURNING ... INTO mang[i]" trực tiếp
    v_product_id     text;
    v_warehouse_id   uuid;
    v_moderator_id   uuid;
    v_seller_id      uuid;
    v_confirmed_at   timestamptz;
    v_form_id        uuid;
    v_receipt_id     uuid;
    i                int;

    -- Order A..E minh hoạ 5 trạng thái/luồng khác nhau (xem ghi chú cuối file)
    v_order_a_id     uuid;
    v_order_b_id     uuid;
    v_order_c_id     uuid;
    v_order_d_id     uuid;
    v_order_e_id     uuid;
    v_oi_c_id        uuid;
BEGIN
    ------------------------------------------------------------------
    -- 0. Sequence sinh member_code — MemberCodeGenerator chạy 'SELECT nextval(...)'
    --    ở mỗi INSERT member_profiles, định dạng MB + 6 chữ số (MB000001).
    ------------------------------------------------------------------
    CREATE SEQUENCE IF NOT EXISTS member_code_seq START 1;

    ------------------------------------------------------------------
    -- 1. Kho — đủ ward_name/province_name/is_new_address để GHN tạo vận đơn chạy được
    ------------------------------------------------------------------
    -- Phường Tân Phú (Q.7 cũ) đã sáp nhập thành Phường Tân Mỹ theo NQ 1685/NQ-UBTVQH15 (hiệu lực 01/07/2025)
    INSERT INTO warehouses (id, name, address, phone, latitude, longitude, opening_hours, status,
                             code, ward_name, district_name, province_name, is_new_address, created_at)
    VALUES (gen_random_uuid(), 'Địa điểm ký gửi - TPHCM', '123 Nguyễn Thị Thập, Phường Tân Phú', '02838225566',
            10.7329000, 106.7240000, '08:00 - 20:00 (Thứ 2 - Chủ nhật)', 'ACTIVE',
            'WH-Q7', 'Phường Tân Mỹ', NULL, 'Hồ Chí Minh', true, v_now)
    RETURNING id INTO v_wh1_id;

    -- Phường Dịch Vọng Hậu (Cầu Giấy cũ, khu vực Xuân Thủy) đã sáp nhập thành Phường Cầu Giấy
    INSERT INTO warehouses (id, name, address, phone, latitude, longitude, opening_hours, status,
                             code, ward_name, district_name, province_name, is_new_address, created_at)
    VALUES (gen_random_uuid(), 'Địa điểm ký gửi - Hà Nội', '45 Xuân Thủy, Phường Dịch Vọng Hậu', '02437912345',
            21.0368000, 105.7828000, '08:00 - 20:00 (Thứ 2 - Chủ nhật)', 'ACTIVE',
            'WH-CG', 'Phường Cầu Giấy', NULL, 'Hà Nội', true, v_now)
    RETURNING id INTO v_wh2_id;

    ------------------------------------------------------------------
    -- 2. Tài khoản (mật khẩu chung: 123456) — admin + 3 moderator (2 ở kho 1, 1 ở kho 2) + 5 member
    ------------------------------------------------------------------
    INSERT INTO accounts (id, email, password_hash, phone, role, status, email_verified, created_at, updated_at)
    VALUES (gen_random_uuid(), 'admin1@rehome.test', v_pwd_hash, '0922222201', 'ADMIN', 'ACTIVE', true, v_now, v_now)
    RETURNING id INTO v_admin_id;

    INSERT INTO accounts (id, email, password_hash, phone, role, status, email_verified, warehouse_id, created_at, updated_at)
    VALUES (gen_random_uuid(), 'moderator1@rehome.test', v_pwd_hash, '0922222202', 'MODERATOR', 'ACTIVE', true, v_wh1_id, v_now, v_now)
    RETURNING id INTO v_mod1_id;

    INSERT INTO accounts (id, email, password_hash, phone, role, status, email_verified, warehouse_id, created_at, updated_at)
    VALUES (gen_random_uuid(), 'moderator2@rehome.test', v_pwd_hash, '0922222203', 'MODERATOR', 'ACTIVE', true, v_wh1_id, v_now, v_now)
    RETURNING id INTO v_mod2_id;

    INSERT INTO accounts (id, email, password_hash, phone, role, status, email_verified, warehouse_id, created_at, updated_at)
    VALUES (gen_random_uuid(), 'moderator3@rehome.test', v_pwd_hash, '0922222204', 'MODERATOR', 'ACTIVE', true, v_wh2_id, v_now, v_now)
    RETURNING id INTO v_mod3_id;

    INSERT INTO accounts (id, email, password_hash, phone, role, status, email_verified, created_at, updated_at)
    VALUES (gen_random_uuid(), 'buyer1@rehome.test', v_pwd_hash, '0911111101', 'MEMBER', 'ACTIVE', true, v_now, v_now)
    RETURNING id INTO v_buyer1_id;

    INSERT INTO accounts (id, email, password_hash, phone, role, status, email_verified, created_at, updated_at)
    VALUES (gen_random_uuid(), 'buyer2@rehome.test', v_pwd_hash, '0911111102', 'MEMBER', 'ACTIVE', true, v_now, v_now)
    RETURNING id INTO v_buyer2_id;

    INSERT INTO accounts (id, email, password_hash, phone, role, status, email_verified, created_at, updated_at)
    VALUES (gen_random_uuid(), 'seller1@rehome.test', v_pwd_hash, '0911111103', 'MEMBER', 'ACTIVE', true, v_now, v_now)
    RETURNING id INTO v_seller1_id;

    INSERT INTO accounts (id, email, password_hash, phone, role, status, email_verified, created_at, updated_at)
    VALUES (gen_random_uuid(), 'seller2@rehome.test', v_pwd_hash, '0911111104', 'MEMBER', 'ACTIVE', true, v_now, v_now)
    RETURNING id INTO v_seller2_id;

    INSERT INTO accounts (id, email, password_hash, phone, role, status, email_verified, created_at, updated_at)
    VALUES (gen_random_uuid(), 'seller3@rehome.test', v_pwd_hash, '0911111105', 'MEMBER', 'ACTIVE', true, v_now, v_now)
    RETURNING id INTO v_seller3_id;

    INSERT INTO accounts (id, email, password_hash, phone, role, status, email_verified, created_at, updated_at)
    VALUES (gen_random_uuid(), 'org1@rehome.test', v_pwd_hash, '0911111106', 'ORGANIZATION', 'ACTIVE', true, v_now, v_now)
    RETURNING id INTO v_org_id;

    v_seller_ids := ARRAY[v_seller1_id, v_seller2_id, v_seller3_id];

    INSERT INTO moderator_profiles (account_id, full_name, moderator_code, warehouse_id) VALUES
        (v_mod1_id, 'Phạm Thị Moderator 1', 'MOD-SEED-0001', v_wh1_id),
        (v_mod2_id, 'Ngô Văn Moderator 2',  'MOD-SEED-0002', v_wh1_id),
        (v_mod3_id, 'Bùi Thị Moderator 3',  'MOD-SEED-0003', v_wh2_id);

    -- member_code dùng đúng định dạng MemberCodeGenerator sinh ra (MB + 6 chữ số), lấy
    -- dải 0009xx để QR tra cứu tại quầy (API 25) test được với dữ liệu giống thật.
    INSERT INTO member_profiles (account_id, full_name, avatar_url, member_code) VALUES
        (v_buyer1_id,  'Nguyễn Văn Buyer 1', NULL, 'MB000901'),
        (v_buyer2_id,  'Đặng Thị Buyer 2',   NULL, 'MB000902'),
        (v_seller1_id, 'Trần Thị Seller 1',  NULL, 'MB000903'),
        (v_seller2_id, 'Lê Văn Seller 2',    NULL, 'MB000904'),
        (v_seller3_id, 'Phạm Thị Seller 3',  NULL, 'MB000905');

    -- Đẩy sequence lên trên mã lớn nhất đang có, nếu không thì member đăng ký thật
    -- sẽ bò dần tới MB000901 và chết ở unique constraint member_code.
    PERFORM setval('member_code_seq',
        (SELECT COALESCE(MAX(SUBSTRING(member_code FROM 3)::bigint), 0) + 1
         FROM member_profiles
         WHERE member_code ~ '^MB[0-9]+$'),
        false);

    INSERT INTO organization_profiles (
        account_id, legal_name, display_name, org_type, tax_code, operation_scope,
        address, contact_phone, contact_email, website, description, logo_url,
        verification_status, rejection_reason, verified_by, verified_at,
        donation_point_name, donation_point_address, donation_point_latitude, donation_point_longitude,
        donation_point_receiving_hours, donation_point_contact_phone,
        full_name, position, cccd_number, cccd_front_url, cccd_back_url
    ) VALUES (
        v_org_id, 'Tổ chức Thiện Nguyện ABC', 'ABC Charity', 'NGO', NULL, 'TP.HCM',
        '10 Nguyễn Huệ, Phường Bến Nghé, TP.HCM', '0911111106', 'org1@rehome.test', NULL,
        'Tổ chức thiện nguyện tiếp nhận quần áo cũ, seed cho mục đích test API.', NULL,
        'VERIFIED', NULL, v_mod1_id, v_now - interval '5 days',
        'Điểm nhận đồ ABC', '10 Nguyễn Huệ, Phường Bến Nghé, TP.HCM', 10.7745000, 106.7030000,
        '08:00 - 17:00 (Thứ 2 - Thứ 6)', '0911111106',
        'Hoàng Văn Đại Diện', 'Giám đốc', '079123456789', NULL, NULL
    );

    ------------------------------------------------------------------
    -- 3. Ví — số dư khớp với wallet_transactions sẽ insert ở bước 8
    ------------------------------------------------------------------
    INSERT INTO wallets (id, member_id, balance, locked_balance, updated_at)
    VALUES (gen_random_uuid(), v_buyer1_id, 5000000, 0, v_now) RETURNING id INTO v_wallet_buyer1;
    INSERT INTO wallets (id, member_id, balance, locked_balance, updated_at)
    VALUES (gen_random_uuid(), v_buyer2_id, 3000000, 0, v_now) RETURNING id INTO v_wallet_buyer2;
    INSERT INTO wallets (id, member_id, balance, locked_balance, updated_at)
    VALUES (gen_random_uuid(), v_seller1_id, 405000, 0, v_now) RETURNING id INTO v_wallet_seller1;
    INSERT INTO wallets (id, member_id, balance, locked_balance, updated_at)
    VALUES (gen_random_uuid(), v_seller2_id, 0, 0, v_now) RETURNING id INTO v_wallet_seller2;
    INSERT INTO wallets (id, member_id, balance, locked_balance, updated_at)
    VALUES (gen_random_uuid(), v_seller3_id, 0, 0, v_now) RETURNING id INTO v_wallet_seller3;

    ------------------------------------------------------------------
    -- 4. Địa chỉ giao hàng — đủ ward_name/province_name để GHN tạo vận đơn
    ------------------------------------------------------------------
    -- ward_code / province_id để NULL: đây là mã trong danh mục GHN, chỉ lấy được khi gọi
    -- GET /address-units/provinces|wards với token thật, bịa số vào đây sẽ khiến FE hiển thị
    -- sai mục đã chọn. Cả hai cột đều nullable và việc tạo vận đơn đi theo tên, nên không sao;
    -- địa chỉ nào Member tự thêm/sửa qua API sẽ được điền đầy đủ.
    -- Phường Bến Nghé (Q.1 cũ) đã sáp nhập thành Phường Sài Gòn theo NQ 1685/NQ-UBTVQH15
    INSERT INTO shipping_addresss (id, member_id, recipient_name, phone, address_detail,
                                    ward_name, province_name, is_default)
    VALUES (gen_random_uuid(), v_buyer1_id, 'Nguyễn Văn Buyer 1', '0911111101',
            '12 Nguyễn Huệ, Phường Bến Nghé', 'Phường Sài Gòn', 'Hồ Chí Minh', true)
    RETURNING id INTO v_sa_buyer1_1;

    -- Phường Bến Thành (Q.1 cũ) giữ nguyên tên sau sáp nhập (gộp thêm Phạm Ngũ Lão + một phần Cầu Ông Lãnh, Nguyễn Thái Bình)
    INSERT INTO shipping_addresss (id, member_id, recipient_name, phone, address_detail,
                                    ward_name, province_name, is_default)
    VALUES (gen_random_uuid(), v_buyer1_id, 'Nguyễn Văn Buyer 1 (Công ty)', '0911111199',
            '45 Lê Lợi, Phường Bến Thành', 'Phường Bến Thành', 'Hồ Chí Minh', false)
    RETURNING id INTO v_sa_buyer1_2;

    -- Phường Dịch Vọng (Cầu Giấy cũ) đã sáp nhập thành Phường Cầu Giấy
    INSERT INTO shipping_addresss (id, member_id, recipient_name, phone, address_detail,
                                    ward_name, province_name, is_default)
    VALUES (gen_random_uuid(), v_buyer2_id, 'Đặng Thị Buyer 2', '0911111102',
            '78 Trần Thái Tông, Phường Dịch Vọng', 'Phường Cầu Giấy', 'Hà Nội', true)
    RETURNING id INTO v_sa_buyer2_1;

    ------------------------------------------------------------------
    -- 5. Thương hiệu & danh mục (5 + 5, dùng chung cho 20 sản phẩm theo chỉ số xoay vòng)
    ------------------------------------------------------------------
    INSERT INTO brands (id, name, status) VALUES (gen_random_uuid(), 'Uniqlo', 'ACTIVE') RETURNING id INTO v_tmp_id; v_brand_ids[1] := v_tmp_id;
    INSERT INTO brands (id, name, status) VALUES (gen_random_uuid(), 'Zara', 'ACTIVE') RETURNING id INTO v_tmp_id; v_brand_ids[2] := v_tmp_id;
    INSERT INTO brands (id, name, status) VALUES (gen_random_uuid(), 'H&M', 'ACTIVE') RETURNING id INTO v_tmp_id; v_brand_ids[3] := v_tmp_id;
    INSERT INTO brands (id, name, status) VALUES (gen_random_uuid(), 'Nike', 'ACTIVE') RETURNING id INTO v_tmp_id; v_brand_ids[4] := v_tmp_id;
    INSERT INTO brands (id, name, status) VALUES (gen_random_uuid(), 'Adidas', 'ACTIVE') RETURNING id INTO v_tmp_id; v_brand_ids[5] := v_tmp_id;

    INSERT INTO categories (id, name, tryon_slot, status, code)
    VALUES (gen_random_uuid(), 'Áo khoác', 'TOP', 'ACTIVE', 'AK') RETURNING id INTO v_tmp_id; v_cat_ids[1] := v_tmp_id;
    INSERT INTO categories (id, name, tryon_slot, status, code)
    VALUES (gen_random_uuid(), 'Áo thun', 'TOP', 'ACTIVE', 'AT') RETURNING id INTO v_tmp_id; v_cat_ids[2] := v_tmp_id;
    INSERT INTO categories (id, name, tryon_slot, status, code)
    VALUES (gen_random_uuid(), 'Quần jean', 'BOTTOM', 'ACTIVE', 'QJ') RETURNING id INTO v_tmp_id; v_cat_ids[3] := v_tmp_id;
    INSERT INTO categories (id, name, tryon_slot, status, code)
    VALUES (gen_random_uuid(), 'Chân váy', 'BOTTOM', 'ACTIVE', 'CV') RETURNING id INTO v_tmp_id; v_cat_ids[4] := v_tmp_id;
    INSERT INTO categories (id, name, tryon_slot, status, code)
    VALUES (gen_random_uuid(), 'Đầm', 'DRESS', 'ACTIVE', 'DM') RETURNING id INTO v_tmp_id; v_cat_ids[5] := v_tmp_id;

    ------------------------------------------------------------------
    -- 6. 20 sản phẩm: #1-10 ở Kho Quận 7, #11-20 ở Kho Cầu Giấy
    --    chuỗi FK bắt buộc: consignment_form -> consignment_receipt -> product -> product_photo
    ------------------------------------------------------------------
    FOR i IN 1..20 LOOP
        v_days_ago[i]    := 30 - i; -- trải 29..10 ngày trước, vẫn trong kỳ 1 (<=30 ngày)
        v_confirmed_at   := v_now - (v_days_ago[i] || ' days')::interval;
        v_warehouse_id   := CASE WHEN i <= 10 THEN v_wh1_id ELSE v_wh2_id END;
        v_moderator_id   := CASE WHEN i > 10 THEN v_mod3_id
                                  WHEN i % 2 = 1 THEN v_mod1_id
                                  ELSE v_mod2_id END;
        v_seller_id      := v_seller_ids[((i - 1) % 3) + 1];
        v_product_id     := (CASE WHEN i <= 10 THEN 'Q7-' ELSE 'CG-' END)
                                || v_cat_codes[((i - 1) % 5) + 1] || '-' || lpad(i::text, 6, '0');

        INSERT INTO consignment_forms (id, member_id, warehouse_id, category_id, brand_id,
                                        product_name, description, desired_price, status,
                                        reject_reason, bring_in_deadline, created_at, updated_at)
        VALUES (gen_random_uuid(), v_seller_id, v_warehouse_id, v_cat_ids[((i - 1) % 5) + 1], v_brand_ids[((i - 1) % 5) + 1],
                v_names[i], 'Hàng còn mới, ít sử dụng (seed data)', v_prices[i], 'APPROVED',
                NULL, (v_confirmed_at - interval '1 day')::date + 7,
                v_confirmed_at - interval '1 day', v_confirmed_at - interval '1 day')
        RETURNING id INTO v_form_id;

        -- Không có pdf_url: PDF biên nhận (F-022) đã chuyển sang bảng consignment_receipt_pdfs,
        -- cột pdf_url không còn trong entity nên DB mới tạo sẽ không có cột này.
        -- Seed không sinh PDF (phải render bằng ReceiptPdfServiceImpl), nên GET
        -- /consignment-receipts/{id}/pdf của các biên nhận seed sẽ trả pdfAvailable = false.
        INSERT INTO consignment_receipts (id, receipt_no, consignment_form_id, member_id, moderator_id,
                                           warehouse_id, agreed_price, floor_price, term_length_days,
                                           max_renewals, free_withdrawal_window_days,
                                           commission_rate_term1, commission_rate_term2, commission_rate_term3,
                                           withdrawal_fee, degraded_compensation_rate, confirmed_at,
                                           condition_note, created_at)
        VALUES (gen_random_uuid(), 'KG-2026-SEED' || lpad(i::text, 2, '0'), v_form_id, v_seller_id, v_moderator_id,
                v_warehouse_id, v_prices[i], round(v_prices[i] * 0.5), 30, 2, 7,
                0.10, 0.15, 0.20, 50000, 0.50, v_confirmed_at,
                'Hàng đã qua kiểm định tại kho, tình trạng 85-95%, không lỗi, không phai màu.', v_confirmed_at)
        RETURNING id INTO v_receipt_id;

        INSERT INTO products (id, consignment_receipt_id, member_id, warehouse_id, category_id, brand_id,
                               name, size, condition_note, current_price, status, current_term,
                               term_started_at, end_of_term_deadline, unclaimed_decision_deadline,
                               shelf_location, created_at, updated_at)
        VALUES (v_product_id, v_receipt_id, v_seller_id, v_warehouse_id, v_cat_ids[((i - 1) % 5) + 1], v_brand_ids[((i - 1) % 5) + 1],
                v_names[i], 'M', 'Hàng đã qua kiểm định tại kho, tình trạng 85-95%, không lỗi, không phai màu.',
                v_prices[i], v_statuses[i], 1,
                v_confirmed_at, v_confirmed_at + interval '90 days', NULL,
                'A1-' || lpad(i::text, 2, '0'), v_confirmed_at, v_now);

        INSERT INTO product_photos (id, product_id, image_url, is_background_removed, display_order)
        VALUES (gen_random_uuid(), v_product_id, 'https://picsum.photos/seed/' || v_product_id || '/600/800', false, 0);
    END LOOP;

    -- name_normalized là cột GET /products?search= so khớp (gõ "ao khoac" ra "Áo khoác").
    -- App tự sinh cột này trong @PrePersist/@PreUpdate của entity Product, nhưng seed ghi
    -- thẳng vào DB nên không chạy qua Hibernate -> phải tự điền, nếu không 20 sản phẩm seed
    -- sẽ không bao giờ hiện ra khi tìm kiếm.
    -- Công thức phải khớp VietnameseText.normalize: lower() trước rồi bỏ dấu (gồm cả dấu móc
    -- của ơ/ư), riêng đ -> d vì đ là ký tự độc lập chứ không phải chữ có dấu.
    UPDATE products
    SET name_normalized = translate(
            lower(name),
            'àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ',
            'aaaaaaaaaaaaaaaaaeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyyd')
    WHERE name_normalized IS NULL;

    ------------------------------------------------------------------
    -- 7. Giỏ hàng của buyer1: 1 món Kho Quận 7 (#7) + 1 món Kho Cầu Giấy (#16)
    --    -> test "checkout từ giỏ" sẽ tách thành 2 đơn khác kho (F-053)
    ------------------------------------------------------------------
    INSERT INTO carts (id, member_id) VALUES (gen_random_uuid(), v_buyer1_id) RETURNING id INTO v_cart_id;

    INSERT INTO cart_items (id, cart_id, product_id, added_at) VALUES
        (gen_random_uuid(), v_cart_id, 'Q7-AT-000007', v_now - interval '1 hour'),
        (gen_random_uuid(), v_cart_id, 'CG-AK-000016', v_now - interval '30 minutes');

    ------------------------------------------------------------------
    -- 8. Đơn hàng — 5 đơn minh hoạ 5 bước trong luồng đặt hàng/thanh toán/giao hàng
    ------------------------------------------------------------------

    -- Order A: COMPLETED — #1 (seller1, 450000đ), đã confirm-receipt -> có WalletTransaction ORDER_PAYOUT
    -- Test: GET /orders/{id}, GET /orders?status=COMPLETED, POST /orders/{id}/rating
    -- payout_at = completed_at: đơn này coi như đã được OrderAutoCompleteServiceImpl trả tiền từ
    -- trước rồi (WalletTransaction ORDER_PAYOUT bên dưới là bằng chứng). Không set thì job sẽ thấy
    -- payout_at NULL + status COMPLETED + đã quá auto_complete_deadline và trả tiền LẦN THỨ HAI.
    INSERT INTO orders (id, order_no, buyer_id, warehouse_id, shipping_address_id,
                         shipping_recipient_name, shipping_phone, shipping_address_detail,
                         shipping_ward_name, shipping_district_name, shipping_province_name, shipping_is_new_address,
                         scanned_count, subtotal, shipping_fee, total_amount, payment_method, status,
                         ghn_tracking_code, payment_deadline, delivered_at, auto_complete_deadline,
                         cancel_reason, cancelled_at, refund_amount, completed_at, payout_at,
                         service_rating, rated_at, rating_comment, created_at, updated_at)
    VALUES (gen_random_uuid(), 'OD-2026-SEED01', v_buyer1_id, v_wh1_id, v_sa_buyer1_1,
            'Nguyễn Văn Buyer 1', '0911111101', '12 Nguyễn Huệ, Phường Bến Nghé',
            'Phường Sài Gòn', NULL, 'Hồ Chí Minh', true,
            1, 450000, 25000, 475000, 'WALLET', 'COMPLETED',
            NULL, v_now - interval '6 days' + interval '15 minutes',
            v_now - interval '2 days', v_now - interval '2 days' + interval '3 days',
            NULL, NULL, NULL, v_now - interval '1 day', v_now - interval '1 day',
            NULL, NULL, NULL, v_now - interval '6 days', v_now - interval '1 day')
    RETURNING id INTO v_order_a_id;

    INSERT INTO order_items (id, order_id, product_id, price_at_order, commission_rate_applied,
                              tax_rate_applied, term_applied, scanned)
    VALUES (gen_random_uuid(), v_order_a_id, 'Q7-AK-000001', 450000, 0.10, 0, 1, true);

    INSERT INTO wallet_transactions (id, transaction_no, wallet_type, wallet_id, type, amount,
                                      balance_after, related_order_id, related_order_item_id,
                                      counterpart_transaction_id, status, note, created_at)
    VALUES (gen_random_uuid(), 'WT-2026-SEED01', 'MEMBER', v_wallet_seller1, 'ORDER_PAYOUT', 405000,
            405000, v_order_a_id, NULL, NULL, 'SUCCESS',
            'Thanh toán đơn hàng OD-2026-SEED01', v_now - interval '1 day');

    -- Order B: PAID (ví) — #2 (seller2, 180000đ), chưa đóng gói -> test POST /orders/{id}/pack/scan, /cancel
    INSERT INTO orders (id, order_no, buyer_id, warehouse_id, shipping_address_id,
                         shipping_recipient_name, shipping_phone, shipping_address_detail,
                         shipping_ward_name, shipping_district_name, shipping_province_name, shipping_is_new_address,
                         scanned_count, subtotal, shipping_fee, total_amount, payment_method, status,
                         ghn_tracking_code, payment_deadline, delivered_at, auto_complete_deadline,
                         cancel_reason, cancelled_at, refund_amount, completed_at,
                         service_rating, rated_at, rating_comment, created_at, updated_at)
    VALUES (gen_random_uuid(), 'OD-2026-SEED02', v_buyer1_id, v_wh1_id, v_sa_buyer1_1,
            'Nguyễn Văn Buyer 1', '0911111101', '12 Nguyễn Huệ, Phường Bến Nghé',
            'Phường Sài Gòn', NULL, 'Hồ Chí Minh', true,
            0, 180000, 25000, 205000, 'WALLET', 'PAID',
            NULL, v_now - interval '2 hours' + interval '15 minutes', NULL, NULL,
            NULL, NULL, NULL, NULL,
            NULL, NULL, NULL, v_now - interval '2 hours', v_now - interval '2 hours')
    RETURNING id INTO v_order_b_id;

    INSERT INTO order_items (id, order_id, product_id, price_at_order, commission_rate_applied,
                              tax_rate_applied, term_applied, scanned)
    VALUES (gen_random_uuid(), v_order_b_id, 'Q7-AT-000002', 180000, 0.10, 0, 1, false);

    -- Order C: PAID qua VNPay, đã tạo vận đơn GHN -> SHIPPING — #3 (seller3, 320000đ)
    -- Test: GET /orders/{id}/tracking, POST /webhooks/ghn (status=delivered) để chuyển DELIVERED
    INSERT INTO orders (id, order_no, buyer_id, warehouse_id, shipping_address_id,
                         shipping_recipient_name, shipping_phone, shipping_address_detail,
                         shipping_ward_name, shipping_district_name, shipping_province_name, shipping_is_new_address,
                         scanned_count, subtotal, shipping_fee, total_amount, payment_method, status,
                         ghn_tracking_code, expected_delivery_date, payment_deadline, delivered_at,
                         auto_complete_deadline, cancel_reason, cancelled_at, refund_amount, completed_at,
                         service_rating, rated_at, rating_comment, created_at, updated_at)
    VALUES (gen_random_uuid(), 'OD-2026-SEED03', v_buyer1_id, v_wh1_id, v_sa_buyer1_1,
            'Nguyễn Văn Buyer 1', '0911111101', '12 Nguyễn Huệ, Phường Bến Nghé',
            'Phường Sài Gòn', NULL, 'Hồ Chí Minh', true,
            1, 320000, 25000, 345000, 'WALLET', 'SHIPPING',
            'GHNSEED00001', v_now + interval '1 day',
            v_now - interval '2 days' + interval '15 minutes', NULL,
            NULL, NULL, NULL, NULL, NULL,
            NULL, NULL, NULL, v_now - interval '2 days', v_now - interval '6 hours')
    RETURNING id INTO v_order_c_id;

    INSERT INTO order_items (id, order_id, product_id, price_at_order, commission_rate_applied,
                              tax_rate_applied, term_applied, scanned)
    VALUES (gen_random_uuid(), v_order_c_id, 'Q7-QJ-000003', 320000, 0.10, 0, 1, true)
    RETURNING id INTO v_oi_c_id;

    INSERT INTO payments (id, order_id, amount, method, momo_transaction_id, momo_order_id, momo_request_id,
                           vnp_txn_ref, vnp_transaction_no, vnp_create_date, status, paid_at)
    VALUES (gen_random_uuid(), v_order_c_id, 345000, 'VNPAY', NULL, NULL, NULL,
            v_order_c_id::text || '-seed03', '14000555',
            to_char((v_now - interval '2 days') AT TIME ZONE 'Asia/Ho_Chi_Minh', 'YYYYMMDDHH24MISS'),
            'SUCCESS', v_now - interval '2 days' + interval '3 minutes');

    -- location = kho GHN đang giữ kiện hàng, hiện trên màn theo dõi vận chuyển.
    -- Sự kiện mới nhất là 'delivering' (trạng thái bình thường) nên GET /orders/{id}/tracking
    -- sẽ trả hasIssue = false; đổi thành 'delivery_fail' để test nhánh có sự cố.
    INSERT INTO order_tracking_events (id, order_id, ghn_status, description, location, occurred_at, created_at) VALUES
        (gen_random_uuid(), v_order_c_id, 'ready_to_pick', 'Đã lấy hàng từ kho', 'Kho Quận 7 - TP.HCM', v_now - interval '1 day 12 hours', v_now - interval '1 day 12 hours'),
        (gen_random_uuid(), v_order_c_id, 'delivering', 'Đang giao cho người nhận', 'Bưu cục Quận 1 - TP.HCM', v_now - interval '6 hours', v_now - interval '6 hours');

    -- Order D: CANCELLED — #4 (seller1, 250000đ), buyer tự hủy -> đã hoàn tiền vào ví, hàng nhả về ON_SALE
    -- Test: GET /orders?status=CANCELLED, xem WalletTransaction REFUND
    INSERT INTO orders (id, order_no, buyer_id, warehouse_id, shipping_address_id,
                         shipping_recipient_name, shipping_phone, shipping_address_detail,
                         shipping_ward_name, shipping_district_name, shipping_province_name, shipping_is_new_address,
                         scanned_count, subtotal, shipping_fee, total_amount, payment_method, status,
                         ghn_tracking_code, payment_deadline, delivered_at, auto_complete_deadline,
                         cancel_reason, cancelled_at, refund_amount, completed_at,
                         service_rating, rated_at, rating_comment, created_at, updated_at)
    VALUES (gen_random_uuid(), 'OD-2026-SEED04', v_buyer1_id, v_wh1_id, v_sa_buyer1_1,
            'Nguyễn Văn Buyer 1', '0911111101', '12 Nguyễn Huệ, Phường Bến Nghé',
            'Phường Sài Gòn', NULL, 'Hồ Chí Minh', true,
            0, 250000, 25000, 275000, 'WALLET', 'CANCELLED',
            NULL, v_now - interval '3 days 1 hour' + interval '15 minutes', NULL, NULL,
            'BUYER_CANCELLED', v_now - interval '3 days', 275000, NULL,
            NULL, NULL, NULL, v_now - interval '3 days 1 hour', v_now - interval '3 days')
    RETURNING id INTO v_order_d_id;

    INSERT INTO order_items (id, order_id, product_id, price_at_order, commission_rate_applied,
                              tax_rate_applied, term_applied, scanned)
    VALUES (gen_random_uuid(), v_order_d_id, 'Q7-CV-000004', 250000, 0.10, 0, 1, false);

    INSERT INTO wallet_transactions (id, transaction_no, wallet_type, wallet_id, type, amount,
                                      balance_after, related_order_id, related_order_item_id,
                                      counterpart_transaction_id, status, note, created_at)
    VALUES (gen_random_uuid(), 'WT-2026-SEED02', 'MEMBER', v_wallet_buyer1, 'REFUND', 275000,
            5000000, v_order_d_id, NULL, NULL, 'SUCCESS',
            'Hoàn tiền hủy đơn OD-2026-SEED04', v_now - interval '3 days');

    -- Order E: PENDING_PAYMENT, mới tạo, CHƯA thanh toán — #11 (seller2, 430000đ), buyer2 ở Hà Nội
    -- Test: POST /api/vnpay/create (hoặc /api/momo/create) với body {"orderIds":["<id-đơn-này>"]}
    --       để chạy lại luồng cổng thanh toán từ đầu; hoặc POST /orders/pay với
    --       {"orderIds":["<id-đơn-này>"],"method":"WALLET"} để trả bằng ví (buyer2 có 3.000.000đ)
    INSERT INTO orders (id, order_no, buyer_id, warehouse_id, shipping_address_id,
                         shipping_recipient_name, shipping_phone, shipping_address_detail,
                         shipping_ward_name, shipping_district_name, shipping_province_name, shipping_is_new_address,
                         scanned_count, subtotal, shipping_fee, total_amount, payment_method, status,
                         ghn_tracking_code, payment_deadline, delivered_at, auto_complete_deadline,
                         cancel_reason, cancelled_at, refund_amount, completed_at,
                         service_rating, rated_at, rating_comment, created_at, updated_at)
    VALUES (gen_random_uuid(), 'OD-2026-SEED05', v_buyer2_id, v_wh2_id, v_sa_buyer2_1,
            'Đặng Thị Buyer 2', '0911111102', '78 Trần Thái Tông, Phường Dịch Vọng',
            'Phường Cầu Giấy', NULL, 'Hà Nội', true,
            0, 430000, 25000, 455000, 'WALLET', 'PENDING_PAYMENT',
            NULL, v_now + interval '15 minutes', NULL, NULL,
            NULL, NULL, NULL, NULL,
            NULL, NULL, NULL, v_now, v_now)
    RETURNING id INTO v_order_e_id;

    INSERT INTO order_items (id, order_id, product_id, price_at_order, commission_rate_applied,
                              tax_rate_applied, term_applied, scanned)
    VALUES (gen_random_uuid(), v_order_e_id, 'CG-AK-000011', 430000, 0.10, 0, 1, false);

END $$;

-- ============================================================================
-- Tài khoản test (mật khẩu chung: 123456, domain @rehome.test, tự chứa không cần UserInitializer)
--   admin1@rehome.test      ADMIN
--   moderator1@rehome.test  MODERATOR  — phụ trách "Kho Quận 7 - TP.HCM"
--   moderator2@rehome.test  MODERATOR  — phụ trách "Kho Quận 7 - TP.HCM"
--   moderator3@rehome.test  MODERATOR  — phụ trách "Kho Cầu Giấy - Hà Nội"
--   buyer1@rehome.test      MEMBER  MB000901  — ví 5.000.000đ, 2 địa chỉ (TP.HCM), giỏ có 2 món (2 kho khác nhau)
--   buyer2@rehome.test      MEMBER  MB000902  — ví 3.000.000đ, 1 địa chỉ (Hà Nội), có đơn OD-2026-SEED05 chờ thanh toán
--   seller1@rehome.test     MEMBER  MB000903  — ví 405.000đ (đã nhận payout từ OD-2026-SEED01)
--   seller2@rehome.test     MEMBER  MB000904  — ví 0đ
--   seller3@rehome.test     MEMBER  MB000905  — ví 0đ
--   org1@rehome.test        ORGANIZATION — hồ sơ VERIFIED
--
-- Kho:
--   Kho Quận 7 - TP.HCM     — sản phẩm #1-10 (SKU Q7-xx-0000NN)
--   Kho Cầu Giấy - Hà Nội   — sản phẩm #11-20 (SKU CG-xx-0000NN)
--
-- Đơn hàng (buyer1 trừ OD-2026-SEED05 là của buyer2):
--   OD-2026-SEED01  COMPLETED           — test GET /orders/{id}, POST /orders/{id}/rating
--   OD-2026-SEED02  PAID (ví)           — test POST /orders/{id}/pack/scan rồi /shipment
--   OD-2026-SEED03  SHIPPING (VNPay)    — có payments(VNPAY) + ghn_tracking_code GHNSEED00001 + 2 tracking event
--                                          -> test GET /orders/{id}/tracking, POST /webhooks/ghn (status=delivered)
--   OD-2026-SEED04  CANCELLED           — đã hoàn 275.000đ vào ví buyer1 (WalletTransaction REFUND)
--   OD-2026-SEED05  PENDING_PAYMENT     — test POST /api/vnpay/create | /api/momo/create với
--                                          body {"orderIds":[...]}, hoặc POST /orders/pay bằng ví,
--                                          hoặc POST /orders/{id}/cancel (huỷ đơn chưa thanh toán)
--
-- Tra cứu Member tại quầy (API 25, POST /counter/lookup, role MODERATOR) — gửi đúng 1 field:
--   {"memberCode": "MB000901"}                       -> 1 kết quả
--   {"phone": "0911111101"}                          -> 1 kết quả (buyer1)
--   {"qrPayload": "REHOME:MEMBER:v1:MB000901"}       -> 1 kết quả, giống hệt quét QR thật
--   {"qrPayload": "REHOME:PRODUCT:v1:Q7-AK-000001"}  -> 400 QR_PAYLOAD_INVALID (sai tiền tố)
-- Chuỗi QR không nằm trong DB: GET /members/me/code tính ra từ member_code mỗi lần gọi.
--
-- Sản phẩm ON_SALE còn trống để test thêm giỏ hàng / đặt đơn mới: tất cả trừ
-- Q7-AK-000001 (SOLD), Q7-AT-000002 (RESERVED), Q7-QJ-000003 (RESERVED), CG-AK-000011 (RESERVED).
-- ============================================================================
