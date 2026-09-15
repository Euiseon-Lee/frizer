-- Preserve all existing full-quantity operations; allow positive partial deductions and reversals.
ALTER TABLE food_history DROP CONSTRAINT ck_food_history_quantity_operation;
ALTER TABLE food_history ADD CONSTRAINT ck_food_history_quantity_operation CHECK (
    operation_id IS NULL OR action_type NOT IN ('CONSUME','DISCARD','CANCEL') OR
    (processed_quantity_amount IS NOT NULL AND processed_quantity_amount>0 AND quantity_unit IS NOT NULL
     AND before_snapshot IS NOT NULL AND after_snapshot IS NOT NULL AND snapshot_version IS NOT NULL AND snapshot_version=1
     AND before_item_version IS NOT NULL AND after_item_version IS NOT NULL AND after_item_version=before_item_version+1 AND after_stock_revision IS NOT NULL
     AND before_quantity_amount IS NOT NULL AND after_quantity_amount IS NOT NULL
     AND before_quantity_amount>=0 AND after_quantity_amount>=0
     AND ((action_type IN ('CONSUME','DISCARD') AND reversal_of_history_id IS NULL
           AND before_quantity_amount-after_quantity_amount=processed_quantity_amount)
       OR (action_type='CANCEL' AND reversal_of_history_id IS NOT NULL
           AND after_quantity_amount-before_quantity_amount=processed_quantity_amount)))
);
