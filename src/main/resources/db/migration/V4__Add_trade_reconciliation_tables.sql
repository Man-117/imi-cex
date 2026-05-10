CREATE TABLE trade_reconciliation_runs (
    id BIGSERIAL PRIMARY KEY,
    reconciliation_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    scope_from TIMESTAMP NOT NULL,
    scope_to TIMESTAMP NOT NULL,
    total_trades_scanned INTEGER NOT NULL DEFAULT 0,
    total_issues INTEGER NOT NULL DEFAULT 0,
    auto_repaired_issues INTEGER NOT NULL DEFAULT 0,
    failure_reason VARCHAR(500),
    summary JSONB,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

CREATE INDEX idx_trade_recon_runs_started_at ON trade_reconciliation_runs(started_at DESC);
CREATE INDEX idx_trade_recon_runs_status ON trade_reconciliation_runs(status);

CREATE TABLE trade_reconciliation_issues (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL,
    issue_type VARCHAR(80) NOT NULL,
    severity VARCHAR(30) NOT NULL,
    reference_type VARCHAR(30) NOT NULL,
    reference_id BIGINT NOT NULL,
    message VARCHAR(500) NOT NULL,
    expected_value NUMERIC(20, 8),
    actual_value NUMERIC(20, 8),
    metadata JSONB,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP,
    CONSTRAINT fk_trade_recon_issue_run FOREIGN KEY (run_id) REFERENCES trade_reconciliation_runs(id) ON DELETE CASCADE
);

CREATE INDEX idx_trade_recon_issues_run_id ON trade_reconciliation_issues(run_id);
CREATE INDEX idx_trade_recon_issues_status ON trade_reconciliation_issues(status);
CREATE INDEX idx_trade_recon_issues_type ON trade_reconciliation_issues(issue_type);
