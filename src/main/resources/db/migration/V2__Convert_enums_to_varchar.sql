-- Convert PostgreSQL ENUM columns to VARCHAR for better Hibernate/Java compatibility

-- Convert kyc_status column
ALTER TABLE users ALTER COLUMN kyc_status TYPE VARCHAR(50);

-- Convert role column
ALTER TABLE users ALTER COLUMN role TYPE VARCHAR(50);

-- Convert order_type column
ALTER TABLE orders ALTER COLUMN order_type TYPE VARCHAR(50);

-- Convert order status column
ALTER TABLE orders ALTER COLUMN status TYPE VARCHAR(50);

-- Convert event_type column
ALTER TABLE order_events ALTER COLUMN event_type TYPE VARCHAR(50);

-- Convert blnk transaction status column
ALTER TABLE blnk_transactions ALTER COLUMN status TYPE VARCHAR(50);

-- Convert fee_type column
ALTER TABLE fee_transactions ALTER COLUMN fee_type TYPE VARCHAR(50);

-- Drop the ENUM types if no longer needed (optional - comment out if other parts still use them)
-- DROP TYPE IF EXISTS user_role CASCADE;
-- DROP TYPE IF EXISTS kyc_status CASCADE;
-- DROP TYPE IF EXISTS order_type CASCADE;
-- DROP TYPE IF EXISTS order_status CASCADE;
-- DROP TYPE IF EXISTS order_event_type CASCADE;
-- DROP TYPE IF EXISTS blnk_transaction_status CASCADE;
-- DROP TYPE IF EXISTS fee_type CASCADE;

