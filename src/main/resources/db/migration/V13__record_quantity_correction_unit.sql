-- Quantity corrections keep their own before-unit; do not infer historical units.
ALTER TABLE food_history ADD COLUMN before_quantity_unit VARCHAR(10);
