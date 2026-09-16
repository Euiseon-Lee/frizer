-- All pre-V15 data belongs to the existing personal account. It stays locked until
-- the application claims it using the existing environment credentials exactly once.
CREATE TABLE app_user (
    user_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    login_id VARCHAR(100) UNIQUE CHECK (login_id IS NULL OR (btrim(login_id) = login_id AND login_id <> '')),
    password_hash VARCHAR(100),
    role VARCHAR(10) NOT NULL CHECK (role IN ('ADMIN','USER')),
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    session_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (NOT enabled OR (login_id IS NOT NULL AND password_hash IS NOT NULL))
);
-- Revocation survives disable/re-enable even if the session made no request while disabled.
CREATE FUNCTION revoke_account_sessions() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF ROW(NEW.password_hash,NEW.enabled,NEW.role) IS DISTINCT FROM ROW(OLD.password_hash,OLD.enabled,OLD.role) THEN
        NEW.session_version := OLD.session_version + 1;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER app_user_session_revision BEFORE UPDATE ON app_user
    FOR EACH ROW EXECUTE FUNCTION revoke_account_sessions();
INSERT INTO app_user(role) VALUES ('USER');
ALTER TABLE food_master ADD COLUMN user_id BIGINT;
UPDATE food_master SET user_id=1;
ALTER TABLE food_master ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE food_master ADD CONSTRAINT food_master_user_fk FOREIGN KEY(user_id) REFERENCES app_user(user_id) ON DELETE RESTRICT;
CREATE INDEX food_master_user_idx ON food_master(user_id);
ALTER TABLE food_item ADD COLUMN user_id BIGINT;
-- Backfill ownership without pretending that stock was edited (V11 revision trigger).
ALTER TABLE food_item DISABLE TRIGGER food_item_revision;
UPDATE food_item SET user_id=1;
ALTER TABLE food_item ENABLE TRIGGER food_item_revision;
ALTER TABLE food_item ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE food_item ADD CONSTRAINT food_item_user_fk FOREIGN KEY(user_id) REFERENCES app_user(user_id) ON DELETE RESTRICT;
CREATE INDEX food_item_user_idx ON food_item(user_id);
ALTER TABLE food_history ADD COLUMN user_id BIGINT;
UPDATE food_history SET user_id=1;
ALTER TABLE food_history ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE food_history ADD CONSTRAINT food_history_user_fk FOREIGN KEY(user_id) REFERENCES app_user(user_id) ON DELETE RESTRICT;
CREATE INDEX food_history_user_idx ON food_history(user_id);
ALTER TABLE food_merge_receipt ADD COLUMN user_id BIGINT;
UPDATE food_merge_receipt SET user_id=1;
ALTER TABLE food_merge_receipt ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE food_merge_receipt ADD CONSTRAINT food_merge_receipt_user_fk FOREIGN KEY(user_id) REFERENCES app_user(user_id) ON DELETE RESTRICT;
CREATE INDEX food_merge_receipt_user_idx ON food_merge_receipt(user_id);
ALTER TABLE food_registration_receipt ADD COLUMN user_id BIGINT;
UPDATE food_registration_receipt SET user_id=1;
ALTER TABLE food_registration_receipt ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE food_registration_receipt ADD CONSTRAINT food_registration_receipt_user_fk FOREIGN KEY(user_id) REFERENCES app_user(user_id) ON DELETE RESTRICT;
CREATE INDEX food_registration_receipt_user_idx ON food_registration_receipt(user_id);
ALTER TABLE food_item_move_receipt ADD COLUMN user_id BIGINT;
UPDATE food_item_move_receipt SET user_id=1;
ALTER TABLE food_item_move_receipt ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE food_item_move_receipt ADD CONSTRAINT food_item_move_receipt_user_fk FOREIGN KEY(user_id) REFERENCES app_user(user_id) ON DELETE RESTRICT;
CREATE INDEX food_item_move_receipt_user_idx ON food_item_move_receipt(user_id);
ALTER TABLE food_quantity_receipt ADD COLUMN user_id BIGINT;
UPDATE food_quantity_receipt SET user_id=1;
ALTER TABLE food_quantity_receipt ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE food_quantity_receipt ADD CONSTRAINT food_quantity_receipt_user_fk FOREIGN KEY(user_id) REFERENCES app_user(user_id) ON DELETE RESTRICT;
CREATE INDEX food_quantity_receipt_user_idx ON food_quantity_receipt(user_id);
ALTER TABLE food_bulk_preview ADD COLUMN user_id BIGINT;
UPDATE food_bulk_preview SET user_id=1;
ALTER TABLE food_bulk_preview ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE food_bulk_preview ADD CONSTRAINT food_bulk_preview_user_fk FOREIGN KEY(user_id) REFERENCES app_user(user_id) ON DELETE RESTRICT;
CREATE INDEX food_bulk_preview_user_idx ON food_bulk_preview(user_id);
ALTER TABLE food_bulk_receipt ADD COLUMN user_id BIGINT;
UPDATE food_bulk_receipt SET user_id=1;
ALTER TABLE food_bulk_receipt ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE food_bulk_receipt ADD CONSTRAINT food_bulk_receipt_user_fk FOREIGN KEY(user_id) REFERENCES app_user(user_id) ON DELETE RESTRICT;
CREATE INDEX food_bulk_receipt_user_idx ON food_bulk_receipt(user_id);
ALTER TABLE food_master ADD UNIQUE(user_id,master_id);
ALTER TABLE food_item ADD UNIQUE(user_id,food_id);
ALTER TABLE food_history ADD UNIQUE(user_id,history_id);
ALTER TABLE food_item ADD FOREIGN KEY(user_id,master_id) REFERENCES food_master(user_id,master_id) ON DELETE RESTRICT;
ALTER TABLE food_history ADD FOREIGN KEY(user_id,food_id) REFERENCES food_item(user_id,food_id) ON DELETE RESTRICT;
ALTER TABLE food_history ADD FOREIGN KEY(user_id,reversal_of_history_id) REFERENCES food_history(user_id,history_id) ON DELETE RESTRICT;
-- Request receipts retain historical identifiers, as before V15. No new item/history FKs:
-- a claim before item locking would acquire KEY SHARE and invert the established lock order.
-- Services validate owner-scoped targets; receipt reads/writes are also owner-scoped.
-- Historical master IDs intentionally outlive merged/deleted masters; retain them without master FKs.
ALTER TABLE food_merge_receipt DROP CONSTRAINT food_merge_receipt_pkey;
ALTER TABLE food_merge_receipt ADD PRIMARY KEY(user_id,request_id);
ALTER TABLE food_registration_receipt DROP CONSTRAINT food_registration_receipt_pkey;
ALTER TABLE food_registration_receipt ADD PRIMARY KEY(user_id,request_id);
ALTER TABLE food_item_move_receipt DROP CONSTRAINT food_item_move_receipt_pkey;
ALTER TABLE food_item_move_receipt ADD PRIMARY KEY(user_id,request_id);
ALTER TABLE food_quantity_receipt DROP CONSTRAINT food_quantity_receipt_pkey;
ALTER TABLE food_quantity_receipt ADD PRIMARY KEY(user_id,request_id);
ALTER TABLE food_bulk_preview DROP CONSTRAINT food_bulk_preview_pkey;
ALTER TABLE food_bulk_preview ADD PRIMARY KEY(user_id,request_id);
ALTER TABLE food_bulk_receipt DROP CONSTRAINT food_bulk_receipt_pkey;
ALTER TABLE food_bulk_receipt ADD PRIMARY KEY(user_id,fingerprint);
