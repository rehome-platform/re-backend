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

Page keys use the prefix `rehome:products:v2:` and a SHA-256 hash of all filters and
normalized page/size. The viewer is **not** part of the key: anonymous and logged-in
viewers share the same cached page. Cached JSON contains only
`PageResponse<ProductListCache.Item>` (each item is the `ProductSummaryResponse` plus the
seller's `memberId`), never JPA entities. `isMine` is computed per request in
`ProductServiceImpl.getProducts` from that `memberId` and is never stored; `memberId`
stays in Redis and is never returned by the API. Featured flags and pagination metadata
retain the API's current format. The generation token keeps its original key
`rehome:products:v1:generation`. Invalid price ranges are rejected before accessing Redis.

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
second request; change filter/page and verify separate results; list as the seller
and as another member and verify only the seller sees `isMine=true`; update a
price or reserve a product and verify the next list reflects the committed change;
stop Redis and verify the list still loads from the database.

Performance changes after the 25 November load test:

- The `getProducts` facade suspends transactions while accessing Redis or waiting
  for an identical request. `ProductCatalogReader.load` owns a short read-only
  transaction for database queries and DTO mapping only. A cache hit does not
  acquire a JDBC connection for catalogue loading. JWT account lookup is separate
  and remains unchanged, including current account-status checks.
- In-flight requests for the same filter/page/generation share one page read and,
  on miss, one DB load within a JVM, across viewers (the cached page holds no
  viewer-specific data). Redis generation is still checked per request; this is not
  a persistent local cache. No coalescing occurs across different Redis
  generations. Original application exceptions are
  propagated to waiters; completed/failed flights are removed so retries work.
- When Redis generation lookup fails, overlapping identical DB fallbacks are
  coalesced using the request hash and a local invalidation epoch. Disabled cache
  retains the original direct DB behavior. This does not coordinate DB loads
  across different backend replicas or eliminate Redis/authentication latency.
- Validate the impact by repeating the same Locust stages after restarting the
  backend; passing functional tests is not evidence of a particular user capacity.
