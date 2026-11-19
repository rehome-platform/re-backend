# Business notifications

No new NotificationType is introduced. NotificationTargetType adds ORDER, RETURN_REQUEST, CONSIGNMENT_FORM and PAYOUT.

| Event | Recipient | Type | Target |
| --- | --- | --- | --- |
| Wallet or gateway payment succeeds | Buyer | ORDER_UPDATE | ORDER / orderId |
| First packing scan | Buyer | ORDER_UPDATE | ORDER / orderId |
| Shipment created (PACKED) | Buyer | ORDER_UPDATE | ORDER / orderId |
| Carrier handover or first transit webhook | Buyer | ORDER_UPDATE | ORDER / orderId |
| Delivered webhook | Buyer | ORDER_UPDATE | ORDER / orderId |
| Manual or automatic completion | Buyer | ORDER_UPDATE | ORDER / orderId |
| Buyer cancellation, payment expiry, carrier failure | Buyer | ORDER_UPDATE | ORDER / orderId |
| Product becomes SOLD on completion | Consignor | ORDER_UPDATE | PRODUCT / SKU |
| Seller payout credited | Consignor | WALLET_EVENT | PRODUCT / SKU |
| Form approved / rejected (including onsite rejection) | Member | QUEUE_TASK | CONSIGNMENT_FORM / formId |
| New or replacement receipt / reconsignment terms | Member | QUEUE_TASK | CONSIGNMENT_RECEIPT / receiptId |
| Stock intake / reconsignment activation | Member | QUEUE_TASK | PRODUCT / SKU |
| End-of-term decision opened | Member | TERM_EXPIRY | PRODUCT / SKU |
| Degraded case opened during inventory or return inspection | Consignor | TERM_EXPIRY | DEGRADED_ITEM_CASE / caseId |
| Return approved (including local fallback) / rejected | Buyer | ORDER_UPDATE | RETURN_REQUEST / returnId |
| Valid or lost return refund | Buyer | WALLET_EVENT | RETURN_REQUEST / returnId |
| Invalid return decision | Buyer | ORDER_UPDATE | RETURN_REQUEST / returnId |
| Bank payout success / failure | Member | WALLET_EVENT | PAYOUT / payoutId |

Bodies contain the order/receipt/form number or product name and applicable deadlines/tracking codes. The existing body-to-message response mapping is unchanged. Product targets use string SKUs; other targets use UUIDs.

## Transaction behavior

BusinessNotifications snapshots recipient ID, target, title and body while the business transaction is active. It registers an afterCommit callback, so rollback discards the notification. After commit, each notification runs through NotificationService in a REQUIRES_NEW transaction, loading its recipient again by ID. Failures in notification persistence or delivery are logged and do not change the committed business operation. Receipt edit/void notifications also use this dispatcher.

This is an in-process post-commit dispatcher, not a durable outbox: a process crash between commit and delivery can lose a notification, and failures are not automatically retried. Existing notification producers outside this task retain their previous behavior.

## Deploy and device validation

Run `scripts/2026-11-19_business_notification_targets.sql` on the backend's database before testing. It only drops the existing notification enum CHECKs, without recreating them. No data reset is required.

Device test: pay -> packing scan -> create shipment -> carrier handover -> delivered webhook -> complete -> seller payout. Check buyer and consignor accounts separately and tap each notification to verify its destination. Also test approved/rejected forms, new receipts, valid/invalid/lost returns and successful/failed payouts. Background push requires configured Firebase and a registered device token; the inbox/WebSocket path uses the existing notification infrastructure.

AI generation concurrency is unchanged in this change.
