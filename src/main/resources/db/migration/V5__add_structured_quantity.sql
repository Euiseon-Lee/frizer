-- Preserve legacy quantities and history verbatim; do not guess numeric amounts or units.
ALTER TABLE food_item ADD COLUMN quantity_amount NUMERIC(12,3);
ALTER TABLE food_item ADD COLUMN quantity_unit VARCHAR(10);
ALTER TABLE food_item ADD CONSTRAINT ck_food_item_structured_quantity CHECK (
    (quantity_amount IS NULL AND quantity_unit IS NULL)
    OR (quantity_amount IS NOT NULL AND quantity_unit IS NOT NULL
        AND quantity_amount > 0 AND quantity_amount <= 999999999.999
        AND length(btrim(quantity_unit)) BETWEEN 1 AND 10)
);
COMMENT ON COLUMN food_item.quantity_amount IS 'Numeric quantity for new registrations; NULL for legacy free-text inventory.';
COMMENT ON COLUMN food_item.quantity_unit IS 'User-entered unit; no automatic unit conversion.';