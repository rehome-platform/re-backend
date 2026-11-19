# Return approval and carrier handover

## Return approval

`POST /return-requests/{id}/approve` requires a moderator in the order's warehouse.

`app.ghn.fallback-on-error` defaults to true and can be overridden with `GHN_FALLBACK_ON_ERROR=false`.
When GHN throws or returns no order code, enabled fallback approves the return with
`LOCAL-RT-{first 8 UUID characters, uppercase}` and a new handover deadline.
`ghnFallbackUsed` is persisted and returned on all return request responses, including retries.
This is an internal reference, not a GHN booking. Disabled fallback preserves HTTP 502 and rolls back approval.

## Forward shipment

`POST /orders/{id}/shipment` now produces PACKED instead of SHIPPING.
`POST /orders/{id}/handover` requires a moderator in the assigned warehouse and no body.
It moves PACKED to SHIPPING, records handedOverAt and returns ApiResponse<OrderResponse>.
Repeating it for SHIPPING returns the current order without another timestamp or audit entry.
Other statuses return HTTP 409 ORDER_NOT_PACKED. Existing SHIPPING orders remain supported.
Order detail exposes handedOverAt and allowedActions includes HANDOVER for PACKED orders.
GHN pickup/transit/delivery events also advance PACKED orders, preventing missed manual handovers from blocking delivery.

## Two-step valid return

1. `POST /return-requests/{id}/resolve` with `{"resolution":"VALID"}` refunds the buyer immediately and records VALID/resolvedAt with no itemAction.
2. `POST /return-requests/{id}/item-action` with `{"action":"RELIST"}` (or RETURN_TO_SELLER, DISCARD) handles the item without refunding again. Only VALID requests with no action are accepted; repeated decisions return 409.

Supplying action in step 1 still performs both operations. Degraded returns retain their existing compensation workflow and cannot be processed again by item-action.

## Deployment

Run both scripts before deploying the new backend:

- `scripts/20261114_warehouse_history.sql`
- `scripts/2026-11-19_return_fallback_and_order_handover.sql`

The history columns are nullable. Existing history is not synthesized or backfilled.
New warehouse logs populate both existing audit_group and activity_type for compatibility.
Frontend must recognize PACKED and call handover when the parcel is given to the carrier.
