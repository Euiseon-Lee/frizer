-- New item merges keep their category snapshots in food_history. Old receipts
-- remain readable and are never converted by guessing historical categories.
ALTER TABLE food_history DROP CONSTRAINT ck_food_history_action;
ALTER TABLE food_history ADD CONSTRAINT ck_food_history_action CHECK (
    action_type IN ('CREATE','UPDATE','CONSUME','DISCARD','CANCEL','FREEZE','MOVE','SPLIT_OUT','SPLIT_IN','MERGE'));
ALTER TABLE food_history DROP CONSTRAINT ck_food_history_transition;
ALTER TABLE food_history ADD CONSTRAINT ck_food_history_transition CHECK (
    (action_type IN ('CREATE','SPLIT_IN') AND previous_storage_type IS NULL) OR
    (action_type='UPDATE' AND previous_storage_type IS NOT NULL) OR
    (action_type IN ('CONSUME','DISCARD','CANCEL','SPLIT_OUT','MERGE') AND previous_storage_type=new_storage_type AND previous_storage_type IS NOT NULL) OR
    (action_type='FREEZE' AND previous_storage_type IS NOT NULL AND previous_storage_type IN ('FRIDGE','ROOM') AND new_storage_type='FREEZER') OR
    (action_type='MOVE' AND previous_storage_type IS NOT NULL AND previous_storage_type<>new_storage_type AND new_storage_type IN ('FRIDGE','ROOM'))
);
CREATE UNIQUE INDEX uq_food_merge_history ON food_history(user_id,food_id,operation_id) WHERE action_type='MERGE';
