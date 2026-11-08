-- A cooking operation is one stock transformation, with immutable item histories.
-- Item identifiers on the operation are historical references: deleting a purchase
-- must not erase what was used in another purchase's cooking record.
ALTER TABLE food_item ADD COLUMN cooked_at DATE;
CREATE TABLE cooking_record (
    user_id BIGINT NOT NULL REFERENCES app_user(user_id) ON DELETE RESTRICT,
    cooking_id UUID NOT NULL,
    request_payload TEXT NOT NULL,
    result_food_id BIGINT,
    food_name VARCHAR(100) NOT NULL,
    quantity_amount NUMERIC(11,2) NOT NULL CHECK (quantity_amount > 0),
    original_quantity_amount NUMERIC(11,2) NOT NULL CHECK (original_quantity_amount > 0),
    quantity_unit VARCHAR(4) NOT NULL CHECK (length(trim(quantity_unit)) > 0),
    cooked_at DATE NOT NULL,
    version_no BIGINT NOT NULL DEFAULT 0,
    result_history_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY(user_id,cooking_id)
);
CREATE TABLE cooking_ingredient (
    user_id BIGINT NOT NULL,
    cooking_id UUID NOT NULL,
    food_id BIGINT NOT NULL,
    food_name VARCHAR(100) NOT NULL,
    quantity_unit VARCHAR(4) NOT NULL,
    used_amount NUMERIC(11,2) NOT NULL CHECK (used_amount >= 0),
    original_used_amount NUMERIC(11,2) NOT NULL CHECK (original_used_amount > 0),
    before_amount NUMERIC(11,2) NOT NULL,
    after_amount NUMERIC(11,2) NOT NULL,
    history_id BIGINT NOT NULL,
    PRIMARY KEY(user_id,cooking_id,food_id),
    FOREIGN KEY(user_id,cooking_id) REFERENCES cooking_record(user_id,cooking_id) ON DELETE RESTRICT
);
CREATE INDEX ix_cooking_ingredient_item ON cooking_ingredient(user_id,food_id);
CREATE TABLE cooking_correction_receipt (
    user_id BIGINT NOT NULL,
    request_id UUID NOT NULL,
    cooking_id UUID NOT NULL,
    request_payload TEXT NOT NULL,
    history_id BIGINT,
    PRIMARY KEY(user_id,request_id),
    FOREIGN KEY(user_id,cooking_id) REFERENCES cooking_record(user_id,cooking_id) ON DELETE RESTRICT
);
ALTER TABLE food_history ADD COLUMN cooking_id UUID,
    ADD CONSTRAINT fk_history_cooking FOREIGN KEY(user_id,cooking_id) REFERENCES cooking_record(user_id,cooking_id) ON DELETE RESTRICT;
CREATE INDEX ix_food_history_cooking ON food_history(user_id,cooking_id);
ALTER TABLE food_history DROP CONSTRAINT ck_food_history_action;
ALTER TABLE food_history ADD CONSTRAINT ck_food_history_action CHECK (
    action_type IN ('CREATE','UPDATE','CONSUME','DISCARD','CANCEL','FREEZE','MOVE','SPLIT_OUT','SPLIT_IN','MERGE','COOK_OUT','COOK_IN'));
ALTER TABLE food_history DROP CONSTRAINT ck_food_history_transition;
ALTER TABLE food_history ADD CONSTRAINT ck_food_history_transition CHECK (
    (action_type IN ('CREATE','SPLIT_IN','COOK_IN') AND previous_storage_type IS NULL) OR
    (action_type='UPDATE' AND previous_storage_type IS NOT NULL) OR
    (action_type IN ('CONSUME','DISCARD','CANCEL','SPLIT_OUT','MERGE','COOK_OUT') AND previous_storage_type=new_storage_type AND previous_storage_type IS NOT NULL) OR
    (action_type='FREEZE' AND previous_storage_type IS NOT NULL AND previous_storage_type IN ('FRIDGE','ROOM') AND new_storage_type='FREEZER') OR
    (action_type='MOVE' AND previous_storage_type IS NOT NULL AND previous_storage_type<>new_storage_type AND new_storage_type IN ('FRIDGE','ROOM'))
);
ALTER TABLE food_history ADD CONSTRAINT ck_cooking_quantity CHECK (
    action_type NOT IN ('COOK_OUT','COOK_IN') OR
    (cooking_id IS NOT NULL AND operation_id IS NOT NULL AND processed_quantity_amount > 0
     AND processed_quantity_amount IS NOT NULL AND quantity_unit IS NOT NULL
     AND after_quantity_amount IS NOT NULL AND after_quantity_amount >= 0
     AND after_snapshot IS NOT NULL AND snapshot_version=1 AND snapshot_version IS NOT NULL
     AND after_item_version IS NOT NULL AND after_stock_revision IS NOT NULL
     AND ((action_type='COOK_IN' AND after_quantity_amount=processed_quantity_amount)
       OR (action_type='COOK_OUT' AND before_quantity_amount IS NOT NULL AND before_snapshot IS NOT NULL
           AND before_item_version IS NOT NULL AND after_item_version=before_item_version+1
           AND before_quantity_amount-after_quantity_amount=processed_quantity_amount)))
);
CREATE UNIQUE INDEX uq_cooking_item_event ON food_history(user_id,cooking_id,food_id,action_type)
    WHERE action_type IN ('COOK_IN','COOK_OUT');
