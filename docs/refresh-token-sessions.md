# Refresh token sessions

`POST /api/auth/refresh` accepts `{ "refreshToken": "..." }` without requiring a
valid access token. The response keeps the login fields: accessToken, refreshToken,
role, id, is_verified and tokenType.

The submitted token row is locked in the outer authentication transaction. Only
that row is rotated to a new random UUID and a new seven-day expiry. Other device
sessions remain valid. The old token cannot be reused after commit. A failed
transaction rolls back rotation, leaving the original token available.

Concurrent refreshes using the same old token are serialized: one can succeed;
the other is rejected once the old token is gone. The successful replacement is
not revoked by that rejected request. FE must still deduplicate refresh requests,
save both replacement tokens, and retry waiting API requests using the new access
token. For multiple browser tabs, coordinate token replacement across tabs too.

Refresh errors use HTTP 401 and the standard ApiResponse shape:

| code | Meaning |
| --- | --- |
| REFRESH_TOKEN_NOT_FOUND | Unknown token or an old token already rotated |
| REFRESH_TOKEN_EXPIRED | Expired token |
| REFRESH_TOKEN_REVOKED | Explicitly revoked session |

Inactive/banned accounts retain the existing ACCOUNT_BANNED response. Raw refresh
tokens are not included in error messages. Logout/password-change revocation
retains its existing account-wide behavior.

Access token lifetime remains 900000 ms (15 minutes); refresh lifetime remains
604800000 ms (7 days), unless deployment overrides configuration. Access expiry
alone is not a reason for FE to log out: handle ACCESS_TOKEN_EXPIRED by calling
refresh first. BE does not automatically replace tokens stored by the client.

No SQL migration or forced logout of existing valid refresh tokens is required.
