-- Extend allowed states without reclassifying existing inventory.
ALTER TABLE food_item DROP CONSTRAINT ck_opening_status;
ALTER TABLE food_item ADD CONSTRAINT ck_opening_status CHECK (
 (opening_status IN ('UNKNOWN','UNOPENED','NOT_APPLICABLE') AND opened_at IS NULL AND opening_confirmed_at IS NULL) OR
 (opening_status='OPENED' AND (opened_at IS NOT NULL OR opening_confirmed_at IS NOT NULL))
);
