"""Generate synthetic SQL fixtures from the ReHome entity schema; never connects to a DB."""
from pathlib import Path
from uuid import NAMESPACE_URL, uuid5

BUYERS_PER_STYLE = 10
PURCHASES_PER_BUYER = 12
CANDIDATES_PER_STYLE = 30
STYLES = ("sport", "office", "casual")
HASH = "$2a$10$9oLul/HMTIiG0xkAyG6zQuL9VlDY1gj6f8.Faz1CdLL/4PdfX2dXS"  # 123456
GROUPS = (
    (("Ao thun the thao", "TOP"), ("Quan jogger", "BOTTOM"), ("Ao khoac the thao", "TOP")),
    (("Ao so mi", "TOP"), ("Quan tay", "BOTTOM"), ("Ao blazer", "TOP")),
    (("Ao oversize", "TOP"), ("Quan jeans", "BOTTOM"), ("Ao hoodie", "TOP")),
)
COLORS = ("den", "trang", "xanh duong", "xam", "be")
SIZES = ("S", "M", "L", "XL")


class Sql(str):
    """Explicit SQL expression, not a string literal."""


def uid(key):
    return str(uuid5(NAMESPACE_URL, "rehome:ml-seed:v1:" + key))


def ago(days):
    return Sql("CURRENT_TIMESTAMP - INTERVAL '%d' DAY" % days)


def literal(value):
    if isinstance(value, Sql):
        return value
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, (int, float)):
        return str(value)
    return "'" + str(value).replace("'", "''") + "'"


def insert(table, columns, rows):
    assert all(len(row) == len(columns) for row in rows), table
    return ("INSERT INTO " + table + " (" + ", ".join(columns) + ") VALUES\n"
            + ",\n".join("  (" + ", ".join(map(literal, row)) + ")" for row in rows)
            + "\nON CONFLICT DO NOTHING;\n")


def generate():
    warehouse = uid("warehouse")
    moderator = uid("moderator")
    accounts, profiles, addresses, wallets = [], [], [], []
    buyers = []
    for group, style in enumerate(STYLES):
        for number in range(1, BUYERS_PER_STYLE + 1):
            buyers.append((uid("buyer:%s:%d" % (style, number)), group, number,
                           "ml.%s.%02d@example.test" % (style, number), False))
    for number in range(1, 4):
        buyers.append((uid("cold:%d" % number), -1, number,
                       "ml.new.%02d@example.test" % number, True))

    members = [(buyer, email, "ML DEMO buyer " + email, "MLR1-B-%03d" % (i + 1))
               for i, (buyer, _, _, email, _) in enumerate(buyers)]
    for group, style in enumerate(STYLES):
        members.append((uid("seller:" + style), "ml.seller.%s@example.test" % style,
                        "ML DEMO seller " + style, "MLR1-S-%03d" % (group + 1)))
    for member, email, name, code in members:
        accounts.append((member, email, HASH, "MEMBER", "ACTIVE", True, None, ago(180), ago(180)))
        profiles.append((member, name, code, 0, 0, 0))
        wallets.append((uid("wallet:" + member), member, 0, 0, ago(180)))
    accounts.append((moderator, "ml.moderator@example.test", HASH, "MODERATOR", "ACTIVE", True,
                     warehouse, ago(180), ago(180)))
    for buyer, _, _, email, _ in buyers:
        addresses.append((uid("address:" + buyer), buyer, "ML DEMO " + email, "0900000000",
                          "ML DEMO - synthetic address, do not ship", "Phuong Sai Gon",
                          "Ho Chi Minh", True))

    categories = [(uid("category:%d:%d" % (g, slot)), "ML Demo - " + name, tryon, "ACTIVE")
                  for g, group in enumerate(GROUPS) for slot, (name, tryon) in enumerate(group)]
    brands = [(uid("brand:%d" % i), "ML Demo Brand " + str(i + 1), "ACTIVE") for i in range(3)]
    forms, receipts, products, orders, items = [], [], [], [], []
    serial = 0

    def product(group, variation, buyer=None, purchase_number=None):
        nonlocal serial
        serial += 1
        sku = "MLR1-SP-%06d" % serial
        category_slot = variation % 3
        category = uid("category:%d:%d" % (group, category_slot))
        brand = uid("brand:%d" % ((group + (variation % 5 == 0)) % 3))
        seller = uid("seller:" + STYLES[group])
        name = GROUPS[group][category_slot][0] + " " + COLORS[variation % len(COLORS)]
        size = SIZES[(variation // 3) % len(SIZES)]
        price = (120000, 240000, 160000)[group] + 20000 * (variation % 5)
        days = (120 - purchase_number * 6 - variation % 4) if buyer else 0
        form_id, receipt_id = uid("form:" + sku), uid("receipt:" + sku)
        form_time = ago(days + 12)
        receipt_time = ago(days + 10)
        stocked_time = ago(days + 7)
        forms.append((form_id, seller, warehouse, category, brand, name,
                      "[ML_SEED_V1] Synthetic item for recommendation experiments, not a real consignment.",
                      price, "INSPECTING", form_time, receipt_time,
                      "MLR1-F-%06d" % serial, size, "GOOD"))
        receipts.append((receipt_id, "MLR1-R-%06d" % serial, form_id, seller, moderator, warehouse,
                         price, price // 2, 30, 2, 7, Sql("0.10"), Sql("0.15"), Sql("0.20"),
                         50000, Sql("0.50"), receipt_time, receipt_time, "CONFIRMED", 1,
                         "Synthetic item - good condition"))
        products.append((sku, receipt_id, seller, warehouse, category, brand, name, name.lower(), size,
                         "Synthetic item - good condition", price, "SOLD" if buyer else "ON_SALE",
                         1, stocked_time, stocked_time, stocked_time, 0))
        if buyer:
            order_id = uid("order:" + sku)
            orders.append((order_id, "MLR1-OD-%06d" % serial, buyer, warehouse, uid("address:" + buyer),
                           "ML DEMO recipient", "0900000000", "ML DEMO - do not ship",
                           "Phuong Sai Gon", "Ho Chi Minh", True, 1,
                           price, 30000, 0, price + 30000, "VNPAY", "COMPLETED",
                           Sql("CURRENT_TIMESTAMP - INTERVAL '%d' DAY + INTERVAL '15' MINUTE" % days),
                           ago(days - 2), ago(days - 9), ago(days - 9), ago(days - 3), ago(days - 9),
                           "[ML_SEED_V1] synthetic purchase history; not a real settled payment",
                           ago(days), ago(days - 9)))
            items.append((uid("item:" + sku), order_id, sku, price, Sql("0.10"), 0, 1, True))

    for buyer, primary, number, _, cold in buyers:
        if cold:
            continue
        for purchase in range(1, PURCHASES_PER_BUYER + 1):
            # 75% primary style, 25% secondary; histories are deliberately not perfectly separated.
            group = primary if purchase % 4 else (primary + 1 + (purchase // 4 + number) % 2) % 3
            product(group, number * 17 + purchase, buyer, purchase)
    for group in range(3):
        for variation in range(CANDIDATES_PER_STYLE):
            product(group, variation)

    sections = ["""-- ReHome synthetic recommendation dataset v1 (PostgreSQL).
-- Generated by scripts/generate_ml_purchase_seed.py using current entity columns.
-- LOCAL/TEST ONLY. Adds fixture rows; no TRUNCATE, DELETE, UPDATE or enum CHECK changes.
-- Requires the application schema, not seed_demo_full.sql. Run the WHOLE file.
-- Login password: 123456. Emails are reserved example.test fixtures.
-- Re-running keeps existing rows via ON CONFLICT DO NOTHING; it does not reset edits.
-- This is ML history data, NOT payment/accounting/GHN workflow certification.
-- Completed orders have payout_at set so the payout scheduler cannot pay them again.
-- No payment gateway, wallet ledger, notification, queue, or external shipment is created.
-- Products have no photos; this dataset is for structured/text recommendation features.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';
"""
    ]
    sections.append(insert("warehouses", ["id", "name", "address", "latitude", "longitude", "opening_hours", "status", "created_at", "ward_name", "province_name", "is_new_address"],
                           [(warehouse, "ML DEMO warehouse", "Synthetic test location - do not ship", Sql("10.7700000"), Sql("106.7000000"), "08:00-17:00", "ACTIVE", ago(180), "Phuong Sai Gon", "Ho Chi Minh", True)]))
    sections.append(insert("accounts", ["id", "email", "password_hash", "role", "status", "email_verified", "warehouse_id", "created_at", "updated_at"], accounts))
    sections.append(insert("member_profiles", ["account_id", "full_name", "member_code", "points_balance", "bonus_ai_tryon_credits", "bonus_live_tryon_credits"], profiles))
    sections.append(insert("wallets", ["id", "member_id", "balance", "locked_balance", "updated_at"], wallets))
    # This unusual table name is intentional: ShippingAddress.@Table(name="shipping_addresss").
    sections.append(insert("shipping_addresss", ["id", "member_id", "recipient_name", "phone", "address_detail", "ward_name", "province_name", "is_default"], addresses))
    sections.append(insert("categories", ["id", "name", "tryon_slot", "status"], categories))
    sections.append(insert("brands", ["id", "name", "status"], brands))
    sections.append(insert("consignment_forms", ["id", "member_id", "warehouse_id", "category_id", "brand_id", "product_name", "description", "desired_price", "status", "created_at", "updated_at", "form_no", "size", "item_condition"], forms))
    sections.append(insert("consignment_receipts", ["id", "receipt_no", "consignment_form_id", "member_id", "moderator_id", "warehouse_id", "agreed_price", "floor_price", "term_length_days", "max_renewals", "free_withdrawal_window_days", "commission_rate_term1", "commission_rate_term2", "commission_rate_term3", "withdrawal_fee", "degraded_compensation_rate", "confirmed_at", "created_at", "status", "version", "condition_note"], receipts))
    sections.append(insert("products", ["id", "consignment_receipt_id", "member_id", "warehouse_id", "category_id", "brand_id", "name", "name_normalized", "size", "condition_note", "current_price", "status", "current_term", "term_started_at", "created_at", "updated_at", "version"], products))
    sections.append(insert("orders", ["id", "order_no", "buyer_id", "warehouse_id", "shipping_address_id", "shipping_recipient_name", "shipping_phone", "shipping_address_detail", "shipping_ward_name", "shipping_province_name", "shipping_is_new_address", "scanned_count", "subtotal", "shipping_fee", "discount_amount", "total_amount", "payment_method", "status", "payment_deadline", "delivered_at", "auto_complete_deadline", "return_deadline", "completed_at", "payout_at", "buyer_note", "created_at", "updated_at"], orders))
    sections.append(insert("order_items", ["id", "order_id", "product_id", "price_at_order", "commission_rate_applied", "tax_rate_applied", "term_applied", "scanned"], items))
    sections.append("""COMMIT;

-- Summary of ONLY this seed namespace. Expected 360 completed orders, 360 SOLD, 90 ON_SALE.
SELECT status, count(*) AS products FROM products WHERE id LIKE 'MLR1-SP-%' GROUP BY status;
SELECT status, count(*) AS orders FROM orders WHERE order_no LIKE 'MLR1-OD-%' GROUP BY status;
SELECT a.email, count(o.id) AS completed_orders
FROM accounts a LEFT JOIN orders o ON o.buyer_id = a.id AND o.status = 'COMPLETED'
WHERE a.email LIKE 'ml.%@example.test' AND a.email NOT LIKE 'ml.seller.%' AND a.role = 'MEMBER'
GROUP BY a.email ORDER BY a.email;
"""
    )
    return "\n".join(sections)


if __name__ == "__main__":
    destination = Path(__file__).with_name("seed_ml_purchase_history.sql")
    destination.write_text(generate(), encoding="utf-8")
    print("Generated " + str(destination) + " (no database connection)")
