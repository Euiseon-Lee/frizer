-- Unspecified source is distinct from an explicitly selected ETC.
-- Existing ETC values cannot be reliably classified and are preserved.
ALTER TABLE food_item ALTER COLUMN source_type DROP NOT NULL;
ALTER TABLE food_item ALTER COLUMN source_type DROP DEFAULT;
ALTER TABLE food_item DROP CONSTRAINT ck_food_item_source_memo;
ALTER TABLE food_item ADD CONSTRAINT ck_food_item_source_memo CHECK (
    source_memo IS NULL OR (source_type IS NOT NULL AND source_type = 'ETC')
);
