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
