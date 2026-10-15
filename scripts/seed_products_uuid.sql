-- Seed 10 sản phẩm mẫu ở trạng thái ON_SALE, product.id dùng định dạng UUID cố định
-- (550e8400-e29b-41d4-a716-446655440001 .. 010) để test Postman/manual dễ tham chiếu.
-- Chạy trực tiếp trong DBeaver (PostgreSQL). Tự tạo kèm account/warehouse/category/brand
-- và chuỗi consignment_form -> consignment_receipt -> product bắt buộc theo FK.
-- Lưu ý: products.id là cột String (SKU), không phải cột uuid thật, nên gán được
-- literal dạng UUID bình thường.

DO $$
DECLARE
    v_member_id    uuid;
    v_moderator_id uuid;
    v_warehouse_id uuid;
    v_category_id  uuid;
    v_brand_id     uuid;
    v_form_id      uuid;
    v_receipt_id   uuid;
    v_product_id   text;
    v_now          timestamptz := now();
    i              int;
    v_names        text[] := ARRAY[
        'Áo sơ mi Zara trắng', 'Áo thun Uniqlo basic', 'Quần jeans Levi''s 501',
        'Áo khoác denim H&M', 'Chân váy xếp ly Zara', 'Áo len cổ lọ Mango',
        'Quần short kaki Old Navy', 'Đầm maxi hoa nhí', 'Áo blazer công sở',
        'Áo hoodie Nike basic'
    ];
    v_prices       numeric[] := ARRAY[220000, 150000, 350000, 280000, 190000,
                                       260000, 170000, 240000, 420000, 310000];
    -- id cố định dạng UUID cho từng sản phẩm, chỉ đổi 3 ký tự cuối (001..010)
    v_product_ids  text[] := ARRAY[
        '550e8400-e29b-41d4-a716-446655440001',
        '550e8400-e29b-41d4-a716-446655440002',
        '550e8400-e29b-41d4-a716-446655440003',
        '550e8400-e29b-41d4-a716-446655440004',
        '550e8400-e29b-41d4-a716-446655440005',
        '550e8400-e29b-41d4-a716-446655440006',
        '550e8400-e29b-41d4-a716-446655440007',
        '550e8400-e29b-41d4-a716-446655440008',
        '550e8400-e29b-41d4-a716-446655440009',
        '550e8400-e29b-41d4-a716-446655440010'
    ];
BEGIN
    -- Account: member ký gửi
    INSERT INTO accounts (id, email, password_hash, role, status, email_verified, created_at, updated_at)
    VALUES (gen_random_uuid(), 'seed.member.' || substr(md5(random()::text), 1, 8) || '@example.com',
            '$2a$10$abcdefghijklmnopqrstuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu', 'MEMBER', 'ACTIVE', true, v_now, v_now)
    RETURNING id INTO v_member_id;

    -- Account: moderator tiếp nhận
    INSERT INTO accounts (id, email, password_hash, role, status, email_verified, created_at, updated_at)
    VALUES (gen_random_uuid(), 'seed.moderator.' || substr(md5(random()::text), 1, 8) || '@example.com',
            '$2a$10$abcdefghijklmnopqrstuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu', 'MODERATOR', 'ACTIVE', true, v_now, v_now)
    RETURNING id INTO v_moderator_id;

    -- Warehouse
    INSERT INTO warehouses (id, name, address, latitude, longitude, opening_hours, status, created_at)
    VALUES (gen_random_uuid(), 'Kho Quận 7', '123 Nguyễn Văn Linh, Q7, TP.HCM', 10.7326, 106.7217,
            '08:00 - 20:00', 'ACTIVE', v_now)
    RETURNING id INTO v_warehouse_id;

    -- Category
    INSERT INTO categories (id, name, tryon_slot, status)
    VALUES (gen_random_uuid(), 'Áo', 'TOP', 'ACTIVE')
    RETURNING id INTO v_category_id;

    -- Brand
    INSERT INTO brands (id, name, status)
    VALUES (gen_random_uuid(), 'Zara', 'ACTIVE')
    RETURNING id INTO v_brand_id;

    FOR i IN 1..10 LOOP
        v_product_id := v_product_ids[i];

        INSERT INTO consignment_forms (id, member_id, warehouse_id, category_id, brand_id,
                                        product_name, description, desired_price, status,
                                        created_at, updated_at)
        VALUES (gen_random_uuid(), v_member_id, v_warehouse_id, v_category_id, v_brand_id,
                v_names[i], 'Hàng còn mới, ít sử dụng', v_prices[i], 'APPROVED', v_now, v_now)
        RETURNING id INTO v_form_id;

        INSERT INTO consignment_receipts (id, receipt_no, consignment_form_id, member_id, moderator_id,
                                           warehouse_id, agreed_price, floor_price, term_length_days,
                                           max_renewals, free_withdrawal_window_days,
                                           commission_rate_term1, commission_rate_term2, commission_rate_term3,
                                           withdrawal_fee, degraded_compensation_rate, confirmed_at, pdf_url,
                                           created_at)
        VALUES (gen_random_uuid(), 'KG-SEED-UUID-' || lpad(i::text, 6, '0'), v_form_id, v_member_id, v_moderator_id,
                v_warehouse_id, round(v_prices[i] * 1.5), round(v_prices[i] * 0.75), 30, 2, 7,
                0.10, 0.15, 0.20, 20000, 0.50, v_now, 'https://example.com/receipts/seed-uuid-' || i || '.pdf',
                v_now)
        RETURNING id INTO v_receipt_id;

        INSERT INTO products (id, consignment_receipt_id, member_id, warehouse_id, category_id, brand_id,
                               name, size, condition_note, current_price, status, current_term,
                               term_started_at, shelf_location, created_at, updated_at)
        VALUES (v_product_id, v_receipt_id, v_member_id, v_warehouse_id,
                v_category_id, v_brand_id, v_names[i], 'M', 'LIKE_NEW',
                v_prices[i], 'ON_SALE', 1, v_now, 'A1-0' || i, v_now, v_now);

        INSERT INTO product_photos (id, product_id, image_url, is_background_removed)
        VALUES (gen_random_uuid(), v_product_id,
                'https://cdn.rehome.vn/products/' || v_product_id || '/thumb.jpg', false);
    END LOOP;
END $$;
