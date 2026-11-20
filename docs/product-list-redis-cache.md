# GET /products Redis cache

Uses the existing `spring.data.redis` connection. No new API keys or SQL migration
are required. Only the public catalog list is cached; product detail and inventory
retain their existing behavior.

Configuration:

```yaml
app:
  cache:
    products:
      enabled: ${PRODUCTS_CACHE_ENABLED:true}
      ttl: ${PRODUCTS_CACHE_TTL:30s}
```

Set `PRODUCTS_CACHE_ENABLED=false` to bypass reads, writes and invalidation.
A zero/negative TTL also disables caching. TTL accepts Spring Boot durations such
as `30s` or `1m`.

Keys use the prefix `rehome:products:v1:` and a SHA-256 hash of all filters,
normalized page/size, and viewer ID. Anonymous viewers have a separate key. Cached
JSON contains only `PageResponse<ProductSummaryResponse>`, never JPA entities;
`isMine`, featured flags and pagination metadata retain the API's current format.
Invalid price ranges are rejected before accessing Redis.

Writes through Product, ProductPhoto, Brand, Category and ConsignmentReceipt
repositories invalidate the catalog after successful transaction commit. This
covers checkout/reservations, cancellation, intake, repricing, moderation, photos,
and featured membership changes. The bulk `clearFeaturedByMemberId` update is
included. Reads do not invalidate. Multiple writes in a service transaction share
one invalidation; rolled-back transactions do not invalidate.

Invalidation changes a shared Redis generation token instead of scanning/deleting
keys. An older in-flight request can only populate the previous generation. Old
pages expire normally by TTL; all BE instances sharing Redis see the new token.

Redis read/write/invalidation errors are logged and do not replace the normal API
result with an error. The database loader runs once; its own exceptions still
propagate. If invalidation fails, an existing entry may remain for the rest of its
TTL. Clock-only changes (featured expiry) and direct SQL/EntityManager writes that
bypass repository mutators likewise become visible within the TTL. Run scripts
against the intended environment and allow the TTL to elapse before checking.

Manual verification: call the same list twice and observe no catalog SQL on the
second request; change filter/page/viewer and verify separate results; update a
price or reserve a product and verify the next list reflects the committed change;
stop Redis and verify the list still loads from the database.
