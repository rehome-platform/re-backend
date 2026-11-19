# Counter tasks and packing photos

## Counter

`GET /counter/members/{accountId}/tasks` includes PENDING, APPROVED and INSPECTING forms.
Each `pendingForms` item adds:

| Field | Meaning |
| --- | --- |
| receiptId | Latest non-VOIDED receipt UUID; null if none |
| confirmedAt | Member confirmation time of that receipt; null until confirmed |
| resumeStep | REVIEW_FORM, START_INSPECTION, INSPECTION, AWAIT_MEMBER_CONFIRM, STOCK_IN, COMPLETED or NONE |
| productSku | Product SKU linked through the form, including the original product after reconsignment; null before intake |
| productStatus | Current product status; null before intake |
| displayStatus | productStatus when a product exists; otherwise resumeStep |

The existing form `status` is unchanged. FE should use resumeStep to select the next screen and displayStatus to distinguish confirmed-but-not-stocked forms from listed products.

## Packing evidence

1. Upload images through the existing `/uploads` API with folder `ORDER_PACKING`.
2. Save their HTTPS URLs with `PUT /orders/{id}/pack/photos`:

```json
{"photoUrls": ["https://storage.example.com/packed.jpg"]}
```

Only a MODERATOR in the order's warehouse who currently holds its PACKING queue task can save photos. The order must be PAID/PACKING and have no tracking code. Maximum 5 images, URLs at most 2048 characters. The array replaces the previous photos; `[]` clears it before shipment. Existing clients can still create shipments without photos.

Response data is the saved URL array. Once a shipment exists, photos cannot be modified through this API. Every update is audited.

`GET /orders/{id}` returns `packingPhotoUrls`, with an empty array for legacy orders.

## Return comparison

Return request responses (including moderator `GET /return-requests/{id}/detail`) add `packingPhotoUrls` from the original order. Existing `photoUrls` remains the buyer's return evidence; `initialConsignmentPhotoUrls` remains the original product evidence. FE can show these galleries side by side for manual inspection. There is no automatic image comparison or change to refund decisions.

## Database

Run `scripts/2026-11-19_order_packing_photos.sql` before deploying with schema validation. It adds only the photo collection table and its PK/FK, without enum CHECK constraints. No backfill is needed.
