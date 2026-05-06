# Centralized Crypto Exchange (Interview Project)

This repository contains a Spring Boot backend that simulates the core flow of a centralized crypto exchange for interview discussion and demo purposes.

## What this project is

- A monolithic exchange backend simulation
- Focused on correctness and consistency patterns
- Built for demonstrating architecture and backend engineering trade-offs

## What is implemented

- JWT authentication and RBAC (`USER`, `ADMIN`)
- Wallet balances with optimistic locking
- Balance funding endpoint (imitation deposit flow)
- Order placement, cancellation, partial/full fills
- Minimal price-time matching between BUY/SELL orders
- Trade settlement between wallets
- Trading fee calculation and immutable fee ledger records
- Order event log (`order_events`) for auditability
- Redis-backed cache for balances/orders/fee rates/idempotency
- Idempotency support for write endpoints (`balance/add`, `orders`, `orders/{id}` cancel)
- Admin endpoints for fee updates and firm account summaries
- Metrics counters for order lifecycle and matching outcomes

## What is intentionally out of scope (for now)

- Real custody rails or blockchain settlement
- Real market data feed
- Production-grade risk engine (margin, liquidation, AML/KYC workflows)
- Distributed matching and multi-region fault tolerance

## Architecture

### Stack

- Java 21
- Spring Boot 3.5.9
- PostgreSQL + Flyway
- Redis
- Spring Security + JWT
- Docker / docker-compose

### Main modules

```
src/main/java/org/william/cex/
├── api/                 # controllers, DTOs, exceptions
├── config/              # security, redis, rest clients
├── domain/
│   ├── admin/           # admin auth and admin operations
│   ├── fee/             # fee rates and fee transactions
│   ├── idempotency/     # request idempotency persistence
│   ├── order/           # orders, events, matching, trades
│   └── user/            # users, wallets, accounts
└── infrastructure/
    ├── cache/           # redis cache manager
    ├── metrics/         # Micrometer counters
    └── security/        # JWT filter/provider and auth utilities
```

## Core API endpoints

### Auth

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/admin/register`
- `POST /api/v1/admin/login`

### User

- `POST /api/v1/balance/add`
- `GET /api/v1/balance/{currency}`
- `POST /api/v1/orders`
- `GET /api/v1/orders/{orderId}`
- `DELETE /api/v1/orders/{orderId}`
- `GET /api/v1/market/price/{base}/{quote}`
- `GET /api/v1/fees/{pair}`

### Admin

- `GET /api/v1/admin/account/balance`
- `POST /api/v1/admin/fees`
- `GET /api/v1/admin/fees`

## Data model highlights

- `user_wallets`: source of truth for balances + locked amounts (`@Version`)
- `orders`: order intent and fill state
- `order_events`: append-only lifecycle events
- `trades`: matched execution records
- `fee_rates`: mutable fee configuration
- `fee_transactions`: immutable fee ledger
- `idempotency_keys`: persisted write deduplication
- `audit_logs`: admin action trace

## Consistency and safety mechanisms

- Optimistic locking on wallets
- Transactional order creation/cancellation/matching/settlement
- Idempotency for repeated client retries
- Cache invalidation on wallet/order/fee updates
- Admin action audit logging
- Basic risk limits (max order notional, max daily notional)

## Local run

```bash
docker-compose up -d
```

App base URL:

- `http://localhost:8080/api`

## Test run

```bash
mvn test
```

Integration tests use Testcontainers for PostgreSQL + Redis so local developer services are not required.

## Interview framing

Use this project as a **CEX core ledger and order workflow simulator**. It demonstrates:

- wallet locking/unlocking invariants
- matching + settlement flow
- idempotent API design
- fee accounting
- secure endpoint design and admin controls
- migration-backed persistence with observable metrics

