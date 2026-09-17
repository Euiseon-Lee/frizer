-- Multi-item moves share one request id and write one receipt row per moved item.
ALTER TABLE food_item_move_receipt DROP CONSTRAINT food_item_move_receipt_pkey;
ALTER TABLE food_item_move_receipt ADD PRIMARY KEY(user_id,request_id,food_id);
