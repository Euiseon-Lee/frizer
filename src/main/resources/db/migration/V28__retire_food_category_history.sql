-- Never infer a purchase item from a group audit row. Nonempty legacy tables
-- require a verified export and explicit disposal decision before retrying.
-- Hold the lock through DROP so an older application cannot race the check.
LOCK TABLE food_category_history IN ACCESS EXCLUSIVE MODE;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM food_category_history) THEN
        RAISE EXCEPTION 'food_category_history contains legacy records; V28 refuses to discard them'
            USING HINT = 'Follow docs/OPERATIONS.md category history retirement: stop writers, export and verify, obtain disposal approval, empty the legacy table, then retry migration.';
    END IF;
END $$;
DROP TABLE food_category_history;
