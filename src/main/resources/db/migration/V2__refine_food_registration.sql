ALTER TABLE food_item ADD COLUMN capacity_text VARCHAR(50);
COMMENT ON COLUMN food_item.capacity_text IS 'Optional display-only amount, e.g. 2 servings or 300g; not used for calculations.';

ALTER TABLE food_item DROP CONSTRAINT ck_food_item_source;

-- Merge the previous delivery source without deleting existing inventory.
UPDATE food_item
   SET source_type = 'DELIVERY_LEFTOVER',
       freeze_type = CASE WHEN storage_type = 'FREEZER' THEN 'HOME_FROZEN' ELSE freeze_type END
 WHERE source_type = 'DELIVERY';

ALTER TABLE food_item ADD CONSTRAINT ck_food_item_source CHECK (
    source_type IN ('PURCHASE', 'DELIVERY_LEFTOVER', 'COOKED', 'PARENTS', 'ETC')
);

-- Quantity is required for new registrations by application validation.
-- Existing unknown quantities remain NULL rather than inventing inventory data.
