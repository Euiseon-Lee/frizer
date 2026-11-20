-- A consumption can transfer remaining sealed stock into one sibling item.
-- Historical IDs deliberately have no FK: deleting a mistaken item must not
-- erase another item's audit trail. Cancellation checks ownership and versions.
ALTER TABLE food_history
    ADD COLUMN separated_food_id BIGINT,
    ADD COLUMN separated_quantity_amount NUMERIC(11,2) NOT NULL DEFAULT 0,
    ADD COLUMN separated_item_version BIGINT;
ALTER TABLE food_history ADD CONSTRAINT ck_consumption_separation CHECK (
    (separated_quantity_amount=0 AND separated_food_id IS NULL AND separated_item_version IS NULL) OR
    (separated_quantity_amount>0 AND separated_food_id IS NOT NULL AND separated_food_id<>food_id
     AND separated_item_version IS NOT NULL AND action_type IN ('CONSUME','CANCEL') AND operation_id IS NOT NULL)
);
ALTER TABLE food_history DROP CONSTRAINT ck_food_history_quantity_operation;
ALTER TABLE food_history ADD CONSTRAINT ck_food_history_quantity_operation CHECK (
    operation_id IS NULL OR action_type NOT IN ('CONSUME','DISCARD','CANCEL','SPLIT_OUT') OR
    (processed_quantity_amount IS NOT NULL AND processed_quantity_amount>0 AND quantity_unit IS NOT NULL
     AND before_snapshot IS NOT NULL AND after_snapshot IS NOT NULL AND snapshot_version IS NOT NULL AND snapshot_version=1
     AND before_item_version IS NOT NULL AND after_item_version IS NOT NULL AND after_item_version=before_item_version+1 AND after_stock_revision IS NOT NULL
     AND before_quantity_amount IS NOT NULL AND after_quantity_amount IS NOT NULL
     AND before_quantity_amount>=0 AND after_quantity_amount>=0
     AND ((action_type IN ('CONSUME','DISCARD') AND reversal_of_history_id IS NULL
           AND before_quantity_amount-after_quantity_amount=processed_quantity_amount+separated_quantity_amount)
       OR (action_type='SPLIT_OUT' AND reversal_of_history_id IS NULL AND after_quantity_amount>0
           AND before_quantity_amount-after_quantity_amount=processed_quantity_amount)
       OR (action_type='CANCEL' AND reversal_of_history_id IS NOT NULL
           AND after_quantity_amount-before_quantity_amount=processed_quantity_amount+separated_quantity_amount)))
);
