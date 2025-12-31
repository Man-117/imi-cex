# Centralized Crypto Exchange (CEX) System

An imitated centralized cryptocurrency exchange backend system built with Spring Boot 3.5.9, designed to showcase distributed consistency patterns and backend development best practices for interview scenarios.

## Overview

This project implements a monolithic Spring Boot application that handles user balance management, order management, and fee administration while integrating with BLNK (blockchain ledger) for immutable transaction records.

### Key Features

- **User Management**: Registration, authentication, and wallet management
- **Balance Management**: Add balance (imitation environment), check balance with caching
- **Order Management**: Create, cancel, and track trading orders with event sourcing
- **Fee Management**: Dynamic fee rates, transaction tracking, and admin controls
- **Distributed Consistency**: 
  - Idempotency for safe retries
  - Optimistic locking for concurrent updates
  - Event sourcing for audit trails
  - Saga pattern for multi-step transactions
- **Caching**: Redis-backed cache for balance, orders, and fee rates
- **Security**: JWT-based authentication with role-based access control

## Architecture

### Technology Stack

- **Java 21** - Latest LTS release with virtual threads support
- **Spring Boot 3.5.9** - Rapid application development framework
- **PostgreSQL 16** - Primary data store with JSONB support
- **Redis 7** - Caching and session management
- **Spring Security** - Authentication and authorization
- **Flyway** - Database migration management
- **Docker** - Containerization and deployment

### Directory Structure

```
src/main/java/org/william/cex/
├── api/
│   ├── controller/       # REST endpoints
│   ├── dto/             # Request/Response DTOs
│   └── exception/       # Custom exceptions
├── config/              # Spring configurations
├── domain/
│   ├── user/           # User domain logic
│   ├── order/          # Order domain logic
│   ├── fee/            # Fee domain logic
│   ├── blnk/           # BLNK integration
│   └── admin/          # Admin domain logic
└── infrastructure/
    ├── cache/          # Redis caching
    └── security/       # JWT and security utilities
```

## API Endpoints

### Authentication Endpoints

```
POST /api/v1/auth/register       - Register new user
POST /api/v1/auth/login          - Login and get JWT token
```

### User Endpoints

```
POST   /api/v1/balance/add       - Add balance to wallet (imitation)
GET    /api/v1/balance/{currency} - Check balance

POST   /api/v1/orders            - Create new order
GET    /api/v1/orders/{orderId}  - Check order status
DELETE /api/v1/orders/{orderId}  - Cancel order

GET    /api/v1/market/price/{pair} - Get simulated market price
GET    /api/v1/fees/{pair}       - Check fee rate for currency pair
```

### Admin Endpoints

```
GET    /api/v1/admin/account/balance  - Check firm's total balance/revenue
POST   /api/v1/admin/fees             - Create/update fee rates
GET    /api/v1/admin/fees             - List all fee rates
```

## Database Schema

### Key Tables

- **users** - User profiles with KYC status and roles
- **user_wallets** - Currency balances with optimistic locking (version column)
- **user_accounts** - Aggregate deposit/withdrawal tracking
- **orders** - Trading orders with status tracking
- **order_events** - Immutable append-only audit log
- **fee_rates** - Dynamic fee configuration
- **fee_transactions** - Immutable fee ledger
- **blnk_ledgers** - Sync state with BLNK system
- **blnk_transactions** - Pending BLNK transaction tracking
- **audit_logs** - Admin action trail

## Distributed Consistency Mechanisms

### 1. Optimistic Locking
- User wallets use `@Version` annotation for concurrent update detection
- Automatically incremented on save, prevents lost updates
- Throws `OptimisticLockingException` on conflict

### 2. Idempotency
- Request idempotency keys cached in Redis for 24 hours
- Duplicate requests return cached response
- Prevents double-charging or duplicate orders

### 3. Saga Pattern
Order lifecycle demonstrates saga with compensating transactions:
1. **Step 1**: Lock balance in wallet (compensates by unlock on failure)
2. **Step 2**: Create order, publish OrderCreatedEvent
3. **Step 3**: Post to BLNK, track with status PENDING
4. **Step 4**: BLNK webhook marks CONFIRMED, updates order status
5. **Compensation**: OrderCompensationEvent unlocks balance if BLNK fails

### 4. Event Sourcing
- `order_events` table is append-only (immutable)
- Every state change creates an event record
- Enables event replay, debugging, and audit trails

### 5. Cache Invalidation
- Balance cache cleared on wallet updates
- Order cache cleared on order state changes
- Fee rate cache invalidated on admin updates

## Getting Started

### Prerequisites

- Docker and Docker Compose (recommended)
- JDK 21 (for local development)
- Maven 3.9+

### Quick Start with Docker

1. Clone the repository
```bash
git clone <repo-url>
cd cex
```

2. Copy environment file
```bash
cp .env.example .env
# Edit .env with your BLNK configuration if needed
```

3. Start all services
```bash
docker-compose up -d
```

4. Application will be available at `http://localhost:8080/api`

### Local Development Setup

1. Start PostgreSQL and Redis
```bash
docker-compose up postgres redis -d
```

2. Build the project
```bash
mvn clean package
```

3. Run the application
```bash
mvn spring-boot:run
```

## Usage Examples

### 1. Register User
```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "SecurePassword123"
  }'

# Response:
{
  "token": "eyJhbGc...",
  "userId": 1,
  "email": "user@example.com",
  "role": "USER"
}
```

### 2. Add Balance (Imitation)
```bash
curl -X POST http://localhost:8080/api/v1/balance/add \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer 1" \
  -d '{
    "currency": "BTC",
    "amount": 1.5
  }'

# Response:
{
  "userId": 1,
  "currency": "BTC",
  "balance": 1.5,
  "lockedAmount": 0,
  "availableBalance": 1.5
}
```

### 3. Check Balance
```bash
curl http://localhost:8080/api/v1/balance/BTC \
  -H "Authorization: Bearer 1"
```

### 4. Create Order
```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer 1" \
  -d '{
    "orderType": "BUY",
    "baseCurrency": "BTC",
    "quoteCurrency": "USD",
    "amount": 0.5,
    "price": 43500.50
  }'

# Response:
{
  "id": 1,
  "userId": 1,
  "orderType": "BUY",
  "baseCurrency": "BTC",
  "quoteCurrency": "USD",
  "amount": 0.5,
  "price": 43500.50,
  "filledAmount": 0,
  "status": "PENDING",
  "createdAt": "2025-12-31T10:00:00",
  "updatedAt": "2025-12-31T10:00:00"
}
```

### 5. Get Order Status
```bash
curl http://localhost:8080/api/v1/orders/1 \
  -H "Authorization: Bearer 1"
```

### 6. Get Market Price
```bash
curl http://localhost:8080/api/v1/market/price/BTC%2FUSD
```

### 7. Get Fee Rate
```bash
curl http://localhost:8080/api/v1/fees/BTC%2FUSD
```

### 8. Admin: Check Account Balance
```bash
curl http://localhost:8080/api/v1/admin/account/balance \
  -H "Authorization: Bearer 1"

# Response:
{
  "totalDeposits": 50000,
  "totalWithdrawals": 10000,
  "totalFees": 150,
  "firmHoldings": 40150
}
```

### 9. Admin: Update Fee Rate
```bash
curl -X POST http://localhost:8080/api/v1/admin/fees \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer 1" \
  -d '{
    "currencyPair": "BTC/USD",
    "feePercentage": 0.002
  }'
```

## Key Design Patterns

### 1. Domain-Driven Design
- Clear separation between API, domain, and infrastructure layers
- Repository pattern for data access
- Service layer for business logic

### 2. Event Sourcing
- Immutable event log in `order_events` table
- Enables complete audit trail and event replay
- Supports debugging and compliance requirements

### 3. Saga Pattern
- Multi-step order processing with automatic rollback
- Compensating transactions for failure scenarios
- Essential for distributed transactions without 2PC

### 4. Cache-Aside Pattern
- Check cache first, load from DB if miss
- Write-through on updates
- TTL-based expiration for eventual consistency

### 5. Strangler Fig Pattern
- BLNK integration doesn't require source code changes
- Calls BLNK via REST API
- Tracks sync state locally for resilience

## Testing

Run tests with Maven:
```bash
mvn test
```

For integration tests with TestContainers:
```bash
mvn test -Pintegration
```

## Configuration

### Key Properties

Edit `src/main/resources/application.properties`:

```properties
# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/cex_db
spring.datasource.username=postgres
spring.datasource.password=postgres

# Redis
spring.data.redis.host=localhost
spring.data.redis.port=6379

# JWT
jwt.secret=your-secret-key-minimum-256-bits
jwt.expiration=86400000

# BLNK
blnk.api.url=http://localhost:5001
blnk.api.key=your-blnk-api-key
```

### Environment Variables for Docker

Create `.env` file:
```
BLNK_API_URL=http://blnk:5001
BLNK_API_KEY=your-key
JWT_SECRET=your-secret-key
```

## Deployment

### Docker Deployment

1. Build Docker image
```bash
docker build -t cex:latest .
```

2. Start with docker-compose
```bash
docker-compose up -d
```

3. View logs
```bash
docker-compose logs -f cex-app
```

### Health Checks

Application exposes health endpoint:
```bash
curl http://localhost:8080/api/actuator/health
```

## Interview Key Highlights

This project demonstrates several key backend developer competencies:

1. **Distributed Consistency**
   - Optimistic locking prevents lost updates
   - Event sourcing provides audit trail
   - Saga pattern handles complex workflows

2. **System Design**
   - Monolithic architecture with clear layering
   - Proper database schema with indexes and constraints
   - Integration with external systems (BLNK)

3. **Code Quality**
   - Domain-driven design principles
   - Separation of concerns (API/Service/Repository)
   - Comprehensive error handling

4. **Performance**
   - Redis caching for frequently accessed data
   - Database indexing on critical queries
   - Batch operations where applicable

5. **Reliability**
   - Transactional boundaries clearly defined
   - Graceful degradation on external failures
   - Audit logging for compliance

6. **DevOps**
   - Docker containerization
   - Database migrations with Flyway
   - Health checks and monitoring ready

## Future Enhancements

- [ ] Circuit breaker for BLNK API calls
- [ ] Distributed tracing with Spring Cloud Sleuth
- [ ] Message queues (RabbitMQ/Kafka) for async processing
- [ ] WebSocket support for real-time order updates
- [ ] Metrics collection with Micrometer/Prometheus
- [ ] Multi-step authentication (2FA)
- [ ] Rate limiting per user
- [ ] Advanced order types (stop-loss, take-profit)

## License

MIT License - See LICENSE file for details

## Contributing

Pull requests are welcome. For major changes, please open an issue first to discuss proposed changes.

