BEGIN;
CREATE TABLE IF NOT EXISTS order_packing_photo_urls (
    order_id uuid NOT NULL REFERENCES orders(id),
    position integer NOT NULL,
    image_url varchar(2048) NOT NULL,
    PRIMARY KEY (order_id, position)
);
COMMIT;
