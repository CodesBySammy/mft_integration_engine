CREATE TABLE IF NOT EXISTS mft_audit_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_timestamp TIMESTAMP NOT NULL,
    run_id VARCHAR(100),
    route_id VARCHAR(100),
    connection_id VARCHAR(255),
    source_connection_id VARCHAR(100),
    destination_connection_id VARCHAR(100),
    source_address VARCHAR(255),
    destination_address VARCHAR(255),
    operation VARCHAR(100) NOT NULL,
    source_file VARCHAR(500),
    destination_file VARCHAR(500),
    file_size BIGINT,
    checksum VARCHAR(128),
    duration_ms BIGINT,
    status VARCHAR(50) NOT NULL,
    error_code VARCHAR(100),
    details TEXT
);

CREATE TABLE IF NOT EXISTS mft_transfer_runs (
    run_id VARCHAR(100) PRIMARY KEY,
    route_id VARCHAR(100) NOT NULL,
    source_connection_id VARCHAR(100),
    destination_connection_id VARCHAR(100),
    source_filename VARCHAR(500),
    destination_filename VARCHAR(500),
    status VARCHAR(50) NOT NULL,
    source_checksum VARCHAR(128),
    output_checksum VARCHAR(128),
    bytes_transferred BIGINT DEFAULT 0,
    record_count BIGINT DEFAULT 0,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    error_code VARCHAR(100),
    error_message TEXT
);

CREATE TABLE IF NOT EXISTS mft_idempotency_records (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    route_id VARCHAR(100) NOT NULL,
    source_connection_id VARCHAR(100),
    remote_path VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL,
    source_checksum VARCHAR(128) NOT NULL,
    run_id VARCHAR(100),
    status VARCHAR(50) NOT NULL,
    first_processed_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    destination_path VARCHAR(500)
);

CREATE INDEX IF NOT EXISTS idx_audit_run_id ON mft_audit_events(run_id);
CREATE INDEX IF NOT EXISTS idx_audit_route_id ON mft_audit_events(route_id);
CREATE INDEX IF NOT EXISTS idx_transfer_route ON mft_transfer_runs(route_id);
