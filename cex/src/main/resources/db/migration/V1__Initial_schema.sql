-- Create ENUM types
CREATE TYPE user_role AS ENUM ('ADMIN', 'USER');
CREATE TYPE kyc_status AS ENUM ('PENDING', 'APPROVED', 'REJECTED');
CREATE TYPE order_status AS ENUM ('PENDING', 'FILLED', 'CANCELLED', 'PARTIALLY_FILLED');
CREATE TYPE order_type AS ENUM ('BUY', 'SELL');
CREATE TYPE order_event_type AS ENUM ('CREATED', 'FILLED', 'PARTIALLY_FILLED', 'CANCELLED', 'COMPENSATION');
CREATE TYPE blnk_transaction_status AS ENUM ('PENDING', 'CONFIRMED', 'FAILED');
CREATE TYPE fee_type AS ENUM ('TRADING_FEE', 'WITHDRAWAL_FEE');

-- Users Table
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role user_role DEFAULT 'USER',
    kyc_status kyc_status DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT users_email_unique UNIQUE (email)
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);

-- User Account Table (for tracking deposits/withdrawals)
CREATE TABLE user_accounts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    total_deposits NUMERIC(20, 8) DEFAULT 0,
    total_withdrawals NUMERIC(20, 8) DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_accounts_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- User Wallets Table (with optimistic locking)
CREATE TABLE user_wallets (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    currency VARCHAR(10) NOT NULL,
    balance NUMERIC(20, 8) NOT NULL DEFAULT 0,
    locked_amount NUMERIC(20, 8) NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_wallets_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT user_wallets_unique_currency UNIQUE (user_id, currency)
);

CREATE INDEX idx_user_wallets_user_id ON user_wallets(user_id);
CREATE INDEX idx_user_wallets_currency ON user_wallets(currency);

-- Orders Table
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    order_type order_type NOT NULL,
    base_currency VARCHAR(10) NOT NULL,
    quote_currency VARCHAR(10) NOT NULL,
    amount NUMERIC(20, 8) NOT NULL,
    price NUMERIC(20, 8) NOT NULL,
    filled_amount NUMERIC(20, 8) DEFAULT 0,
    status order_status DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_orders_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT check_amount_positive CHECK (amount > 0),
    CONSTRAINT check_price_positive CHECK (price > 0)
);

CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_created_at ON orders(created_at DESC);
CREATE INDEX idx_orders_user_created ON orders(user_id, created_at DESC);

-- Order Events Table (immutable audit log)
CREATE TABLE order_events (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    event_type order_event_type NOT NULL,
    details JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_events_order_id FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
);

CREATE INDEX idx_order_events_order_id ON order_events(order_id);
CREATE INDEX idx_order_events_created_at ON order_events(created_at DESC);

-- Trades Table (matched orders)
CREATE TABLE trades (
    id BIGSERIAL PRIMARY KEY,
    buy_order_id BIGINT NOT NULL,
    sell_order_id BIGINT NOT NULL,
    amount NUMERIC(20, 8) NOT NULL,
    price NUMERIC(20, 8) NOT NULL,
    settlement_status VARCHAR(50) DEFAULT 'PENDING',
    settled_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_trades_buy_order_id FOREIGN KEY (buy_order_id) REFERENCES orders(id) ON DELETE SET NULL,
    CONSTRAINT fk_trades_sell_order_id FOREIGN KEY (sell_order_id) REFERENCES orders(id) ON DELETE SET NULL
);

CREATE INDEX idx_trades_created_at ON trades(created_at DESC);

-- Fee Rates Table
CREATE TABLE fee_rates (
    id BIGSERIAL PRIMARY KEY,
    currency_pair VARCHAR(20) NOT NULL,
    fee_percentage NUMERIC(8, 6) NOT NULL,
    admin_id BIGINT,
    effective_from TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_fee_rates_admin_id FOREIGN KEY (admin_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT check_fee_percentage CHECK (fee_percentage >= 0 AND fee_percentage <= 1)
);

CREATE INDEX idx_fee_rates_currency_pair ON fee_rates(currency_pair);
CREATE INDEX idx_fee_rates_effective_from ON fee_rates(effective_from DESC);

-- Fee Transactions Table (immutable ledger)
CREATE TABLE fee_transactions (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT,
    amount NUMERIC(20, 8) NOT NULL,
    fee_type fee_type NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_fee_transactions_order_id FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE SET NULL
);

CREATE INDEX idx_fee_transactions_order_id ON fee_transactions(order_id);
CREATE INDEX idx_fee_transactions_created_at ON fee_transactions(created_at DESC);

-- BLNK Ledger Table (sync state with BLNK)
CREATE TABLE blnk_ledgers (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    blnk_account_id VARCHAR(255) NOT NULL UNIQUE,
    blnk_status VARCHAR(50) DEFAULT 'PENDING',
    synced_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_blnk_ledgers_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_blnk_ledgers_user_id ON blnk_ledgers(user_id);
CREATE INDEX idx_blnk_ledgers_blnk_account_id ON blnk_ledgers(blnk_account_id);

-- BLNK Transactions Table (pending/completed BLNK posts)
CREATE TABLE blnk_transactions (
    id BIGSERIAL PRIMARY KEY,
    local_order_id BIGINT,
    blnk_transaction_id VARCHAR(255),
    status blnk_transaction_status DEFAULT 'PENDING',
    response_data JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_blnk_transactions_order_id FOREIGN KEY (local_order_id) REFERENCES orders(id) ON DELETE SET NULL
);

CREATE INDEX idx_blnk_transactions_status ON blnk_transactions(status);
CREATE INDEX idx_blnk_transactions_created_at ON blnk_transactions(created_at DESC);
CREATE INDEX idx_blnk_transactions_order_id ON blnk_transactions(local_order_id);

-- Administrators Table (subset of users with admin role)
CREATE TABLE administrators (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    permissions JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_administrators_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_administrators_user_id ON administrators(user_id);

-- Audit Logs Table (admin actions)
CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    admin_id BIGINT NOT NULL,
    action VARCHAR(255) NOT NULL,
    resource VARCHAR(255),
    changes JSONB,
    ip_address VARCHAR(45),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_logs_admin_id FOREIGN KEY (admin_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_audit_logs_admin_id ON audit_logs(admin_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at DESC);
CREATE INDEX idx_audit_logs_resource ON audit_logs(resource);

-- Idempotency Keys Table (for tracking idempotent requests)
CREATE TABLE idempotency_keys (
    id BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    response_data JSONB NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_idempotency_keys_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_idempotency_keys_user_id ON idempotency_keys(user_id);
CREATE INDEX idx_idempotency_keys_expires_at ON idempotency_keys(expires_at);

