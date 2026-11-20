# Chat word masking

Run `scripts/2026-11-20_chat_original_content.sql` before deployment. It adds nullable
`messages.original_content`; older rows stay unchanged with null originalContent.

Only chat calls `ContentModerationService.mask`. Other callers of `evaluate` and
`check` retain AUTO_REJECT blocking. Chat masks all action levels and passes the
highest action to recordMatch. Audit metadata contains the action, not private text.

Phone/email/bank account masking runs first. `content` stores the final masked text;
`originalContent` stores the sensitive-masked text before violation masking, only
when violation words were masked. Clean and image-only messages leave it null.

Send responses, history, and `/topic/conversations.{id}` include originalContent,
using existing participant permissions. Inbox previews and push bodies use content.
Vietnamese accents, decomposed combining marks, emoji, inserted punctuation and
overlapping phrases are covered while preserving original character positions.

Example: `Đồ ngu, SĐT 0912345678` becomes content `** ***, SĐT ***` and
originalContent `Đồ ngu, SĐT ***`.
