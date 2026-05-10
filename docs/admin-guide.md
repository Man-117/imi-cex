# Centralized Crypto Exchange - Administrator Guide

This guide is for platform administrators who manage fee policies, monitor account-level totals, and operate admin authentication safely.

## 1. Prerequisites

- Service is running and reachable at `http://localhost:8080/api`
- You know the configured admin registration key (`ADMIN_REGISTRATION_KEY`)
- You have access to Swagger UI:
  - `http://localhost:8080/api/swagger-ui.html`

## 2. Admin bootstrap and login

### 2.1 Set admin registration key

Set a strong secret before first use:

```bash
export ADMIN_REGISTRATION_KEY='replace-with-strong-admin-secret'
```

Or in `.env` for Docker-based startup:

```env
ADMIN_REGISTRATION_KEY=replace-with-strong-admin-secret
```

### 2.2 Register first admin

`POST /api/v1/admin/register`

```json
{
  "email": "admin@example.com",
  "password": "StrongAdminPassword123!",
  "adminKey": "replace-with-strong-admin-secret"
}
```

Successful response includes a JWT token.

### 2.3 Login as admin

`POST /api/v1/admin/login`

```json
{
  "email": "admin@example.com",
  "password": "StrongAdminPassword123!"
}
```

Use the returned token:

`Authorization: Bearer <admin-jwt-token>`

## 3. Admin API capabilities

## 3.1 View account-level exchange totals

`GET /api/v1/admin/account/balance`

Returns:

- `totalDeposits`
- `totalWithdrawals`
- `totalFees`
- `firmHoldings`

## 3.2 Manage fee policy

### Create or update fee rate

`POST /api/v1/admin/fees`

```json
{
  "currencyPair": "BTC/USD",
  "feePercentage": 0.0025
}
```

Notes:

- `feePercentage` must be between `0` and `1`
- all updates are audited (`audit_logs`)

### List fee rates

`GET /api/v1/admin/fees`

Admin token is required.

## 3.3 Run and repair trade reconciliation

Use these endpoints to perform finance/ops reconciliation between trades, order fill state, and fee ledger records.

### Run reconciliation

`POST /api/v1/admin/reconciliation/trades/run?lookbackHours=24`

- `lookbackHours` must be in range `1` to `720`
- returns a run record with issue list for the inspected window
- run metadata is auditable via `audit_logs`

Common issue types:

- `UNSETTLED_TRADE`: trade is not marked settled
- `ORDER_FILLED_MISMATCH`: `orders.filled_amount` differs from reconciled trade sum
- `FEE_LEDGER_MISMATCH`: fee transactions differ from expected fee totals

### List recent runs

`GET /api/v1/admin/reconciliation/trades/runs`

Returns the latest reconciliation runs (without per-issue payload).

### Get run details

`GET /api/v1/admin/reconciliation/trades/runs/{runId}`

Returns run-level status and per-issue records.

### Auto-repair fixable breaks

`POST /api/v1/admin/reconciliation/trades/runs/{runId}/repair`

Auto-repair currently handles:

- missing trade settlement flags (`UNSETTLED_TRADE`)
- order filled amount drift (`ORDER_FILLED_MISMATCH`)
- missing fee entries where expected fee is greater than actual (`FEE_LEDGER_MISMATCH`)

Breaks that cannot be repaired safely are moved to `MANUAL_REVIEW`.

## 3.4 Automatic reconciliation scheduler

Automatic reconciliation is now supported through a background scheduler.

Default behavior:

- enabled: `true`
- schedule: every 15 minutes
- lookback window: last 24 hours
- automatic repair: enabled

Environment variables:

- `CEX_RECONCILIATION_AUTO_ENABLED` (default: `true`)
- `CEX_RECONCILIATION_AUTO_CRON` (default: `0 */15 * * * *`)
- `CEX_RECONCILIATION_AUTO_ZONE` (default: `UTC`)
- `CEX_RECONCILIATION_AUTO_LOOKBACK_HOURS` (default: `24`)
- `CEX_RECONCILIATION_AUTO_AUTO_REPAIR` (default: `true`)

When enabled, each scheduled run is persisted in reconciliation run history just like manual runs.

## 4. Operational guidance

## 4.1 Key rotation

- Rotate `JWT_SECRET` and `ADMIN_REGISTRATION_KEY` periodically.
- When rotating, reissue active admin sessions.

## 4.2 Least-privilege access

- Do not share admin credentials across operators.
- Use separate admin identities for auditing and accountability.

## 4.3 Verify fee changes

After fee update:

1. Check `GET /api/v1/admin/fees`
2. Validate expected pair/rate
3. Confirm downstream behavior via trade/fee transactions

## 4.4 Monitor health and metrics

- Health endpoint: `GET /api/actuator/health`
- Metrics endpoint: `GET /api/actuator/metrics`

Recommended checks:

- order lifecycle counters
- trade execution counters
- authentication error spikes

## 5. Troubleshooting

- `401 Unauthorized`: missing/expired token or invalid signature
- `403 Forbidden`: non-admin token on admin endpoint
- `400 Bad Request`: invalid fee payload or out-of-range percentage
- `500 Internal Server Error`: review application logs and database/redis connectivity

## 6. Security checklist

- Use long random values for `JWT_SECRET`
- Set and protect `ADMIN_REGISTRATION_KEY`
- Restrict network access to admin surfaces
- Use TLS in any non-local environment
- Store secrets in a secure secret manager instead of plaintext files
