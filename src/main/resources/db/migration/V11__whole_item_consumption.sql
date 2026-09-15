-- Existing terminal test data needs an explicit transition decision; never infer its history.
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM food_item WHERE status <> 'ACTIVE') THEN
        RAISE EXCEPTION 'V11 requires review of legacy terminal items before migration; no data has been deleted';
    END IF;
END $$;
ALTER TABLE food_item DROP CONSTRAINT ck_food_item_status;
ALTER TABLE food_item ADD CONSTRAINT ck_food_item_status CHECK (status IN ('ACTIVE','DEPLETED'));
ALTER TABLE food_item DROP CONSTRAINT ck_food_item_structured_quantity;
ALTER TABLE food_item ADD CONSTRAINT ck_food_item_structured_quantity CHECK (
    (quantity_amount IS NULL AND quantity_unit IS NULL AND status='ACTIVE') OR
    (quantity_amount IS NOT NULL AND quantity_unit IS NOT NULL AND length(btrim(quantity_unit)) BETWEEN 1 AND 10
     AND ((status='ACTIVE' AND quantity_amount>0) OR (status='DEPLETED' AND quantity_amount=0)))
);
ALTER TABLE food_item ADD COLUMN version_no BIGINT NOT NULL DEFAULT 0;
ALTER TABLE food_item ADD COLUMN stock_revision BIGINT NOT NULL DEFAULT 0;
-- Covers every existing write path, including whole-food moves. Benign edits do not block cancellation.
CREATE FUNCTION track_food_item_revision() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    NEW.version_no := OLD.version_no + 1;
    NEW.stock_revision := OLD.stock_revision + CASE WHEN
      ROW(NEW.master_id,NEW.quantity_amount,NEW.quantity_unit,NEW.quantity_text,NEW.status,
          NEW.storage_type,NEW.freeze_type,NEW.opened_at,NEW.frozen_at)
      IS DISTINCT FROM
      ROW(OLD.master_id,OLD.quantity_amount,OLD.quantity_unit,OLD.quantity_text,OLD.status,
          OLD.storage_type,OLD.freeze_type,OLD.opened_at,OLD.frozen_at) THEN 1 ELSE 0 END;
    NEW.updated_at := GREATEST(NEW.updated_at, OLD.updated_at + interval '1 microsecond');
    RETURN NEW;
END $$;
CREATE TRIGGER food_item_revision BEFORE UPDATE ON food_item FOR EACH ROW EXECUTE FUNCTION track_food_item_revision();

ALTER TABLE food_history ADD COLUMN operation_id UUID;
ALTER TABLE food_history ADD COLUMN processed_quantity_amount NUMERIC(12,3);
ALTER TABLE food_history ADD COLUMN quantity_unit VARCHAR(10);
ALTER TABLE food_history ADD COLUMN before_quantity_amount NUMERIC(12,3);
ALTER TABLE food_history ADD COLUMN after_quantity_amount NUMERIC(12,3);
ALTER TABLE food_history ADD COLUMN before_snapshot JSONB;
ALTER TABLE food_history ADD COLUMN after_snapshot JSONB;
ALTER TABLE food_history ADD COLUMN snapshot_version INTEGER;
ALTER TABLE food_history ADD COLUMN before_item_version BIGINT;
ALTER TABLE food_history ADD COLUMN after_item_version BIGINT;
ALTER TABLE food_history ADD COLUMN after_stock_revision BIGINT;
ALTER TABLE food_history ADD COLUMN reversal_of_history_id BIGINT UNIQUE REFERENCES food_history(history_id) ON DELETE RESTRICT;
ALTER TABLE food_history DROP CONSTRAINT ck_food_history_action;
ALTER TABLE food_history ADD CONSTRAINT ck_food_history_action CHECK (action_type IN ('CREATE','UPDATE','CONSUME','DISCARD','CANCEL','FREEZE','MOVE'));
ALTER TABLE food_history DROP CONSTRAINT ck_food_history_transition;
ALTER TABLE food_history ADD CONSTRAINT ck_food_history_transition CHECK (
    (action_type='CREATE' AND previous_storage_type IS NULL) OR
    (action_type='UPDATE' AND previous_storage_type IS NOT NULL) OR
    (action_type IN ('CONSUME','DISCARD','CANCEL') AND previous_storage_type=new_storage_type AND previous_storage_type IS NOT NULL) OR
    (action_type='FREEZE' AND previous_storage_type IS NOT NULL AND previous_storage_type IN ('FRIDGE','ROOM') AND new_storage_type='FREEZER') OR
    (action_type='MOVE' AND previous_storage_type IS NOT NULL AND previous_storage_type<>new_storage_type AND new_storage_type IN ('FRIDGE','ROOM'))
);
ALTER TABLE food_history ADD CONSTRAINT ck_food_history_quantity_operation CHECK (
    operation_id IS NULL OR action_type NOT IN ('CONSUME','DISCARD','CANCEL') OR
    (processed_quantity_amount IS NOT NULL AND processed_quantity_amount>0 AND quantity_unit IS NOT NULL
     AND before_snapshot IS NOT NULL AND after_snapshot IS NOT NULL AND snapshot_version IS NOT NULL AND snapshot_version=1
     AND before_item_version IS NOT NULL AND after_item_version IS NOT NULL AND after_item_version=before_item_version+1 AND after_stock_revision IS NOT NULL
     AND before_quantity_amount IS NOT NULL AND after_quantity_amount IS NOT NULL
     AND ((action_type IN ('CONSUME','DISCARD') AND reversal_of_history_id IS NULL
           AND before_quantity_amount=processed_quantity_amount AND after_quantity_amount=0)
       OR (action_type='CANCEL' AND reversal_of_history_id IS NOT NULL
           AND before_quantity_amount=0 AND after_quantity_amount=processed_quantity_amount)))
);
CREATE TABLE food_quantity_receipt (
    request_id UUID PRIMARY KEY,
    request_payload TEXT NOT NULL,
    food_id BIGINT NOT NULL,
    history_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_food_history_quantity ON food_history(food_id,history_id DESC) WHERE action_type IN ('CONSUME','DISCARD');

-- Only new registrations: never backfill purchase amounts from today's remaining quantity.
CREATE FUNCTION snapshot_food_registration_quantity() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NEW.action_type='CREATE' THEN
        SELECT i.quantity_amount,i.quantity_unit,i.quantity_amount,
               to_jsonb(i)||jsonb_build_object('food_name',m.food_name,'category',m.category),i.version_no
        INTO NEW.processed_quantity_amount,NEW.quantity_unit,NEW.after_quantity_amount,NEW.after_snapshot,NEW.after_item_version
        FROM food_item i JOIN food_master m USING(master_id) WHERE i.food_id=NEW.food_id;
        NEW.operation_id := gen_random_uuid();
        NEW.snapshot_version := 1;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER food_registration_quantity BEFORE INSERT ON food_history FOR EACH ROW EXECUTE FUNCTION snapshot_food_registration_quantity();
