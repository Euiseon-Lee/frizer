-- Physical delete for mistaken registrations: the item and its whole history are
-- removed in one transaction. The receipt has no FK to the deleted rows so the
-- completed request stays on record and a retried request never deletes or
-- recreates anything twice.
CREATE TABLE food_delete_receipt (
    user_id BIGINT NOT NULL REFERENCES app_user(user_id) ON DELETE RESTRICT,
    request_id UUID NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    source_id BIGINT NOT NULL,
    source_name VARCHAR(100) NOT NULL,
    item_count INTEGER NOT NULL CHECK (item_count > 0),
    history_count INTEGER NOT NULL CHECK (history_count >= 0),
    master_removed BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(user_id, request_id)
);
