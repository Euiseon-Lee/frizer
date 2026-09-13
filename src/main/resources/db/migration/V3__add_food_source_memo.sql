ALTER TABLE food_item ADD COLUMN source_memo VARCHAR(200);
COMMENT ON COLUMN food_item.source_memo IS 'Optional note for an explicitly selected ETC source; separate from general food memo.';
ALTER TABLE food_item ADD CONSTRAINT ck_food_item_source_memo CHECK (
    source_memo IS NULL OR source_type = 'ETC'
);
