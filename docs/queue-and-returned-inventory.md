# Queue notifications and returned inventory

Run `scripts/2026-11-19_queue_notification_targets.sql` before deployment.
It drops the old notification target CHECK, without adding a replacement, and
removes the legacy unique constraint on `degraded_item_cases.return_request_id`.
The foreign key is preserved. One returned order may contain multiple products
and therefore multiple degraded cases.

New queue items notify moderators of the assigned warehouse. Global items notify
all moderators. Enqueuing an existing item sends no additional notification.
Notifications are delivered after commit through NotificationService: in-app,
WebSocket `/user/queue/notifications`, and the existing push delivery mechanism.
The notification retains the warehouse ID and uses QUEUE_TASK.

| Queue type | Notification target |
| --- | --- |
| FORM_REVIEW, INSPECTION | CONSIGNMENT_FORM |
| PACKING | ORDER |
| RETURN | RETURN_REQUEST |
| WITHDRAWAL | WITHDRAWAL |
| ORG_VERIFICATION | ORGANIZATION |
| LEGAL_CHANGE | LEGAL_CHANGE |
| VIOLATION_REPORT | VIOLATION_REPORT (new) |
| DONATION_PICKUP, DONATION_REASSIGN, DONATION_PREP | DONATION (new) |

`POST /degraded-item-cases` accepts ON_SALE and RETURNED products in the moderator's
warehouse. A pending case prevents opening another. The product moves to WAREHOUSE.
For RETURNED products, the latest associated return is locked before the product,
using the same lock order as return item-action. If that return is VALID with no
itemAction, the new case links to it and sets degraded=true. Item-action then
rejects the return with 409 RETURN_ITEM_DEGRADED. No additional buyer refund occurs.
Moderator photos and notes remain available on the new case.

`GET /inventory` and the moderator SKU lookup `GET /products/{id}` add nullable
`pendingReturnRequestId`. It is populated only for RETURNED products whose latest
return is VALID, has no itemAction, and is not marked degraded. Inventory resolves
all returned products on the page in a single query. Older returns do not override
a newer handled, invalid, or degraded return.

Suggested manual checks: enqueue twice and receive one notification; check another
warehouse's moderator gets none; open a case for a returned product and verify its
pendingReturnRequestId disappears and item-action returns RETURN_ITEM_DEGRADED.
