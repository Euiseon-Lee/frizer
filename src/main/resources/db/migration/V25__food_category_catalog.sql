-- Additive foundation only. Existing free-text category and history remain untouched.
-- Catalog codes are stable identifiers; labels/examples may evolve through new migrations.
CREATE TABLE food_category_major (
    code VARCHAR(40) PRIMARY KEY CHECK (code ~ '^[a-z][a-z0-9_]*$'),
    label VARCHAR(50) NOT NULL CHECK (btrim(label) <> ''),
    example VARCHAR(500) NOT NULL,
    display_order INTEGER NOT NULL UNIQUE CHECK (display_order > 0),
    requires_minor BOOLEAN NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE food_category_minor (
    code VARCHAR(64) PRIMARY KEY CHECK (code ~ '^[a-z][a-z0-9_]*$'),
    major_code VARCHAR(40) NOT NULL REFERENCES food_category_major(code),
    label VARCHAR(50) NOT NULL CHECK (btrim(label) <> ''),
    example VARCHAR(500) NOT NULL,
    display_order INTEGER NOT NULL CHECK (display_order > 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (major_code, code),
    UNIQUE (major_code, display_order)
);

INSERT INTO food_category_major(code,label,example,display_order,requires_minor) VALUES
('grain', '곡물류', '쌀·잡곡·오트밀·시리얼 / 밀가루·전분·부침가루 / 라면·우동면·파스타면 / 떡국떡·인절미 / 식빵·베이글·크림빵', 1, true),
('meat', '육류', '부위는 음식명으로 구별. 기타 고기는 양고기 등, 부속고기·뼈는 곱창·간·닭발·사골·등뼈, 육가공품은 햄·소시지·베이컨·육포', 2, true),
('seafood', '수산물', '고등어·연어 / 새우·게 / 오징어·문어 / 바지락·굴·전복 / 김·미역 / 마른 멸치·황태 / 어묵·맛살·참치캔 / 생선 알·해삼', 3, true),
('soy', '콩·두부', '검은콩·병아리콩 / 부침두부·찌개두부 / 순두부·연두부 / 유부·건두부·비지·낫토·두부면', 4, true),
('egg', '알류', '생달걀·깐 달걀 / 생메추리알·깐 메추리알 / 오리알', 5, true),
('vegetable', '채소', '상추·배추·브로콜리 / 무·당근·감자·고구마 / 오이·호박·토마토·고추·옥수수 / 양파·대파·마늘·생강·깻잎·고수 / 표고·팽이 / 콩나물·숙주·새싹·냉동 혼합 채소', 6, true),
('fruit', '과일', '사과·바나나·귤·딸기 / 컷과일 모음·혼합 베리 / 건포도·곶감 / 황도 / 과일 퓌레·페이스트', 7, true),
('nut_seed', '견과·씨앗', '아몬드·호두·땅콩·밤 / 호박씨·해바라기씨 / 하루견과', 8, true),
('dairy', '유제품·대체품', '흰 우유·가공 우유 / 모차렐라·크림치즈 / 일반·그릭 요거트 / 버터·생크림·연유 / 두유·귀리 음료·식물성 치즈·마가린 / 분유', 9, true),
('seasoning', '양념·소스', '설탕·꿀 / 간장·된장 / 식용유·참기름 / 식초·조리용 술 / 후추·참깨·건조 허브 / 케첩·굴소스 / 육수 코인·스톡 / 카레가루 / 딸기잼·땅콩버터 / 이스트·젤라틴 / 기타 조리 보조 재료', 10, true),
('kimchi', '김치', '배추김치·깍두기·파김치·열무김치', 11, false),
('pickle', '절임', '장아찌·피클·단무지', 12, false),
('salted_seafood', '젓갈', '명란젓·오징어젓·낙지젓·새우젓', 13, false),
('dish', '요리류', '밥·볶음밥·죽 / 조리된 우동·파스타 / 미역국·갈비탕 / 직접 만든·레토르트 카레 / 제육·치킨·돈가스 / 생선구이·회 / 달걀말이·미역무침·멸치볶음 / 떡볶이·순대 / 냉동 만두 / 피자·햄버거 / 샌드위치 / 토핑·드레싱 포함 샐러드 / 수프·여러 요리 모음', 14, true),
('snack', '간식·디저트', '스낵·쿠키 / 초콜릿·젤리 / 케이크·파이·도넛·푸딩 / 아이스크림·셔벗 / 약과·단백질바', 15, true),
('beverage', '음료', '생수·탄산수·주스·커피 음료·원두·캡슐', 16, false),
('tea', '차', '찻잎·티백·녹차·홍차·곡물차·차 음료', 17, false),
('alcohol', '주류', '맥주·소주·와인·위스키·막걸리', 18, false),
('other', '기타 식품', '다른 대분류에 해당하지 않는 음식', 19, false);

INSERT INTO food_category_minor(code,major_code,label,example,display_order) VALUES
('grain_cereal', 'grain', '곡류', '쌀·잡곡·오트밀·시리얼', 1),
('grain_flour', 'grain', '가루', '밀가루·전분·부침가루', 2),
('grain_noodle', 'grain', '면', '라면·우동면·파스타면', 3),
('grain_rice_cake', 'grain', '떡', '떡국떡·인절미', 4),
('grain_bread', 'grain', '빵', '식빵·베이글·크림빵', 5),
('meat_beef', 'meat', '소고기', '부위는 음식명으로 구별해줘.', 1),
('meat_pork', 'meat', '돼지고기', '부위는 음식명으로 구별해줘.', 2),
('meat_chicken', 'meat', '닭고기', '부위는 음식명으로 구별해줘.', 3),
('meat_duck', 'meat', '오리고기', '부위는 음식명으로 구별해줘.', 4),
('meat_other', 'meat', '기타 고기', '양고기 등', 5),
('meat_offal', 'meat', '부속고기·뼈', '곱창·간·닭발·사골·등뼈', 6),
('meat_processed', 'meat', '육가공품', '햄·소시지·베이컨·육포', 7),
('seafood_fish', 'seafood', '생선', '고등어·연어', 1),
('seafood_crustacean', 'seafood', '갑각류', '새우·게', 2),
('seafood_mollusk', 'seafood', '연체류', '오징어·문어', 3),
('seafood_shellfish', 'seafood', '조개류', '바지락·굴·전복', 4),
('seafood_seaweed', 'seafood', '해조류', '김·미역', 5),
('seafood_dried', 'seafood', '건어물', '마른 멸치·황태', 6),
('seafood_processed', 'seafood', '수산가공품', '어묵·맛살·참치캔', 7),
('seafood_other', 'seafood', '기타 수산물', '생선 알·해삼', 8),
('soy_bean', 'soy', '콩', '검은콩·병아리콩', 1),
('soy_tofu', 'soy', '두부', '부침두부·찌개두부', 2),
('soy_soft_tofu', 'soy', '순두부·연두부', '순두부·연두부', 3),
('soy_other', 'soy', '기타 콩가공품', '유부·건두부·비지·낫토·두부면', 4),
('egg_chicken', 'egg', '달걀', '생달걀·깐 달걀', 1),
('egg_quail', 'egg', '메추리알', '생메추리알·깐 메추리알', 2),
('egg_other', 'egg', '기타 알', '오리알', 3),
('vegetable_leaf_stem', 'vegetable', '잎·줄기채소', '상추·배추·브로콜리', 1),
('vegetable_root', 'vegetable', '뿌리·덩이채소', '무·당근·감자·고구마', 2),
('vegetable_fruit', 'vegetable', '열매채소', '오이·호박·토마토·고추·옥수수', 3),
('vegetable_aromatic', 'vegetable', '파·마늘·허브', '양파·대파·마늘·생강·깻잎·고수', 4),
('vegetable_mushroom', 'vegetable', '버섯', '표고·팽이', 5),
('vegetable_mixed_other', 'vegetable', '기타·혼합 채소', '콩나물·숙주·새싹·냉동 혼합 채소', 6),
('fruit_fresh', 'fruit', '일반 과일', '사과·바나나·귤·딸기', 1),
('fruit_mixed', 'fruit', '혼합 과일', '컷과일 모음·혼합 베리', 2),
('fruit_dried', 'fruit', '말린 과일', '건포도·곶감', 3),
('fruit_canned', 'fruit', '과일 통조림', '황도', 4),
('fruit_processed_other', 'fruit', '기타 과일 가공품', '과일 퓌레·페이스트', 5),
('nut_seed_nut', 'nut_seed', '견과', '아몬드·호두·땅콩·밤', 1),
('nut_seed_seed', 'nut_seed', '씨앗', '호박씨·해바라기씨', 2),
('nut_seed_mixed', 'nut_seed', '혼합 견과·씨앗', '하루견과', 3),
('dairy_milk', 'dairy', '우유', '흰 우유·가공 우유', 1),
('dairy_cheese', 'dairy', '치즈', '모차렐라·크림치즈', 2),
('dairy_yogurt', 'dairy', '요거트', '일반·그릭 요거트', 3),
('dairy_butter_cream', 'dairy', '버터·크림', '버터·생크림·연유', 4),
('dairy_plant_based', 'dairy', '식물성 대체품', '두유·귀리 음료·식물성 치즈·마가린', 5),
('dairy_other', 'dairy', '기타 유제품', '분유', 6),
('seasoning_salt_sweetener', 'seasoning', '소금·감미료', '설탕·꿀', 1),
('seasoning_paste_sauce', 'seasoning', '장류', '간장·된장', 2),
('seasoning_oil', 'seasoning', '기름', '식용유·참기름', 3),
('seasoning_vinegar_cooking_wine', 'seasoning', '식초·맛술', '식초·조리용 술', 4),
('seasoning_spice', 'seasoning', '향신료', '후추·참깨·건조 허브', 5),
('seasoning_sauce_dressing', 'seasoning', '소스·드레싱', '케첩·굴소스', 6),
('seasoning_stock', 'seasoning', '육수·조미료', '육수 코인·스톡', 7),
('seasoning_curry_jajang', 'seasoning', '카레·짜장 재료', '카레가루', 8),
('seasoning_spread', 'seasoning', '잼·스프레드', '딸기잼·땅콩버터', 9),
('seasoning_baking', 'seasoning', '제과·제빵 재료', '이스트·젤라틴', 10),
('seasoning_other', 'seasoning', '기타 양념', '기타 조리 보조 재료', 11),
('dish_rice', 'dish', '밥류', '밥·볶음밥·죽', 1),
('dish_noodle', 'dish', '면류', '조리된 우동·파스타', 2),
('dish_soup', 'dish', '국·탕·찌개·전골', '미역국·갈비탕', 3),
('dish_curry', 'dish', '카레', '직접 만든·레토르트 카레', 4),
('dish_meat', 'dish', '고기 요리', '제육·치킨·돈가스', 5),
('dish_seafood', 'dish', '생선·해산물 요리', '생선구이·회', 6),
('dish_side_dish', 'dish', '반찬류', '달걀말이·미역무침·멸치볶음', 7),
('dish_street_food', 'dish', '분식', '떡볶이·순대', 8),
('dish_dumpling', 'dish', '만두', '냉동 만두', 9),
('dish_pizza_burger', 'dish', '피자·버거', '피자·햄버거', 10),
('dish_sandwich', 'dish', '샌드위치', '샌드위치', 11),
('dish_salad', 'dish', '샐러드', '토핑·드레싱 포함 샐러드', 12),
('dish_other', 'dish', '기타 요리', '수프·여러 요리 모음', 13),
('snack_cookie', 'snack', '과자', '스낵·쿠키', 1),
('snack_chocolate_candy', 'snack', '초콜릿·사탕', '초콜릿·젤리', 2),
('snack_dessert', 'snack', '케이크·디저트', '케이크·파이·도넛·푸딩', 3),
('snack_ice_cream', 'snack', '아이스크림·빙과', '아이스크림·셔벗', 4),
('snack_other', 'snack', '기타 간식', '약과·단백질바', 5);

ALTER TABLE food_master
    ADD COLUMN category_major_code VARCHAR(40),
    ADD COLUMN category_minor_code VARCHAR(64),
    ADD CONSTRAINT fk_food_master_category_major FOREIGN KEY (category_major_code)
        REFERENCES food_category_major(code),
    ADD CONSTRAINT fk_food_master_category_minor FOREIGN KEY (category_major_code,category_minor_code)
        REFERENCES food_category_minor(major_code,code),
    ADD CONSTRAINT ck_food_master_category_parent CHECK (
        category_major_code IS NOT NULL OR category_minor_code IS NULL
    );

-- NULL/NULL is the preserved legacy state. A new structured value must be complete.
-- Active flags are a write-policy concern in the application, not a reason to break
-- historical references when an option is retired.
CREATE FUNCTION validate_food_master_category() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE needs_minor BOOLEAN;
BEGIN
    IF NEW.category_major_code IS NOT NULL THEN
        SELECT requires_minor INTO needs_minor FROM food_category_major
          WHERE code = NEW.category_major_code;
        IF FOUND AND needs_minor <> (NEW.category_minor_code IS NOT NULL) THEN
            RAISE EXCEPTION 'Incomplete food category selection'
                USING ERRCODE = '23514', CONSTRAINT = 'ck_food_master_category_complete';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER food_master_category_complete
    BEFORE INSERT OR UPDATE OF category_major_code,category_minor_code ON food_master
    FOR EACH ROW EXECUTE FUNCTION validate_food_master_category();
