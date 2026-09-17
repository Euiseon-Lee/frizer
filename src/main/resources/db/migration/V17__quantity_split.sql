-- Quantity split: deduct the source item and create one sibling item in the same master.
-- SPLIT_OUT records the deduction on the source; SPLIT_IN records the new item's first quantity
-- (never CREATE: splits are not new stock). The receipt links source and child permanently.
CREATE TABLE food_split_receipt (
    user_id BIGINT NOT NULL REFERENCES app_user(user_id) ON DELETE RESTRICT,
    request_id UUID NOT NULL,
    request_payload TEXT NOT NULL,
    source_id BIGINT NOT NULL,
    child_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(user_id, request_id)
);
ALTER TABLE food_history DROP CONSTRAINT ck_food_history_action;
ALTER TABLE food_history ADD CONSTRAINT ck_food_history_action CHECK (
    action_type IN ('CREATE','UPDATE','CONSUME','DISCARD','CANCEL','FREEZE','MOVE','SPLIT_OUT','SPLIT_IN'));
ALTER TABLE food_history DROP CONSTRAINT ck_food_history_transition;
ALTER TABLE food_history ADD CONSTRAINT ck_food_history_transition CHECK (
    (action_type IN ('CREATE','SPLIT_IN') AND previous_storage_type IS NULL) OR
    (action_type='UPDATE' AND previous_storage_type IS NOT NULL) OR
    (action_type IN ('CONSUME','DISCARD','CANCEL','SPLIT_OUT') AND previous_storage_type=new_storage_type AND previous_storage_type IS NOT NULL) OR
    (action_type='FREEZE' AND previous_storage_type IS NOT NULL AND previous_storage_type IN ('FRIDGE','ROOM') AND new_storage_type='FREEZER') OR
    (action_type='MOVE' AND previous_storage_type IS NOT NULL AND previous_storage_type<>new_storage_type AND new_storage_type IN ('FRIDGE','ROOM'))
);
ALTER TABLE food_history DROP CONSTRAINT ck_food_history_quantity_operation;
ALTER TABLE food_history ADD CONSTRAINT ck_food_history_quantity_operation CHECK (
    operation_id IS NULL OR action_type NOT IN ('CONSUME','DISCARD','CANCEL','SPLIT_OUT') OR
    (processed_quantity_amount IS NOT NULL AND processed_quantity_amount>0 AND quantity_unit IS NOT NULL
     AND before_snapshot IS NOT NULL AND after_snapshot IS NOT NULL AND snapshot_version IS NOT NULL AND snapshot_version=1
     AND before_item_version IS NOT NULL AND after_item_version IS NOT NULL AND after_item_version=before_item_version+1 AND after_stock_revision IS NOT NULL
     AND before_quantity_amount IS NOT NULL AND after_quantity_amount IS NOT NULL
     AND before_quantity_amount>=0 AND after_quantity_amount>=0
     AND ((action_type IN ('CONSUME','DISCARD') AND reversal_of_history_id IS NULL
           AND before_quantity_amount-after_quantity_amount=processed_quantity_amount)
       OR (action_type='SPLIT_OUT' AND reversal_of_history_id IS NULL AND after_quantity_amount>0
           AND before_quantity_amount-after_quantity_amount=processed_quantity_amount)
       OR (action_type='CANCEL' AND reversal_of_history_id IS NOT NULL
           AND after_quantity_amount-before_quantity_amount=processed_quantity_amount)))
);
-- A split child's first quantity is snapshotted exactly like a registration.
CREATE OR REPLACE FUNCTION snapshot_food_registration_quantity() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NEW.action_type IN ('CREATE','SPLIT_IN') THEN
        SELECT i.quantity_amount,i.quantity_unit,i.quantity_amount,
               to_jsonb(i)||jsonb_build_object('food_name',m.food_name,'category',m.category),i.version_no
        INTO NEW.processed_quantity_amount,NEW.quantity_unit,NEW.after_quantity_amount,NEW.after_snapshot,NEW.after_item_version
        FROM food_item i JOIN food_master m USING(master_id) WHERE i.food_id=NEW.food_id;
        NEW.operation_id := gen_random_uuid();
        NEW.snapshot_version := 1;
    END IF;
    RETURN NEW;
END $$;
