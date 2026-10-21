-- Seed dữ liệu mẫu "đủ dùng" để test API thủ công (Postman/Swagger), phủ hầu hết
-- các bảng đứng sau các API đã implement: accounts + 3 loại profile, wallets,
-- shipping_addresss, brands, categories, consignment_forms/receipts, products,
-- carts/cart_items, orders/order_items, wallet_transactions, payments.
--
-- Điều kiện chạy: app đã chạy ít nhất 1 lần (ddl-auto=update đã tạo xong schema).
-- Không phụ thuộc WarehouseInitializer/UserInitializer — script tự tạo warehouse
-- và account riêng, chạy trực tiếp trong DBeaver (PostgreSQL), theo đúng convention
-- của scripts/seed_products.sql (DO $$ ... $$, gen_random_uuid()).
--
-- Mật khẩu của TẤT CẢ account tạo trong script này: 123456
-- (hash bcrypt thật, đã verify bằng BCryptPasswordEncoder.matches, không phải placeholder giả).
--
-- Lưu ý: order_no/receipt_no/transaction_no dùng hậu tố chữ "SEEDxx" (OD-2026-SEED01,
-- KG-2026-SEED01, WT-2026-SEED01, ...) thay vì số thuần, để không bao giờ trùng với
-- mã app thật sinh ra (app chỉ sinh toàn số, dùng COUNT(*) theo prefix năm).
--
-- Chạy lại nhiều lần sẽ lỗi unique constraint (email/order_no/...) — muốn reset thì xoá các
-- dòng seed trước (where email like '%@rehome.test' .. ) rồi chạy lại.

DO $$
DECLARE
    v_warehouse_id   uuid;

    v_buyer_id       uuid;
    v_seller1_id     uuid;
    v_seller2_id     uuid;
    v_org_id         uuid;
    v_moderator_id   uuid;

    v_wallet_buyer   uuid;
    v_wallet_seller1 uuid;
    v_wallet_seller2 uuid;

    v_sa1_id         uuid; -- địa chỉ mặc định của buyer
    v_sa2_id         uuid;

    v_brand_uniqlo   uuid;
    v_brand_zara     uuid;
    v_brand_hm       uuid;

    v_cat_aokhoac    uuid;
    v_cat_quanjean   uuid;
    v_cat_vaydam     uuid;

    v_cart_id        uuid;

    v_now            timestamptz := now();
    v_pwd_hash        text := '$2a$10$wPf5RyANdLOfKAzGo3sP5OMj9coM3zNZde2xjV910qTti4zx3XTFe'; -- "123456"

    -- Dữ liệu 6 sản phẩm, xử lý bằng vòng lặp vì cùng một chuỗi FK bắt buộc
    -- form -> receipt -> product -> photo
    v_product_ids    text[]  := ARRAY['SEED-AK-0001','SEED-QJ-0001','SEED-VD-0001',
                                       'SEED-VD-0002','SEED-AK-0002','SEED-QJ-0002'];
    v_names          text[]  := ARRAY['Áo khoác denim','Quần jean slimfit','Váy đầm hoa',
                                       'Váy đầm xòe','Áo khoác kaki','Quần tây'];
    v_prices         numeric[] := ARRAY[220000,150000,180000,170000,200000,160000];
    v_statuses       text[]  := ARRAY['SOLD','RESERVED','ON_SALE','ON_SALE','ON_SALE','RESERVED'];
    v_days_ago       int[]   := ARRAY[15,14,13,12,11,10];
    v_seller_ids     uuid[];   -- gán sau khi tạo account seller1/seller2
    v_category_ids   uuid[];   -- gán sau khi tạo category
    v_brand_ids      uuid[];   -- gán sau khi tạo brand
    v_receipt_confirmed timestamptz;
    v_form_id        uuid;
    v_receipt_id     uuid;
    i                int;

    -- Order 1 (COMPLETED) + Order 3 (CANCELLED) + Order 4 (PAID/MOMO) cần commission/order item
    v_order1_id      uuid;
    v_order2_id      uuid;
    v_order3_id      uuid;
    v_order4_id      uuid;
    v_oi_id          uuid;
BEGIN
    ------------------------------------------------------------------
    -- 1. Kho (warehouse riêng cho bộ seed này)
    ------------------------------------------------------------------
    INSERT INTO warehouses (id, name, address, latitude, longitude, opening_hours, status, created_at)
    VALUES (gen_random_uuid(), 'Kho Demo Seed', '1 Lê Duẩn, P. Bến Nghé, Q.1, TP.HCM',
            10.7816, 106.7006, '08:00 - 20:00', 'ACTIVE', v_now)
    RETURNING id INTO v_warehouse_id;

    ------------------------------------------------------------------
    -- 2. Tài khoản + hồ sơ (mật khẩu chung: 123456)
    ------------------------------------------------------------------
    INSERT INTO accounts (id, email, password_hash, phone, role, status, email_verified, created_at, updated_at)
    VALUES (gen_random_uuid(), 'buyer1@rehome.test', v_pwd_hash, '0911111101', 'MEMBER', 'ACTIVE', true, v_now, v_now)
    RETURNING id INTO v_buyer_id;

    INSERT INTO accounts (id, email, password_hash, phone, role, status, email_verified, created_at, updated_at)
    VALUES (gen_random_uuid(), 'seller1@rehome.test', v_pwd_hash, '0911111102', 'MEMBER', 'ACTIVE', true, v_now, v_now)
    RETURNING id INTO v_seller1_id;

    INSERT INTO accounts (id, email, password_hash, phone, role, status, email_verified, created_at, updated_at)
    VALUES (gen_random_uuid(), 'seller2@rehome.test', v_pwd_hash, '0911111103', 'MEMBER', 'ACTIVE', true, v_now, v_now)
    RETURNING id INTO v_seller2_id;

    INSERT INTO accounts (id, email, password_hash, phone, role, status, email_verified, created_at, updated_at)
    VALUES (gen_random_uuid(), 'org1@rehome.test', v_pwd_hash, '0911111104', 'ORGANIZATION', 'ACTIVE', true, v_now, v_now)
    RETURNING id INTO v_org_id;

    INSERT INTO accounts (id, email, password_hash, phone, role, status, email_verified, created_at, updated_at)
    VALUES (gen_random_uuid(), 'moderator1@rehome.test', v_pwd_hash, '0911111105', 'MODERATOR', 'ACTIVE', true, v_now, v_now)
    RETURNING id INTO v_moderator_id;

    INSERT INTO member_profiles (account_id, full_name, avatar_url, member_code) VALUES
        (v_buyer_id,   'Nguyễn Văn Buyer',  NULL, 'MEM-SEED-0001'),
        (v_seller1_id, 'Trần Thị Seller 1', NULL, 'MEM-SEED-0002'),
        (v_seller2_id, 'Lê Văn Seller 2',   NULL, 'MEM-SEED-0003');

    INSERT INTO moderator_profiles (account_id, full_name, moderator_code, warehouse_id)
    VALUES (v_moderator_id, 'Phạm Thị Moderator', 'MOD-SEED-0001', v_warehouse_id);

    INSERT INTO organization_profiles (
        account_id, legal_name, display_name, org_type, tax_code, operation_scope,
        address, contact_phone, contact_email, website, description, logo_url,
        verification_status, rejection_reason, verified_by, verified_at,
        donation_point_name, donation_point_address, donation_point_latitude, donation_point_longitude,
        donation_point_receiving_hours, donation_point_contact_phone,
        full_name, position, cccd_number, cccd_front_url, cccd_back_url
    ) VALUES (
        v_org_id, 'Tổ chức Thiện Nguyện ABC', 'ABC Charity', 'NGO', NULL, 'TP.HCM',
        '10 Nguyễn Huệ, Q.1, TP.HCM', '0911111104', 'org1@rehome.test', NULL,
        'Tổ chức thiện nguyện tiếp nhận quần áo cũ, seed cho mục đích test API.', NULL,
        'VERIFIED', NULL, v_moderator_id, v_now - interval '5 days',
        'Điểm nhận đồ ABC', '10 Nguyễn Huệ, Q.1, TP.HCM', 10.7745, 106.7030,
        '08:00 - 17:00 (Thứ 2 - Thứ 6)', '0911111104',
        'Hoàng Văn Đại Diện', 'Giám đốc', '079123456789', NULL, NULL
    );

    ------------------------------------------------------------------
    -- 3. Ví (số dư set sẵn, khớp với wallet_transactions sẽ insert ở bước 8)
    ------------------------------------------------------------------
    INSERT INTO wallets (id, member_id, balance, locked_balance, updated_at)
    VALUES (gen_random_uuid(), v_buyer_id, 5000000, 0, v_now)
    RETURNING id INTO v_wallet_buyer;

    INSERT INTO wallets (id, member_id, balance, locked_balance, updated_at)
    VALUES (gen_random_uuid(), v_seller1_id, 198000, 0, v_now)
    RETURNING id INTO v_wallet_seller1;

    INSERT INTO wallets (id, member_id, balance, locked_balance, updated_at)
    VALUES (gen_random_uuid(), v_seller2_id, 0, 0, v_now)
    RETURNING id INTO v_wallet_seller2;

    ------------------------------------------------------------------
    -- 4. Địa chỉ giao hàng của buyer
    ------------------------------------------------------------------
    INSERT INTO shipping_addresss (id, member_id, recipient_name, phone, address_detail, is_default)
    VALUES (gen_random_uuid(), v_buyer_id, 'Nguyễn Văn Buyer', '0911111101',
            '12 Nguyễn Huệ, P. Bến Nghé, Q.1, TP.HCM', true)
    RETURNING id INTO v_sa1_id;

    INSERT INTO shipping_addresss (id, member_id, recipient_name, phone, address_detail, is_default)
    VALUES (gen_random_uuid(), v_buyer_id, 'Nguyễn Văn Buyer (Công ty)', '0911111199',
            '45 Lê Lợi, P. Bến Thành, Q.1, TP.HCM', false)
    RETURNING id INTO v_sa2_id;

    ------------------------------------------------------------------
    -- 5. Thương hiệu & danh mục
    ------------------------------------------------------------------
    INSERT INTO brands (id, name, status) VALUES (gen_random_uuid(), 'Uniqlo', 'ACTIVE') RETURNING id INTO v_brand_uniqlo;
    INSERT INTO brands (id, name, status) VALUES (gen_random_uuid(), 'Zara', 'ACTIVE') RETURNING id INTO v_brand_zara;
    INSERT INTO brands (id, name, status) VALUES (gen_random_uuid(), 'H&M', 'ACTIVE') RETURNING id INTO v_brand_hm;

    INSERT INTO categories (id, name, tryon_slot, status)
    VALUES (gen_random_uuid(), 'Áo khoác', 'TOP', 'ACTIVE') RETURNING id INTO v_cat_aokhoac;
    INSERT INTO categories (id, name, tryon_slot, status)
    VALUES (gen_random_uuid(), 'Quần jean', 'BOTTOM', 'ACTIVE') RETURNING id INTO v_cat_quanjean;
    INSERT INTO categories (id, name, tryon_slot, status)
    VALUES (gen_random_uuid(), 'Váy đầm', 'DRESS', 'ACTIVE') RETURNING id INTO v_cat_vaydam;

    v_seller_ids   := ARRAY[v_seller1_id, v_seller2_id, v_seller1_id, v_seller2_id, v_seller1_id, v_seller2_id];
    v_category_ids := ARRAY[v_cat_aokhoac, v_cat_quanjean, v_cat_vaydam, v_cat_vaydam, v_cat_aokhoac, v_cat_quanjean];
    v_brand_ids    := ARRAY[v_brand_uniqlo, v_brand_zara, v_brand_hm, v_brand_zara, v_brand_uniqlo, v_brand_hm];

    ------------------------------------------------------------------
    -- 6. Ký gửi -> sản phẩm (6 món, chuỗi form -> receipt -> product -> photo)
    ------------------------------------------------------------------
    FOR i IN 1..6 LOOP
        v_receipt_confirmed := v_now - (v_days_ago[i] || ' days')::interval;

        INSERT INTO consignment_forms (id, member_id, warehouse_id, category_id, brand_id,
                                        product_name, description, desired_price, status,
                                        reject_reason, bring_in_deadline, created_at, updated_at)
        VALUES (gen_random_uuid(), v_seller_ids[i], v_warehouse_id, v_category_ids[i], v_brand_ids[i],
                v_names[i], 'Hàng còn mới, ít sử dụng (seed data)', v_prices[i], 'APPROVED',
                NULL, (v_receipt_confirmed - interval '1 day')::date + 7,
                v_receipt_confirmed - interval '1 day', v_receipt_confirmed - interval '1 day')
        RETURNING id INTO v_form_id;

        INSERT INTO consignment_receipts (id, receipt_no, consignment_form_id, member_id, moderator_id,
                                           warehouse_id, agreed_price, floor_price, term_length_days,
                                           max_renewals, free_withdrawal_window_days,
                                           commission_rate_term1, commission_rate_term2, commission_rate_term3,
                                           withdrawal_fee, degraded_compensation_rate, confirmed_at, pdf_url,
                                           created_at)
        VALUES (gen_random_uuid(), 'KG-2026-SEED' || lpad(i::text, 2, '0'), v_form_id, v_seller_ids[i], v_moderator_id,
                v_warehouse_id, v_prices[i], round(v_prices[i] * 0.5), 30, 2, 7,
                0.10, 0.15, 0.20, 20000, 0.50, v_receipt_confirmed,
                'https://example.com/receipts/seed-' || i || '.pdf', v_receipt_confirmed)
        RETURNING id INTO v_receipt_id;

        INSERT INTO products (id, consignment_receipt_id, member_id, warehouse_id, category_id, brand_id,
                               name, size, condition_note, current_price, status, current_term,
                               term_started_at, end_of_term_deadline, unclaimed_decision_deadline,
                               shelf_location, created_at, updated_at)
        VALUES (v_product_ids[i], v_receipt_id, v_seller_ids[i], v_warehouse_id, v_category_ids[i], v_brand_ids[i],
                v_names[i], 'M', 'Còn mới 90%, không lỗi', v_prices[i], v_statuses[i], 1,
                v_receipt_confirmed, v_receipt_confirmed + interval '90 days', NULL,
                NULL, v_receipt_confirmed, v_now);

        INSERT INTO product_photos (id, product_id, image_url, is_background_removed)
        VALUES (gen_random_uuid(), v_product_ids[i],
                'https://cdn.rehome.vn/products/' || v_product_ids[i] || '/thumb.jpg', false);
    END LOOP;

    ------------------------------------------------------------------
    -- 7. Giỏ hàng của buyer: 2 món đang ON_SALE (P3 của seller1, P4 của seller2)
    --    -> test "checkout từ giỏ" sẽ tạo 1 đơn gồm hàng của 2 seller khác nhau
    ------------------------------------------------------------------
    INSERT INTO carts (id, member_id) VALUES (gen_random_uuid(), v_buyer_id) RETURNING id INTO v_cart_id;

    INSERT INTO cart_items (id, cart_id, product_id, added_at) VALUES
        (gen_random_uuid(), v_cart_id, v_product_ids[3], v_now - interval '1 hour'),
        (gen_random_uuid(), v_cart_id, v_product_ids[4], v_now - interval '30 minutes');

    ------------------------------------------------------------------
    -- 8. Đơn hàng (4 đơn, mỗi đơn minh hoạ 1 trạng thái/flow khác nhau)
    ------------------------------------------------------------------

    -- Order 1: COMPLETED — P1 (seller1), đã confirm-receipt -> có WalletTransaction ORDER_PAYOUT
    -- Dùng để test: GET /orders/{id}, GET /wallet/revenue, GET /wallet/transactions, POST /orders/{id}/rating
    INSERT INTO orders (id, order_no, buyer_id, warehouse_id, shipping_address_id,
                         shipping_recipient_name, shipping_phone, shipping_address_detail,
                         subtotal, shipping_fee, total_amount, payment_method, status,
                         ghn_tracking_code, payment_deadline, delivered_at, auto_complete_deadline,
                         cancel_reason, cancelled_at, refund_amount, completed_at,
                         service_rating, rated_at, rating_comment, created_at, updated_at)
    VALUES (gen_random_uuid(), 'OD-2026-SEED01', v_buyer_id, v_warehouse_id, v_sa1_id,
            'Nguyễn Văn Buyer', '0911111101', '12 Nguyễn Huệ, P. Bến Nghé, Q.1, TP.HCM',
            220000, 25000, 245000, 'WALLET', 'COMPLETED',
            NULL, v_now - interval '5 days' + interval '15 minutes',
            v_now - interval '2 days', v_now - interval '2 days' + interval '3 days',
            NULL, NULL, NULL, v_now - interval '1 day',
            NULL, NULL, NULL, v_now - interval '5 days', v_now - interval '1 day')
    RETURNING id INTO v_order1_id;

    INSERT INTO order_items (id, order_id, product_id, price_at_order, commission_rate_applied,
                              tax_rate_applied, term_applied)
    VALUES (gen_random_uuid(), v_order1_id, v_product_ids[1], 220000, 0.10, 0, 1)
    RETURNING id INTO v_oi_id;

    INSERT INTO wallet_transactions (id, transaction_no, wallet_type, wallet_id, type, amount,
                                      balance_after, related_order_id, related_order_item_id,
                                      counterpart_transaction_id, status, note, created_at)
    VALUES (gen_random_uuid(), 'WT-2026-SEED01', 'MEMBER', v_wallet_seller1, 'ORDER_PAYOUT', 198000,
            198000, v_order1_id, v_oi_id, NULL, 'SUCCESS',
            'Thanh toán đơn hàng OD-2026-SEED01', v_now - interval '1 day');

    -- Order 2: PAID — P2 (seller2), chưa giao/chưa hoàn tất -> test POST /orders/{id}/cancel
    INSERT INTO orders (id, order_no, buyer_id, warehouse_id, shipping_address_id,
                         shipping_recipient_name, shipping_phone, shipping_address_detail,
                         subtotal, shipping_fee, total_amount, payment_method, status,
                         ghn_tracking_code, payment_deadline, delivered_at, auto_complete_deadline,
                         cancel_reason, cancelled_at, refund_amount, completed_at,
                         service_rating, rated_at, rating_comment, created_at, updated_at)
    VALUES (gen_random_uuid(), 'OD-2026-SEED02', v_buyer_id, v_warehouse_id, v_sa1_id,
            'Nguyễn Văn Buyer', '0911111101', '12 Nguyễn Huệ, P. Bến Nghé, Q.1, TP.HCM',
            150000, 25000, 175000, 'WALLET', 'PAID',
            NULL, v_now - interval '2 hours' + interval '15 minutes', NULL, NULL,
            NULL, NULL, NULL, NULL,
            NULL, NULL, NULL, v_now - interval '2 hours', v_now - interval '2 hours')
    RETURNING id INTO v_order2_id;

    INSERT INTO order_items (id, order_id, product_id, price_at_order, commission_rate_applied,
                              tax_rate_applied, term_applied)
    VALUES (gen_random_uuid(), v_order2_id, v_product_ids[2], 150000, 0.10, 0, 1);

    -- Order 3: CANCELLED — P5 (seller1), đã được hoàn tiền -> test WalletTransaction REFUND,
    -- và lọc GET /orders?status=CANCELLED
    INSERT INTO orders (id, order_no, buyer_id, warehouse_id, shipping_address_id,
                         shipping_recipient_name, shipping_phone, shipping_address_detail,
                         subtotal, shipping_fee, total_amount, payment_method, status,
                         ghn_tracking_code, payment_deadline, delivered_at, auto_complete_deadline,
                         cancel_reason, cancelled_at, refund_amount, completed_at,
                         service_rating, rated_at, rating_comment, created_at, updated_at)
    VALUES (gen_random_uuid(), 'OD-2026-SEED03', v_buyer_id, v_warehouse_id, v_sa1_id,
            'Nguyễn Văn Buyer', '0911111101', '12 Nguyễn Huệ, P. Bến Nghé, Q.1, TP.HCM',
            200000, 25000, 225000, 'WALLET', 'CANCELLED',
            NULL, v_now - interval '3 days 1 hour' + interval '15 minutes', NULL, NULL,
            'BUYER_CANCELLED', v_now - interval '3 days', 225000, NULL,
            NULL, NULL, NULL, v_now - interval '3 days 1 hour', v_now - interval '3 days')
    RETURNING id INTO v_order3_id;

    INSERT INTO order_items (id, order_id, product_id, price_at_order, commission_rate_applied,
                              tax_rate_applied, term_applied)
    VALUES (gen_random_uuid(), v_order3_id, v_product_ids[5], 200000, 0.10, 0, 1);

    INSERT INTO wallet_transactions (id, transaction_no, wallet_type, wallet_id, type, amount,
                                      balance_after, related_order_id, related_order_item_id,
                                      counterpart_transaction_id, status, note, created_at)
    VALUES (gen_random_uuid(), 'WT-2026-SEED02', 'MEMBER', v_wallet_buyer, 'REFUND', 225000,
            5000000, v_order3_id, NULL, NULL, 'SUCCESS',
            'Hoàn tiền hủy đơn OD-2026-SEED03', v_now - interval '3 days');

    -- Order 4: PAID qua MOMO — P6 (seller2) -> test bảng payments / GET /orders (payment_method=MOMO)
    INSERT INTO orders (id, order_no, buyer_id, warehouse_id, shipping_address_id,
                         shipping_recipient_name, shipping_phone, shipping_address_detail,
                         subtotal, shipping_fee, total_amount, payment_method, status,
                         ghn_tracking_code, payment_deadline, delivered_at, auto_complete_deadline,
                         cancel_reason, cancelled_at, refund_amount, completed_at,
                         service_rating, rated_at, rating_comment, created_at, updated_at)
    VALUES (gen_random_uuid(), 'OD-2026-SEED04', v_buyer_id, v_warehouse_id, v_sa1_id,
            'Nguyễn Văn Buyer', '0911111101', '12 Nguyễn Huệ, P. Bến Nghé, Q.1, TP.HCM',
            160000, 25000, 185000, 'MOMO', 'PAID',
            NULL, v_now - interval '1 hour' + interval '15 minutes', NULL, NULL,
            NULL, NULL, NULL, NULL,
            NULL, NULL, NULL, v_now - interval '1 hour', v_now - interval '1 hour')
    RETURNING id INTO v_order4_id;

    INSERT INTO order_items (id, order_id, product_id, price_at_order, commission_rate_applied,
                              tax_rate_applied, term_applied)
    VALUES (gen_random_uuid(), v_order4_id, v_product_ids[6], 160000, 0.10, 0, 1);

    INSERT INTO payments (id, order_id, amount, method, momo_transaction_id, momo_order_id,
                           momo_request_id, status, paid_at)
    VALUES (gen_random_uuid(), v_order4_id, 185000, 'MOMO', '555000111', 'SEED-MOMO-0001',
            'SEED-MOMO-REQ-0001', 'SUCCESS', v_now - interval '1 hour' + interval '2 minutes');

END $$;

-- ============================================================================
-- Tài khoản test (mật khẩu chung: 123456)
--   buyer1@rehome.test      MEMBER        — ví 5,000,000đ, 2 địa chỉ giao hàng, giỏ có 2 món
--   seller1@rehome.test     MEMBER        — ví 198,000đ (đã nhận payout từ đơn OD-2026-SEED01)
--   seller2@rehome.test     MEMBER        — ví 0đ, chưa có giao dịch
--   org1@rehome.test        ORGANIZATION  — hồ sơ VERIFIED
--   moderator1@rehome.test  MODERATOR     — gắn vào "Kho Demo Seed"
--   admin@rehome.com        ADMIN         — đã có sẵn từ UserInitializer (password 123456)
--
-- Đơn hàng:
--   OD-2026-SEED01  COMPLETED  — test GET /wallet/revenue, GET /orders/{id}, POST rating
--   OD-2026-SEED02  PAID       — test POST /orders/{id}/cancel
--   OD-2026-SEED03  CANCELLED — đã có WalletTransaction REFUND
--   OD-2026-SEED04  PAID (MOMO) — có kèm 1 dòng payments
--
-- Sản phẩm ON_SALE còn lại để test thêm giỏ hàng / đặt đơn mới: SEED-VD-0001, SEED-VD-0002,
-- SEED-AK-0002 (2 món đầu đã có sẵn trong giỏ của buyer1).
-- ============================================================================
