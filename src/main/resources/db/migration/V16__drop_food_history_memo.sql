-- memo only ever held the constants '음식 등록'/'음식 수정'; action_type already carries this.
ALTER TABLE food_history DROP COLUMN memo;
