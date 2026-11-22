PRAGMA foreign_keys = ON;
CREATE TABLE IF NOT EXISTS clients (
 id INTEGER PRIMARY KEY AUTOINCREMENT,
 full_name TEXT NOT NULL CHECK(length(trim(full_name))>0)
);
CREATE TABLE IF NOT EXISTS accounts (
 id INTEGER PRIMARY KEY AUTOINCREMENT,
 client_id INTEGER NOT NULL REFERENCES clients(id),
 type TEXT NOT NULL CHECK(type IN ('CARD','DEPOSIT','LOAN')),
 currency TEXT NOT NULL CHECK(currency IN ('KZT','USD','EUR')),
 status TEXT NOT NULL CHECK(status IN ('ACTIVE','BLOCKED')),
 balance_minor INTEGER NOT NULL DEFAULT 0 CHECK(typeof(balance_minor)='integer' AND balance_minor>=0),
 debt_minor INTEGER NOT NULL DEFAULT 0 CHECK(typeof(debt_minor)='integer' AND debt_minor>=0),
 CHECK(type='LOAN' OR debt_minor=0)
);
-- Raw request account IDs intentionally have no FK: nonexistent IDs can be logged as FAILED.
CREATE TABLE IF NOT EXISTS transfer_requests (
 id TEXT PRIMARY KEY NOT NULL,
 source_id INTEGER NOT NULL,
 target_id INTEGER NOT NULL,
 amount_minor INTEGER NOT NULL CHECK(typeof(amount_minor)='integer' AND amount_minor>0),
 type TEXT NOT NULL CHECK(type IN ('TRANSFER','EXCHANGE')),
 status TEXT NOT NULL CHECK(status IN ('PENDING_CONFIRMATION','SUCCESS','FAILED','CANCELLED','EXPIRED')),
 reason TEXT NOT NULL,
 created_at_ms INTEGER NOT NULL,
 expires_at_ms INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS operations (
 id TEXT PRIMARY KEY NOT NULL REFERENCES transfer_requests(id),
 client_id INTEGER REFERENCES clients(id),
 source_id INTEGER NOT NULL,
 target_id INTEGER NOT NULL,
 type TEXT NOT NULL CHECK(type IN ('TRANSFER','EXCHANGE')),
 source_minor INTEGER NOT NULL,
 target_minor INTEGER NOT NULL,
 source_currency TEXT,
 target_currency TEXT,
 rate TEXT NOT NULL,
 occurred_at_ms INTEGER NOT NULL,
 status TEXT NOT NULL CHECK(status IN ('SUCCESS','FAILED')),
 reason TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_operations_client_time ON operations(client_id,occurred_at_ms,id);
CREATE INDEX IF NOT EXISTS idx_requests_status ON transfer_requests(status);
