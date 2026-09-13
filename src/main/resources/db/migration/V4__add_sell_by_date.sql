ALTER TABLE food_item ADD COLUMN sell_by_at DATE;
COMMENT ON COLUMN food_item.sell_by_at IS 'Optional sell-by date printed on the package; never substituted for the use-by date.';
COMMENT ON COLUMN food_item.expired_at IS 'Optional use-by date printed on the package. Existing values are preserved.';
