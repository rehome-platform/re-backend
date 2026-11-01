-- Chạy nếu KHÔNG dùng ddl-auto=update (PostgreSQL). Chạy lại nhiều lần vẫn an toàn.
-- OC-02: Campaign có title/coverUrl/receiveAtOwnAddress + nhiều kho (campaign_warehouses).
-- location_name/latitude/longitude không còn nhập tay (địa chỉ riêng lấy từ
-- organization_profiles.donation_point_*), nên bỏ NOT NULL - ddl-auto=update không tự
-- drop được NOT NULL của cột đã tồn tại.
ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS title                    varchar(255);
ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS cover_url                varchar(2048);
ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS receive_at_own_address   boolean NOT NULL DEFAULT false;
ALTER TABLE campaigns ALTER COLUMN location_name DROP NOT NULL;
ALTER TABLE campaigns ALTER COLUMN latitude       DROP NOT NULL;
ALTER TABLE campaigns ALTER COLUMN longitude      DROP NOT NULL;

CREATE TABLE IF NOT EXISTS campaign_warehouses (
    campaign_id  uuid NOT NULL REFERENCES campaigns(id),
    warehouse_id uuid NOT NULL REFERENCES warehouses(id),
    PRIMARY KEY (campaign_id, warehouse_id)
);
