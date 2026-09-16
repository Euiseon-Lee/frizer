CREATE TABLE food_bulk_preview (
    request_id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    payload TEXT NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    result_count INTEGER CHECK (result_count > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX food_bulk_preview_expiry ON food_bulk_preview(expires_at) WHERE result_count IS NULL;
CREATE TABLE food_bulk_receipt (
    fingerprint VARCHAR(64) PRIMARY KEY,
    request_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
