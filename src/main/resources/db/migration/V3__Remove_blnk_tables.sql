-- Migration to remove BLNK integration tables (no longer used)

-- Drop blnk_transactions table first (has foreign key)
DROP TABLE IF EXISTS blnk_transactions CASCADE;

-- Drop blnk_ledgers table
DROP TABLE IF EXISTS blnk_ledgers CASCADE;

-- Drop the blnk transaction status enum type if it exists
DROP TYPE IF EXISTS blnk_transaction_status CASCADE;

