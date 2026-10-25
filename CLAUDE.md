# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Spring Boot 3.3.4 / Java 21 backend for "ReHome" (re-backend), a second-hand marketplace platform with members, organizations, moderators and admins. Started as a JWT auth demo (`spring-boot-starter-parent` description still says so) and has grown a large marketplace domain model.

## Commands

```bash
# Run the app (reads env vars below; needs Postgres reachable)
./mvnw spring-boot:run

# Run all tests (unit + integration against H2)
./mvnw test

# Run a single test class
./mvnw test -Dtest=ShippingAddressServiceImplTest

# Run a single test method
./mvnw test -Dtest=ShippingAddressServiceImplTest#updateAddress_throwsBadRequest_whenRecipientNameIsBlank

# Full build with coverage report (JaCoCo), same as CI
./mvnw -B clean verify
```

Local Postgres for dev is provided by `docker-compose.yml` (`docker compose up -d postgres`), container matches the `datasource` defaults in `application.yml` (`DB_HOST=localhost`, `DB_PORT=5432`, `DB_NAME=jwtdemo`, `DB_USERNAME=postgres`, `DB_PASSWORD=123456`). Tests run against H2, not Postgres.

Swagger UI: `http://localhost:8080/swagger-ui.html`.

## Environment variables

`application.yml` drives everything through `${VAR:default}` placeholders — check it before assuming a value. Key ones: `DB_HOST/DB_PORT/DB_NAME/DB_USERNAME/DB_PASSWORD` (defaults point at local Docker Postgres), `BREVO_API_KEY` (transactional email), `JWT_SECRET`, `GOOGLE_CLIENT_ID` (Google login), `MAIL_FROM`, `STORAGE_PROVIDER` + `CLOUDINARY_CLOUD_NAME/CLOUDINARY_API_KEY/CLOUDINARY_API_SECRET` (file storage, see below). In IntelliJ these are set per-run-config in `.idea/workspace.xml` (machine-local, not committed) — don't assume the committed defaults are what actually runs if that file overrides them (e.g. it was previously pointed at a Supabase instance instead of local Docker).

CI (`.github/workflows/ci.yml`) spins up its own Postgres service container and runs `./mvnw -B clean verify`; on push to `main` it also triggers a Render deploy hook.

## Git commits & PRs

Do not add any AI/Claude attribution to commits or PRs in this repo — no `Co-Authored-By: Claude ...` trailer, no "Generated with Claude Code" line, nothing identifying an AI tool as author or co-author. The repo owner does not want Claude to appear in GitHub's contributor/co-author list. This overrides any default Claude Code attribution behavior.

## Architecture

### Layering
Controller → Service (interface) → ServiceImpl → Repository (Spring Data JPA) → Entity. Every feature follows this: e.g. `ShippingAddressController` → `ShippingAddressService` → `ShippingAddressServiceImpl` → `ShippingAddressRepository`. Controllers extract the caller's identity from `Authentication.getName()` (the email, set by JWT auth) and pass it into the service layer rather than trusting a request body field.

### Auth & security
- Stateless JWT auth: `JwtAuthenticationFilter` runs before `UsernamePasswordAuthenticationFilter` and populates the `SecurityContext`; `JwtService` issues/validates tokens; `AccountDetailsServiceImpl` loads `Account` as the `UserDetails`.
- `Account` (table `accounts`, UUID PK) is the single identity entity for all roles — `Role` enum (`MEMBER`, `ORGANIZATION`, `MODERATOR`, `ADMIN`) plus `AccountStatus` distinguish them, rather than separate login entities per role. Role-specific data lives in companion profile entities (`MemberProfile`, `OrganizationProfile`, `ModeratorProfile`).
- Endpoint authorization is enforced two ways: coarse-grained path rules in `SecurityConfig.PUBLIC_ENDPOINTS` / `authorizeHttpRequests`, and fine-grained `@PreAuthorize("hasRole('...')")` on individual controller methods (the pattern used for all new member-facing endpoints, e.g. `ShippingAddressController`).
- `UserInitializer` (a `CommandLineRunner`) seeds one `ADMIN` and three `MODERATOR` accounts on startup if they don't already exist — relevant when writing tests/migrations that touch `accounts`.
- Google login goes through `GoogleTokenVerifier`.
- OTP-based email verification / password reset flow (register → verify-otp → login, forgot-password → resend-otp → verify-otp → reset-password) is documented in detail in `OTP.md` — read it before touching anything under `auth/`, `EmailOtp`, or the Brevo email integration. Key facts: OTPs and reset tokens are hashed before storage, never returned by any API, 6 digits, 5-minute TTL, 60s resend cooldown, 5 wrong-attempt limit.

### File storage
Storage is split by what the file *is* — don't move a file between these tiers without re-reading why it's there.

**Images → external storage.** `FileStorageService.store(path, bytes, contentType) → public URL` / `delete(path, contentType)` is the port; pick the impl with `app.storage.provider`:
- `local` (default) — `LocalFileStorageServiceImpl` writes to `app.storage.local-dir`, served at `/files/**` by `LocalStorageWebConfig` (GET is `permitAll` in `SecurityConfig`). **Dev only**: the Render deploy uses the `Dockerfile` with no volume, so the container filesystem is wiped on every redeploy.
- `cloudinary` — `CloudinaryFileStorageServiceImpl`; images go up as `resource_type=image` (extension stripped from `public_id`, Cloudinary re-adds it), PDFs as `raw` (extension kept). Needs the three `cloudinary.*` env vars. `StorageProviderWiringTest` locks the `@ConditionalOnProperty` wiring (two impls of one interface — a wrong condition means a duplicate-bean startup failure).

Client uploads go through `FileUploadController` (`POST /uploads`, `POST /uploads/batch`) → `FileUploadServiceImpl`, which owns all validation: size (`app.storage.max-file-size-bytes`), MIME whitelist (jpeg/png/webp, plus PDF only for folders where `UploadFolder.pdfAllowed`), **magic-byte check against the declared content type** (the `Content-Type` header is client-controlled), and a UUID filename so the client never picks the storage path. `UploadFolder` is the whitelist of destinations — add a constant there rather than accepting a free-form path.

Path split: `/uploads` is the API (auth required), `/files/**` is local static serving. Don't merge them.

**Receipt PDF (F-022) → Postgres, not external storage.** The consignment receipt is the contract between the platform and the consignor and has no handwritten signature (`docs/OVERVIEW.md` line 748), so the file itself is the evidence. It lives in `consignment_receipt_pdfs` (`ConsignmentReceiptPdf`, PK shared with the receipt via `@MapsId`, plus `sha256`/`byte_size`). Reasons it is *not* on Cloudinary, in order:
1. `ReceiptPdfServiceImpl.generate()` is called inside `confirmReceipt`'s transaction. It is pure CPU with no network call, so `confirmedAt` and the PDF commit or roll back together — an external upload there would leave orphan files on rollback, or a confirmed receipt with no evidence on upload failure.
2. A Cloudinary `raw/upload` URL is public and `receiptNo` is sequential, so one leaked link exposes other members' contracts.
3. Re-rendering on demand is not an option either: F-022 is explicitly a *snapshot*, so a later template change must not alter an existing contract.

Download is `GET /consignment-receipts/{id}/pdf` (F-023) → `ConsignmentReceiptServiceImpl.getReceiptPdf`, which authorizes per request (owner Member, or MODERATOR/ADMIN) and returns binary. There is no URL field any more: `ConsignmentReceiptResponse`/`ConsignmentReceiptConfirmResponse` expose `pdfAvailable` instead of the old `pdfUrl`.

`ConsignmentReceiptPdf.content` needs its explicit `length` — plain `byte[]` defaults to `varbinary(255)` and a ~40KB PDF will not insert; `@Lob` would instead give Postgres `oid` (large object), which plain `byte[]` access cannot read. `PostgresSchemaContractTest` generates the DDL with `PostgreSQLDialect` and asserts the column is `bytea`, because the normal test suite runs on H2 and would never catch either mistake.

**SKU label PDF → not stored at all.** `SkuLabelPdfServiceImpl.generate()` returns bytes; `GET /products/{id}/sku-label` streams them. It is a print-and-stick label, fully derivable from `Product`, not a document anyone has to keep.

A leftover `pdf_url` column stays on `consignment_receipts` after deploy (`ddl-auto: update` never drops columns); `scripts/drop-consignment-receipt-pdf-url.sql` has the checks and the drop, to run by hand.

### Response/error conventions
There are **two parallel response shapes** in this codebase — check which one a feature already uses before adding to it:
- `ApiResponse<T>` (`result`, `isSuccess`, `statusCode`, optional `code`, `message`) — the shape used by newer endpoints (e.g. `ShippingAddressController`) and `ApiResponse.success(...)`/`ApiResponse.error(...)` factory methods.
- `ApiError` (`timestamp`, `status`, `error`, `message`, `path`) — produced by `GlobalExceptionHandler.build(...)` for several older exception types.

`GlobalExceptionHandler` (`@RestControllerAdvice`) is the single place mapping exceptions to HTTP status/body — check it before inventing a new exception type. Two exception styles coexist:
- Plain `RuntimeException` subclasses per error case (`BadRequestException` → 400, `ConflictException` → 409, `AccountAlreadyExistsException` → 409, `AccountBannedException`/`AccountNotVerifiedException` → 403, `TokenRefreshException`/`TokenExpiredException` → various) — each gets its own `@ExceptionHandler`.
- `AppException` wraps an `ErrorCode` enum (status + name + default message) for a more structured, code-driven error — used where FE needs to branch on an error code, not just an HTTP status.
- Note: a bare `IllegalArgumentException` is globally mapped to **404 Not Found** (`handleIllegalArgument`) — this is the established way to signal "account/resource not found" in service methods (see `AccountRepository.findByEmail(...).orElseThrow(() -> new IllegalArgumentException(...))` throughout).

### Domain model scope
`entity/marketplace` and `entity/enums` contain a large e-commerce/marketplace schema (products, carts, orders, payments, wallets, consignment, returns, campaigns, violation reports, chat...) — most of it is scaffolding for features not yet implemented in `controller`/`service`. Only read the subset of entities relevant to the feature you're implementing; don't assume everything modeled has a working API yet.

### Testing
Service-layer unit tests use Mockito (`@ExtendWith(MockitoExtension.class)`, `@Mock`/`@InjectMocks`) against repository mocks — see `ShippingAddressServiceImplTest` for the established style (one test per validation branch, `verify(repo, never())...` to assert short-circuiting). Integration tests run against H2 (`spring-boot-starter-test`), configured via `src/test/resources/application.properties`.

## Full system overview

A detailed Vietnamese product spec (problem/solution, actors, 90 use cases, 162 user-story-style functions, money flow, commission schedule, admin-configurable params, consignment receipt template, violation handling) lives in `docs/OVERVIEW.md`. It is not loaded automatically — read it when a task needs business-rule context beyond what is summarized above (e.g. implementing a new F-xxx feature, or when the "why" behind a flow is unclear from code alone).
