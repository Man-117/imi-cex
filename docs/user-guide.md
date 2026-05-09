# Centralized Crypto Exchange - User Guide

This guide explains how to run the service, authenticate, and use the main API flows.

## 1. Run the backend

### Option A: Docker Compose

```bash
docker-compose up -d
```

Base URL:

- `http://localhost:8080/api`

### Option B: Local Java runtime

Prerequisites:

- Java 21
- Maven
- PostgreSQL
- Redis

Run:

```bash
mvn spring-boot:run
```

## 2. API documentation (Swagger UI)

After startup, open:

- Swagger UI: `http://localhost:8080/api/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/api/v3/api-docs`

Use Swagger UI to inspect request/response schemas and test endpoints directly.

## 3. Authentication flow

### 3.1 Register a user

`POST /api/v1/auth/register`

```json
{
  "email": "user@example.com",
  "password": "StrongPassword123!"
}
```

Response includes a JWT token.

### 3.2 Login

`POST /api/v1/auth/login`

```json
{
  "email": "user@example.com",
  "password": "StrongPassword123!"
}
```

Use the returned token in protected endpoints:

`Authorization: Bearer <jwt-token>`

## 4. Core user operations

### 4.1 Add balance

`POST /api/v1/balance/add`

Headers:

- `Authorization: Bearer <jwt-token>`
- Optional idempotency: `Idempotency-Key: <unique-key>`

Body:

```json
{
  "currency": "USD",
  "amount": 10000
}
```

### 4.2 Check balance

`GET /api/v1/balance/{currency}`

Example:

`GET /api/v1/balance/USD`

### 4.3 Create order

`POST /api/v1/orders`

Headers:

- `Authorization: Bearer <jwt-token>`
- Optional idempotency: `Idempotency-Key: <unique-key>`

Body:

```json
{
  "orderType": "BUY",
  "baseCurrency": "BTC",
  "quoteCurrency": "USD",
  "amount": 0.25,
  "price": 45000
}
```

Notes:

- Matching and settlement are performed automatically when a compatible counter-order exists.
- Risk limits apply for order notional and rolling daily notional.

### 4.4 Get order

`GET /api/v1/orders/{orderId}`

### 4.5 Cancel order

`DELETE /api/v1/orders/{orderId}`

Headers:

- `Authorization: Bearer <jwt-token>`
- Optional idempotency: `Idempotency-Key: <unique-key>`

## 5. Market and fee endpoints

- `GET /api/v1/market/price/{base}/{quote}`
- `GET /api/v1/fees/{pair}`

Examples:

- `/api/v1/market/price/BTC/USD`
- `/api/v1/fees/BTC/USD`

## 6. Admin operations

### 6.1 Admin registration/login

- `POST /api/v1/admin/register`
- `POST /api/v1/admin/login`

### 6.2 Fee management

- `POST /api/v1/admin/fees` (admin token required)
- `GET /api/v1/admin/fees` (admin token required)

### 6.3 Account overview

- `GET /api/v1/admin/account/balance` (admin token required)

## 7. Idempotency behavior

For write operations, include a stable `Idempotency-Key` header per client operation.

If the same key is reused by the same user, the server returns the original stored response.
If the key is reused by a different user, the request is rejected with conflict.

## 8. Common troubleshooting

- `401 Unauthorized`: missing/expired/invalid JWT.
- `403 Forbidden`: endpoint requires admin privileges.
- `400 Bad Request`: validation, risk guardrails, or business rule failure.
- Swagger not loading: verify app context path (`/api`) and URL `/api/swagger-ui.html`.
