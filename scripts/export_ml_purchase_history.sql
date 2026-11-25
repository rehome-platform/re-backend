-- Synthetic ML demo only. Export each SELECT separately as CSV in DBeaver/pgAdmin.
-- Do not use email, fixture prefixes or synthetic group names as model features.
-- Query 1: completed purchases. Use historical price, not today's product price.
SELECT o.buyer_id AS user_id, p.id AS product_id, o.completed_at AS interaction_at,
       p.name, p.name_normalized, c.id AS category_id, c.name AS category_name,
       b.id AS brand_id, b.name AS brand_name, p.size,
       oi.price_at_order AS purchase_price, p.created_at AS available_at
FROM orders o
JOIN order_items oi ON oi.order_id = o.id
JOIN products p ON p.id = oi.product_id
JOIN categories c ON c.id = p.category_id
JOIN brands b ON b.id = p.brand_id
WHERE o.order_no LIKE 'MLR1-OD-%' AND o.status = 'COMPLETED'
  AND NOT EXISTS (
      SELECT 1 FROM return_requests rr
      WHERE rr.order_id = o.id AND rr.status = 'VALID'
  )
ORDER BY o.buyer_id, o.completed_at, o.id;

-- Query 2: available candidates for online/demo recommendations NOW.
-- The application must also exclude products owned by the requesting member.
SELECT p.id AS product_id, p.member_id AS seller_id, p.name, p.name_normalized,
       c.id AS category_id, c.name AS category_name,
       b.id AS brand_id, b.name AS brand_name, p.size, p.current_price,
       p.created_at AS available_at, p.warehouse_id
FROM products p
JOIN categories c ON c.id = p.category_id
JOIN brands b ON b.id = p.brand_id
WHERE p.id LIKE 'MLR1-SP-%' AND p.status = 'ON_SALE'
ORDER BY p.id;
