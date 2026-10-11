-- Group-level audit survives empty groups, item deletion and later group merges.
CREATE TABLE food_category_history (
    category_history_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(user_id),
    master_id BIGINT NOT NULL,
    food_name VARCHAR(100) NOT NULL,
    before_category VARCHAR(50),
    before_major_code VARCHAR(40),
    before_minor_code VARCHAR(64),
    after_category VARCHAR(50) NOT NULL,
    after_major_code VARCHAR(40) NOT NULL,
    after_minor_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX ix_food_category_history_owner_master
    ON food_category_history(user_id, master_id, category_history_id);

-- Only future registration/split snapshots gain codes; existing history is never rewritten.
CREATE OR REPLACE FUNCTION snapshot_food_registration_quantity() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NEW.action_type IN ('CREATE','SPLIT_IN') THEN
        SELECT i.quantity_amount,i.quantity_unit,i.quantity_amount,
               to_jsonb(i)||jsonb_build_object('food_name',m.food_name,'category',m.category,
                   'category_major_code',m.category_major_code,'category_minor_code',m.category_minor_code),i.version_no
        INTO NEW.processed_quantity_amount,NEW.quantity_unit,NEW.after_quantity_amount,NEW.after_snapshot,NEW.after_item_version
        FROM food_item i JOIN food_master m USING(master_id) WHERE i.food_id=NEW.food_id;
        NEW.operation_id := gen_random_uuid();
        NEW.snapshot_version := 1;
    END IF;
    RETURN NEW;
END $$;
