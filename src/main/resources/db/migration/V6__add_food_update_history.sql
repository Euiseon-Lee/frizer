ALTER TABLE food_history ADD COLUMN changes_text TEXT;
ALTER TABLE food_history DROP CONSTRAINT ck_food_history_action;
ALTER TABLE food_history ADD CONSTRAINT ck_food_history_action CHECK (
    action_type IN ('CREATE','UPDATE','CONSUME','FREEZE','MOVE','DISCARD')
);
ALTER TABLE food_history DROP CONSTRAINT ck_food_history_transition;
ALTER TABLE food_history ADD CONSTRAINT ck_food_history_transition CHECK (
    (action_type='CREATE' AND previous_storage_type IS NULL)
    OR (action_type='UPDATE' AND previous_storage_type IS NOT NULL)
    OR (action_type IN ('CONSUME','DISCARD') AND previous_storage_type IS NOT NULL AND previous_storage_type=new_storage_type)
    OR (action_type='FREEZE' AND previous_storage_type IS NOT NULL AND previous_storage_type IN ('FRIDGE','ROOM') AND new_storage_type='FREEZER')
    OR (action_type='MOVE' AND previous_storage_type IS NOT NULL AND previous_storage_type<>new_storage_type AND new_storage_type IN ('FRIDGE','ROOM'))
);
COMMENT ON COLUMN food_history.changes_text IS 'Changed field labels and complete before/after values for UPDATE events.';