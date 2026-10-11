-- Category history now belongs to the purchase action, and is deleted with it.
-- Do not guess item ownership for V26 group audit rows or rewrite old snapshots.
-- The unused V26 table is retained to preserve previously recorded data.
ALTER TABLE food_history
    ADD COLUMN category_before JSONB CHECK (category_before IS NULL OR jsonb_typeof(category_before) = 'object'),
    ADD COLUMN category_after JSONB CHECK (category_after IS NULL OR jsonb_typeof(category_after) = 'object');
