-- Per-item notification preference; dates and storage history remain unchanged.
ALTER TABLE food_item ADD COLUMN warning_paused_until DATE;
ALTER TABLE food_item ADD CONSTRAINT ck_warning_paused_until CHECK
    (warning_paused_until IS NULL OR warning_paused_until BETWEEN DATE '0001-01-01' AND DATE '9999-12-31');
-- Covers all write paths, including bulk/whole-food storage moves.
CREATE FUNCTION reset_food_warning_pause() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF ROW(NEW.expired_at,NEW.frozen_at,NEW.storage_type) IS DISTINCT FROM
       ROW(OLD.expired_at,OLD.frozen_at,OLD.storage_type) THEN
        NEW.warning_paused_until := NULL;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER food_warning_pause_reset BEFORE UPDATE ON food_item
    FOR EACH ROW EXECUTE FUNCTION reset_food_warning_pause();
