-- Item moves keep their identity and history. No FK to masters: a vacated master may be removed.
CREATE TABLE food_item_move_receipt (
    request_id UUID PRIMARY KEY,
    fingerprint VARCHAR(64) NOT NULL,
    food_id BIGINT NOT NULL,
    source_id BIGINT NOT NULL,
    target_id BIGINT NOT NULL,
    source_name VARCHAR(100) NOT NULL,
    target_name VARCHAR(100) NOT NULL,
    target_category VARCHAR(50),
    source_removed BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (source_id <> target_id)
);
CREATE INDEX ix_item_move_food ON food_item_move_receipt(food_id);
CREATE INDEX ix_item_move_removed_source ON food_item_move_receipt(source_id) WHERE source_removed;
