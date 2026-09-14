-- A completed request survives later item changes. No inferred matching by food name.
CREATE TABLE food_registration_receipt (
    request_id UUID PRIMARY KEY,
    request_payload TEXT NOT NULL,
    food_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
