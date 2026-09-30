ALTER TABLE food_item ADD COLUMN opening_status VARCHAR(16);
ALTER TABLE food_item ADD COLUMN opening_confirmed_at DATE;
-- Preserve existing timestamps and revision counters during classification.
ALTER TABLE food_item DISABLE TRIGGER food_item_revision;
UPDATE food_item SET opening_status=CASE WHEN opened_at IS NULL THEN 'UNKNOWN' ELSE 'OPENED' END;
ALTER TABLE food_item ENABLE TRIGGER food_item_revision;
ALTER TABLE food_item ALTER COLUMN opening_status SET NOT NULL;
ALTER TABLE food_item ADD CONSTRAINT ck_opening_status CHECK (
 (opening_status IN ('UNKNOWN','UNOPENED') AND opened_at IS NULL AND opening_confirmed_at IS NULL) OR
 (opening_status='OPENED' AND (opened_at IS NOT NULL OR opening_confirmed_at IS NOT NULL))
);
-- Compatibility for legacy insert paths; missing data never implies unopened.
CREATE FUNCTION initialize_opening_status() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NEW.opening_status IS NULL THEN
  NEW.opening_status := CASE WHEN NEW.opened_at IS NULL THEN 'UNKNOWN' ELSE 'OPENED' END;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER food_opening_insert BEFORE INSERT ON food_item FOR EACH ROW EXECUTE FUNCTION initialize_opening_status();

CREATE OR REPLACE FUNCTION track_food_item_revision() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    NEW.version_no := OLD.version_no + 1;
    NEW.stock_revision := OLD.stock_revision + CASE WHEN
      ROW(NEW.master_id,NEW.quantity_amount,NEW.quantity_unit,NEW.quantity_text,NEW.status,
          NEW.storage_type,NEW.freeze_type,NEW.opened_at,NEW.frozen_at,NEW.opening_status,NEW.opening_confirmed_at)
      IS DISTINCT FROM
      ROW(OLD.master_id,OLD.quantity_amount,OLD.quantity_unit,OLD.quantity_text,OLD.status,
          OLD.storage_type,OLD.freeze_type,OLD.opened_at,OLD.frozen_at,OLD.opening_status,OLD.opening_confirmed_at) THEN 1 ELSE 0 END;
    NEW.updated_at := GREATEST(NEW.updated_at, OLD.updated_at + interval '1 microsecond');
    RETURN NEW;
END $$;
