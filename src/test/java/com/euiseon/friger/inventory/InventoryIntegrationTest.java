package com.euiseon.friger.inventory;

import java.time.Clock;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import com.euiseon.friger.common.type.*;
import com.euiseon.friger.inventory.service.InventoryService;
import com.euiseon.friger.inventory.exception.InvalidFoodException;
import com.euiseon.friger.inventory.dto.FoodCreateForm;
import com.euiseon.friger.inventory.entity.FoodItem;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(InventoryIntegrationTest.FixedTime.class)
class InventoryIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.11")
            .withDatabaseName("frizer_inventory_test");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedTime {
        @Bean @Primary
        Clock testClock() {
            // UTC is still September 12; Seoul is already September 13.
            return Clock.fixed(Instant.parse("2026-09-12T16:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }

    @Autowired InventoryService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @BeforeEach
    void resetDatabase() {
        jdbc.update("DELETE FROM food_history");
        jdbc.update("DELETE FROM food_item");
    }

    @Test
    void pagesRenderWithChocoNavigation() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(view().name("home"));
        mvc.perform(get("/inventory")).andExpect(status().isOk())
                .andExpect(content().string(containsString("첫 음식 등록하기")));
        mvc.perform(get("/inventory/new")).andExpect(status().isOk())
                .andExpect(content().string(containsString("음식 등록하기")));
        mvc.perform(get("/history")).andExpect(status().isOk());
    }

    @Test
    void homeSeparatesExpiredTodayAndUnknownDatesAndFiltersStorage() throws Exception {
        mvc.perform(post("/inventory").param("foodName", "경과 음식").param("quantityAmount", "1").param("quantityUnit", "개")
                .param("storageType", "ROOM").param("expiredAt", "2026-09-12")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/inventory").param("foodName", "오늘 음식").param("quantityAmount", "2").param("quantityUnit", "팩")
                .param("storageType", "FRIDGE").param("expiredAt", "2026-09-13")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/inventory").param("foodName", "기한 미입력").param("quantityAmount", "1").param("quantityUnit", "병")
                .param("storageType", "FRIDGE").param("sellByAt", "2026-09-01")).andExpect(status().is3xxRedirection());
        var home = mvc.perform(get("/")).andExpect(status().isOk()).andReturn().getModelAndView().getModel();
        assertThat((java.util.List<FoodItem>) home.get("attentionFoods")).extracting(FoodItem::foodName).containsExactlyInAnyOrder("경과 음식", "기한 미입력");
        assertThat((java.util.List<FoodItem>) home.get("dueFoods")).extracting(FoodItem::foodName).containsExactly("오늘 음식");
        assertThat(home.get("unknownDateCount")).isEqualTo(1L);
        var filtered = mvc.perform(get("/inventory").param("storage", "ROOM")).andExpect(status().isOk())
                .andReturn().getModelAndView().getModel();
        assertThat((java.util.List<FoodItem>) filtered.get("foods")).extracting(FoodItem::foodName).containsExactly("경과 음식");
        mvc.perform(get("/history")).andExpect(status().isOk())
                .andExpect(content().string(containsString("오늘 음식")))
                .andExpect(content().string(containsString("2팩")));
    }

    @Test
    void registrationPersistsFoodAndSingleCreateHistoryAndRedirects() throws Exception {
        mvc.perform(post("/inventory").param("foodName", " 두부 ").param("storageType", "FRIDGE")
                .param("quantityAmount", "1").param("quantityUnit", "모")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/inventory")).andExpect(flash().attributeExists("successMessage"));
        FoodItem saved = service.findActive().getFirst();
        assertThat(saved.foodName()).isEqualTo("두부");
        assertThat(saved.sourceType()).isEqualTo(FoodSourceType.ETC);
        assertThat(saved.freezeType()).isEqualTo(FreezeType.NONE);
        assertThat(saved.frozenAt()).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_history WHERE food_id=? AND action_type='CREATE' AND previous_storage_type IS NULL AND new_storage_type='FRIDGE' AND quantity_text='1모'",
                Long.class, saved.foodId())).isEqualTo(1L);
        mvc.perform(get("/inventory")).andExpect(content().string(containsString("두부")));
    }

    @Test
    void leftoverDefaultsToFreezerAndUnknownDateStaysUnknown() {
        service.create(form(null, FoodSourceType.DELIVERY_LEFTOVER, FreezeType.COMMERCIAL_FROZEN, null, false));
        FoodItem food = service.findActive().getFirst();
        assertThat(food.storageType()).isEqualTo(StorageType.FREEZER);
        assertThat(food.freezeType()).isEqualTo(FreezeType.HOME_FROZEN);
        assertThat(food.frozenAt()).isNull();
    }

    @Test
    void explicitLocationWinsAndNonFreezerClearsFreezingFields() {
        service.create(form(StorageType.ROOM, FoodSourceType.DELIVERY_LEFTOVER,
                FreezeType.COMMERCIAL_FROZEN, LocalDate.of(2026, 9, 1), true));
        FoodItem food = service.findActive().getFirst();
        assertThat(food.storageType()).isEqualTo(StorageType.ROOM);
        assertThat(food.freezeType()).isEqualTo(FreezeType.NONE);
        assertThat(food.frozenAt()).isNull();
    }

    @Test
    void newCommercialFreezingUsesSeoulToday() {
        service.create(form(StorageType.FREEZER, FoodSourceType.PURCHASE,
                FreezeType.COMMERCIAL_FROZEN, null, true));
        FoodItem food = service.findActive().getFirst();
        assertThat(food.freezeType()).isEqualTo(FreezeType.COMMERCIAL_FROZEN);
        assertThat(food.frozenAt()).isEqualTo(LocalDate.of(2026, 9, 13));
    }

    @Test
    void existingFreezerDateIsPreservedAndMissingTypeDefaultsToHomeFrozen() {
        service.create(form(StorageType.FREEZER, null, null, LocalDate.of(2026, 9, 1), false));
        FoodItem food = service.findActive().getFirst();
        assertThat(food.freezeType()).isEqualTo(FreezeType.HOME_FROZEN);
        assertThat(food.frozenAt()).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @Test
    void serviceRejectsInvalidNameAndMissingStorageWithoutWrites() {
        assertThatThrownBy(() -> service.create(new FoodCreateForm(" ", null, null, null,
                null, null, null, null, null, null, false, null, null, null, null, null)))
                .isInstanceOf(InvalidFoodException.class);
        assertThat(service.findActive()).isEmpty();
    }

    @Test
    void futureDatesAreRejectedByServerAndInputIsPreserved() throws Exception {
        for (String field : new String[]{"purchasedAt", "openedAt", "frozenAt"}) {
            mvc.perform(post("/inventory").param("foodName", "냉동 만두").param("storageType", "FREEZER")
                    .param("quantityAmount", "1").param("quantityUnit", "팩").param(field, "2026-09-14")).andExpect(status().isOk())
                    .andExpect(model().attributeHasFieldErrors("foodForm", field))
                    .andExpect(content().string(containsString("냉동 만두")));
        }
        assertThat(service.findActive()).isEmpty();
    }

    @Test
    void malformedEnumAndDatesBecomeFormErrors() throws Exception {
        mvc.perform(post("/inventory").param("foodName", "두부").param("storageType", "INVALID"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("foodForm", "storageType"));
        mvc.perform(post("/inventory").param("foodName", "두부").param("storageType", "FRIDGE")
                .param("expiredAt", "not-a-date"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("foodForm", "expiredAt"));
        assertThat(service.findActive()).isEmpty();
    }

    @Test
    void expiredFoodIsAllowedWithCautionAndNamesAreEscaped() throws Exception {
        mvc.perform(post("/inventory").param("foodName", "<script>alert(1)</script>")
                .param("storageType", "FRIDGE").param("quantityAmount", "1").param("quantityUnit", "개").param("expiredAt", "2026-09-01"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/inventory")).andExpect(status().isOk())
                .andExpect(content().string(containsString("경과 · 확인 필요")))
                .andExpect(content().string(containsString("&lt;script&gt;")));
    }

    @Test
    void listContainsOnlyActiveItemsInStableNewestOrder() {
        long first = service.create(form(StorageType.FRIDGE, null, null, null, false));
        long second = service.create(form(StorageType.FREEZER, null, null, null, false));
        long terminal = service.create(form(StorageType.ROOM, null, null, null, false));
        jdbc.update("UPDATE food_item SET status='CONSUMED' WHERE food_id=?", terminal);
        assertThat(service.findActive()).extracting(FoodItem::foodId).containsExactly(second, first);
    }

    @Test
    void actualHistoryInsertFailureRollsBackFoodInsert() {
        jdbc.execute("CREATE FUNCTION reject_test_history() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test history failure'; END $$");
        jdbc.execute("CREATE TRIGGER reject_test_history BEFORE INSERT ON food_history FOR EACH ROW EXECUTE FUNCTION reject_test_history()");
        try {
            assertThatThrownBy(() -> service.create(form(StorageType.FRIDGE, null, null, null, false)))
                    .isInstanceOf(DataAccessException.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item", Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM food_history", Long.class)).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER reject_test_history ON food_history");
            jdbc.execute("DROP FUNCTION reject_test_history()");
        }
    }

    private static FoodCreateForm form(StorageType storage, FoodSourceType source, FreezeType freeze,
            LocalDate frozenAt, boolean freezeToday) {
        return new FoodCreateForm("테스트 음식", storage, null, BigDecimal.ONE, null, null, null,
                frozenAt, source, freeze, freezeToday, null, null, null, null, "끼");
    }

    @Test
    void quantityIsRequiredButCapacityIsOptional() throws Exception {
        for (String quantity : new String[]{"", "   "}) {
            mvc.perform(post("/inventory").param("foodName", "밀키트").param("storageType", "FRIDGE")
                    .param("quantityAmount", quantity).param("quantityUnit", "팩")).andExpect(status().isOk())
                    .andExpect(model().attributeHasFieldErrors("foodForm", "quantityAmount"));
        }
        assertThat(service.findActive()).isEmpty();
        long id = service.create(form(StorageType.FRIDGE, null, null, null, false));
        assertThat(service.findById(id).capacityText()).isNull();
    }

    @Test
    void capacityAndParentsSourceRoundTripToDetail() throws Exception {
        mvc.perform(post("/inventory").param("foodName", "부모님 반찬").param("quantityAmount", "2").param("quantityUnit", "통")
                .param("capacityText", "300g").param("sourceType", "PARENTS").param("storageType", "FRIDGE")
                .param("category", "반찬").param("memo", "일요일에 받음").param("purchasedAt", "2026-09-12")
                .param("openedAt", "2026-09-13")).andExpect(status().is3xxRedirection());
        FoodItem food = service.findActive().getFirst();
        assertThat(food.capacityText()).isEqualTo("300g");
        assertThat(food.sourceType()).isEqualTo(FoodSourceType.PARENTS);
        mvc.perform(get("/inventory/" + food.foodId())).andExpect(status().isOk())
                .andExpect(content().string(containsString("부모님의 은혜")))
                .andExpect(content().string(containsString("300g")))
                .andExpect(content().string(containsString("2통")))
                .andExpect(content().string(containsString("2026-09-12")))
                .andExpect(content().string(containsString("일요일에 받음")));
        mvc.perform(get("/inventory")).andExpect(content().string(containsString("/inventory/" + food.foodId())));
        mvc.perform(get("/inventory/999999999")).andExpect(status().isNotFound());
    }

    @Test
    void selectingTodayOverridesAnObsoleteFrozenDate() {
        long id = service.create(form(StorageType.FREEZER, null, null, LocalDate.of(2030, 1, 1), true));
        assertThat(service.findById(id).frozenAt()).isEqualTo(LocalDate.of(2026, 9, 13));
    }

    @Test
    void otherSourceMemoIsStoredSeparatelyAndEscapedInDetail() throws Exception {
        mvc.perform(post("/inventory").param("foodName", "선물 소스").param("quantityAmount", "1").param("quantityUnit", "병")
                .param("storageType", "FRIDGE").param("sourceType", "ETC")
                .param("sourceMemo", " <b>지인 선물</b> ").param("memo", "일반 메모"))
                .andExpect(status().is3xxRedirection());
        FoodItem food = service.findActive().getFirst();
        assertThat(food.sourceMemo()).isEqualTo("<b>지인 선물</b>");
        assertThat(food.memo()).isEqualTo("일반 메모");
        mvc.perform(get("/inventory/" + food.foodId())).andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;b&gt;지인 선물&lt;/b&gt;")));
    }

    @Test
    void sourceMemoIsIgnoredUnlessOtherSourceWasExplicitlySelected() throws Exception {
        for (String source : new String[]{"", "PURCHASE"}) {
            mvc.perform(post("/inventory").param("foodName", "소스").param("quantityAmount", "1").param("quantityUnit", "병")
                    .param("storageType", "FRIDGE").param("sourceType", source)
                    .param("sourceMemo", "이전 입력"))
                    .andExpect(status().is3xxRedirection());
        }
        assertThat(service.findActive()).allSatisfy(food -> assertThat(food.sourceMemo()).isNull());
    }

    @Test
    void sourceMemoLengthIsValidatedAndInputPreserved() throws Exception {
        mvc.perform(post("/inventory").param("foodName", "소스").param("quantityAmount", "1").param("quantityUnit", "병")
                .param("storageType", "FRIDGE").param("sourceType", "ETC").param("sourceMemo", "가".repeat(201)))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("foodForm", "sourceMemo"));
        assertThat(service.findActive()).isEmpty();
    }

    @Test
    void legacyDeliveryMigratesWithoutLosingInventoryOrInventingQuantity() {
        String schema = "legacy_upgrade";
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).target("1").load().migrate();
        jdbc.update("INSERT INTO legacy_upgrade.food_item(food_name,storage_type,source_type,freeze_type,expired_at) VALUES ('legacy meal','FREEZER','DELIVERY','COMMERCIAL_FROZEN','2026-10-01')");
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).load().migrate();
        var saved = jdbc.queryForMap("SELECT source_type,freeze_type,quantity_text,capacity_text,expired_at,sell_by_at FROM legacy_upgrade.food_item");
        assertThat(saved.get("source_type")).isEqualTo("DELIVERY_LEFTOVER");
        assertThat(saved.get("freeze_type")).isEqualTo("HOME_FROZEN");
        assertThat(saved.get("quantity_text")).isNull();
        assertThat(saved.get("capacity_text")).isNull();
        assertThat(saved.get("expired_at").toString()).isEqualTo("2026-10-01");
        assertThat(saved.get("sell_by_at")).isNull();
    }

    @ParameterizedTest
    @CsvSource(value = {"2026-09-20,NULL", "NULL,2026-09-21", "2026-09-20,2026-09-21", "NULL,NULL"}, nullValues = "NULL")
    void packageDatesAreIndependentlyOptional(String sellBy, String useBy) throws Exception {
        var request = post("/inventory").param("foodName", "날짜 확인 식품")
                .param("quantityAmount", "1").param("quantityUnit", "개").param("storageType", "FRIDGE");
        if (sellBy != null) request.param("sellByAt", sellBy);
        if (useBy != null) request.param("expiredAt", useBy);
        mvc.perform(request).andExpect(status().is3xxRedirection());
        FoodItem saved = service.findById(service.findActive().getFirst().foodId());
        assertThat(saved.sellByAt()).isEqualTo(sellBy == null ? null : LocalDate.parse(sellBy));
        assertThat(saved.expiredAt()).isEqualTo(useBy == null ? null : LocalDate.parse(useBy));
        var detail = mvc.perform(get("/inventory/" + saved.foodId())).andExpect(status().isOk());
        if (sellBy != null) detail.andExpect(content().string(containsString(sellBy)));
        if (useBy != null) detail.andExpect(content().string(containsString(useBy)));
    }

    @Test
    void pastSellByDoesNotBecomeExpiredUseBy() throws Exception {
        mvc.perform(post("/inventory").param("foodName", "소스").param("quantityAmount", "1").param("quantityUnit", "병")
                .param("storageType", "FRIDGE").param("sellByAt", "2026-09-01"))
                .andExpect(status().is3xxRedirection());
        FoodItem saved = service.findActive().getFirst();
        assertThat(saved.expiredAt()).isNull();
        String html = mvc.perform(get("/inventory/" + saved.foodId())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(html).contains("유통기한 경과").doesNotContain("소비기한 경과");
    }

    @Test
    void invalidSellByReturnsAnErrorWithoutLosingUseBy() throws Exception {
        mvc.perform(post("/inventory").param("foodName", "소스").param("quantityAmount", "1").param("quantityUnit", "병")
                .param("storageType", "FRIDGE").param("sellByAt", "not-a-date").param("expiredAt", "2026-09-21"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("foodForm", "sellByAt"))
                .andExpect(content().string(containsString("2026-09-21")));
        assertThat(service.findActive()).isEmpty();
    }
    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.001", "6.343345", "1000000000", "1.2345", "반 봉지", "NaN"})
    void rejectsInvalidNumericQuantityWithoutWriting(String amount) throws Exception {
        mvc.perform(post("/inventory").param("foodName", "만두").param("storageType", "FREEZER")
                .param("quantityAmount", amount).param("quantityUnit", "봉지"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("foodForm", "quantityAmount"))
                .andExpect(content().string(containsString("만두")))
                .andExpect(content().string(containsString("봉지")));
        assertThat(service.findActive()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_history", Long.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "가나다라마바사아자차카"})
    void rejectsMissingOrLongUnit(String unit) throws Exception {
        mvc.perform(post("/inventory").param("foodName", "만두").param("storageType", "FREEZER")
                .param("quantityAmount", "0.5").param("quantityUnit", unit))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("foodForm", "quantityUnit"));
        assertThat(service.findActive()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"0.50,봉지,0.5봉지", "0.01,g,0.01g", "999999999.99,mL,999999999.99mL", "2.00,팩,2팩"})
    void structuredQuantityRoundTripsWithoutRounding(String amount, String unit, String display) throws Exception {
        mvc.perform(post("/inventory").param("foodName", "수량 확인").param("storageType", "FRIDGE")
                .param("quantityAmount", amount).param("quantityUnit", "  " + unit + "  "))
                .andExpect(status().is3xxRedirection());
        FoodItem saved = service.findById(service.findActive().getFirst().foodId());
        assertThat(saved.quantityAmount()).isEqualByComparingTo(amount);
        assertThat(saved.quantityUnit()).isEqualTo(unit);
        assertThat(saved.quantityText()).isEqualTo(display);
        for (String path : new String[]{"/inventory", "/inventory/" + saved.foodId(), "/history"}) {
            mvc.perform(get(path)).andExpect(status().isOk()).andExpect(content().string(containsString(display)));
        }
        assertThat(jdbc.queryForObject("SELECT quantity_text FROM food_history WHERE food_id=?", String.class, saved.foodId()))
                .isEqualTo(display);
    }

    @Test
    void customUnitIsEscapedAndLegacyTextStillRenders() throws Exception {
        mvc.perform(post("/inventory").param("foodName", "사용자 단위").param("storageType", "FRIDGE")
                .param("quantityAmount", "2").param("quantityUnit", "<b>팩</b>"))
                .andExpect(status().is3xxRedirection());
        long id = service.findActive().getFirst().foodId();
        mvc.perform(get("/inventory/" + id)).andExpect(status().isOk())
                .andExpect(content().string(containsString("2&lt;b&gt;팩&lt;/b&gt;")));
        long legacyId = jdbc.queryForObject("INSERT INTO food_item(food_name,storage_type,quantity_text) VALUES ('기존 음식','FRIDGE','반 봉지') RETURNING food_id", Long.class);
        FoodItem legacy = service.findById(legacyId);
        assertThat(legacy.quantityAmount()).isNull();
        assertThat(legacy.quantityUnit()).isNull();
        mvc.perform(get("/inventory/" + legacyId)).andExpect(status().isOk())
                .andExpect(content().string(containsString("반 봉지")));
    }

    @Test
    void v5PreservesExistingFoodAndHistoryExactly() {
        String schema = "quantity_upgrade";
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).target("4").load().migrate();
        jdbc.update("INSERT INTO quantity_upgrade.food_item(food_name,storage_type,quantity_text) VALUES ('기존 음식','FRIDGE','반 봉지')");
        jdbc.update("INSERT INTO quantity_upgrade.food_history(food_id,action_type,new_storage_type,quantity_text) SELECT food_id,'CREATE','FRIDGE',quantity_text FROM quantity_upgrade.food_item");
        var beforeFood = jdbc.queryForMap("SELECT * FROM quantity_upgrade.food_item");
        var beforeHistory = jdbc.queryForMap("SELECT * FROM quantity_upgrade.food_history");
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).load().migrate();
        var afterFood = jdbc.queryForMap("SELECT * FROM quantity_upgrade.food_item");
        assertThat(afterFood.remove("quantity_amount")).isNull();
        assertThat(afterFood.remove("quantity_unit")).isNull();
        assertThat(afterFood).isEqualTo(beforeFood);
        var afterHistory = jdbc.queryForMap("SELECT * FROM quantity_upgrade.food_history"); afterHistory.remove("changes_text"); assertThat(afterHistory).isEqualTo(beforeHistory);
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder editRequest(FoodItem food) {
        return post("/inventory/" + food.foodId() + "/edit")
                .param("expectedUpdatedAt", food.updatedAt().toString()).param("foodName", food.foodName())
                .param("storageType", food.storageType().name()).param("sourceType", food.sourceType().name())
                .param("quantityAmount", food.quantityAmount() == null ? "1" : food.quantityAmount().stripTrailingZeros().toPlainString())
                .param("quantityUnit", food.quantityUnit() == null ? "개" : food.quantityUnit());
    }

    @Test
    void editFormLoadsAllFieldsAndUsesDetailForCancel() throws Exception {
        mvc.perform(post("/inventory").param("foodName", "수정할 음식").param("storageType", "FREEZER")
                .param("sourceType", "ETC").param("sourceMemo", "지인").param("quantityAmount", "0.5").param("quantityUnit", "봉지")
                .param("capacityText", "300g").param("category", "간편식").param("memo", "남겨둔 메모")
                .param("freezeType", "COMMERCIAL_FROZEN").param("frozenAt", "2026-09-10")
                .param("sellByAt", "2026-09-20").param("expiredAt", "2026-09-21")
                .param("openedAt", "2026-09-12").param("purchasedAt", "2026-09-11"))
                .andExpect(status().is3xxRedirection());
        FoodItem food = service.findActive().getFirst();
        mvc.perform(get("/inventory/" + food.foodId() + "/edit")).andExpect(status().isOk())
                .andExpect(model().attribute("foodForm", FoodCreateForm.from(food)))
                .andExpect(content().string(containsString("수정 내용 저장하기")))
                .andExpect(content().string(containsString("value=\"0.5\"")))
                .andExpect(content().string(containsString("name=\"expectedUpdatedAt\"")))
                .andExpect(content().string(containsString("action=\"/inventory/" + food.foodId() + "/edit\"")));
    }

    @Test
    void updatePersistsChangesAndFullEscapedHistory() throws Exception {
        long id = service.create(form(StorageType.FRIDGE, null, null, null, false));
        FoodItem before = service.findById(id);
        var request = editRequest(before);
        request.param("foodName", "ignored"); // replace values below, rather than submit duplicate values
        request = post("/inventory/" + id + "/edit").param("expectedUpdatedAt", before.updatedAt().toString())
                .param("foodName", "새 이름").param("quantityAmount", "2.5").param("quantityUnit", "팩")
                .param("storageType", "FREEZER").param("sourceType", "ETC").param("sourceMemo", "선물")
                .param("freezeType", "HOME_FROZEN").param("frozenAt", "2026-09-12")
                .param("capacityText", "500g").param("category", "반찬").param("memo", "<b>" + "긴 메모".repeat(100) + "</b>")
                .param("purchasedAt", "2026-09-10").param("openedAt", "2026-09-11")
                .param("sellByAt", "2026-09-20").param("expiredAt", "2026-09-21");
        mvc.perform(request).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/inventory/" + id));
        FoodItem after = service.findById(id);
        assertThat(after.createdAt()).isEqualTo(before.createdAt());
        assertThat(after.updatedAt()).isAfter(before.updatedAt());
        assertThat(after.foodName()).isEqualTo("새 이름");
        assertThat(after.quantityText()).isEqualTo("2.5팩");
        assertThat(after.sourceMemo()).isEqualTo("선물");
        assertThat(after.capacityText()).isEqualTo("500g");
        assertThat(after.frozenAt()).isEqualTo(LocalDate.of(2026,9,12));
        String changes = jdbc.queryForObject("SELECT changes_text FROM food_history WHERE food_id=? AND action_type='UPDATE'", String.class, id);
        assertThat(changes).contains("음식명: 테스트 음식 → 새 이름", "보관 위치: 냉장실 → 냉동실", "수량: 1 → 2.5", "단위: 끼 → 팩", "소비기한: - → 2026-09-21", after.memo());
        mvc.perform(get("/history")).andExpect(status().isOk())
                .andExpect(content().string(containsString("수정")))
                .andExpect(content().string(containsString("&lt;b&gt;")));
        mvc.perform(get("/inventory/" + id)).andExpect(content().string(containsString(">수정 <time")));
    }

    @Test
    void unchangedSaveDoesNotWriteOrDisplayModifiedTimestamp() throws Exception {
        long id = service.create(form(StorageType.FRIDGE, null, null, null, false));
        FoodItem before = service.findById(id);
        mvc.perform(editRequest(before)).andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", "변경한 내용이 없어."));
        assertThat(service.findById(id)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_history WHERE food_id=?", Long.class,id)).isEqualTo(1L);
        String html = mvc.perform(get("/inventory/" + id)).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(html).contains(">등록 <time").doesNotContain(">수정 <time");
    }

    @Test
    void invalidAndStaleEditsPreserveDataAndSubmittedInputs() throws Exception {
        long id = service.create(form(StorageType.FRIDGE, null, null, null, false));
        FoodItem before = service.findById(id);
        mvc.perform(editRequest(before).param("memo", "첫 변경"))
                .andExpect(status().is3xxRedirection());
        FoodItem changed = service.findById(id);
        mvc.perform(editRequest(before).param("memo", "오래된 화면"))
                .andExpect(status().isOk()).andExpect(model().hasErrors())
                .andExpect(content().string(containsString("다른 화면에서")))
                .andExpect(content().string(containsString("오래된 화면")));
        mvc.perform(editRequest(changed).param("purchasedAt", "2026-09-14").param("memo", "입력 유지"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("foodForm", "purchasedAt"))
                .andExpect(content().string(containsString("입력 유지")));
        assertThat(service.findById(id)).isEqualTo(changed);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_history WHERE food_id=?", Long.class,id)).isEqualTo(2L);
    }

    @Test
    void legacyQuantityRequiresExplicitConfirmationAndUpdatesWithoutGuessing() throws Exception {
        long id = jdbc.queryForObject("INSERT INTO food_item(food_name,storage_type,quantity_text) VALUES ('기존 음식','FRIDGE','반 봉지') RETURNING food_id", Long.class);
        FoodItem before = service.findById(id);
        mvc.perform(get("/inventory/" + id + "/edit")).andExpect(status().isOk())
                .andExpect(content().string(containsString("기존 수량: 반 봉지")));
        mvc.perform(post("/inventory/" + id + "/edit").param("expectedUpdatedAt", before.updatedAt().toString())
                .param("foodName", "기존 음식").param("storageType", "FRIDGE"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("foodForm", "quantityAmount", "quantityUnit"));
        assertThat(service.findById(id)).isEqualTo(before);
        mvc.perform(editRequest(before)).andExpect(status().is3xxRedirection());
        assertThat(service.findById(id).quantityAmount()).isEqualByComparingTo("1");
        assertThat(jdbc.queryForObject("SELECT changes_text FROM food_history WHERE food_id=?", String.class,id))
                .contains("반 봉지 (기존 입력) → 1");
    }

    @Test
    void editHistoryFailureRollsBackFoodAndDates() {
        long id = service.create(form(StorageType.FRIDGE, null, null, null, false));
        FoodItem before = service.findById(id);
        jdbc.execute("CREATE FUNCTION reject_edit_history() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test'; END $$");
        jdbc.execute("CREATE TRIGGER reject_edit_history BEFORE INSERT ON food_history FOR EACH ROW EXECUTE FUNCTION reject_edit_history()");
        try {
            assertThatThrownBy(() -> service.update(id, form(StorageType.FREEZER, null, null, null, false), before.updatedAt()))
                    .isInstanceOf(DataAccessException.class);
            assertThat(service.findById(id)).isEqualTo(before);
        } finally {
            jdbc.execute("DROP TRIGGER reject_edit_history ON food_history");
            jdbc.execute("DROP FUNCTION reject_edit_history()");
        }
    }

    @Test
    void terminalAndMissingFoodCannotBeEdited() throws Exception {
        long id = service.create(form(StorageType.FRIDGE, null, null, null, false));
        FoodItem before = service.findById(id);
        jdbc.update("UPDATE food_item SET status='CONSUMED' WHERE food_id=?",id);
        mvc.perform(get("/inventory/" + id + "/edit")).andExpect(redirectedUrl("/inventory/" + id));
        mvc.perform(editRequest(before)).andExpect(status().isOk()).andExpect(model().hasErrors());
        assertThat(service.findById(id).status()).isEqualTo(FoodStatus.CONSUMED);
        mvc.perform(get("/inventory/999999/edit")).andExpect(status().isNotFound());
    }

    @Test
    void missingStorageUsesNewMessageWithoutRedundantHelp() throws Exception {
        mvc.perform(post("/inventory").param("foodName","두부").param("quantityAmount","1").param("quantityUnit","모"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("아앗 필수 정보라구!")));
        String html = mvc.perform(get("/inventory/new")).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(html).doesNotContain("보관할 장소를 골라줘.");
    }
    @Test
    void blankRegistrationShowsRequiredErrorsOnlyInline() throws Exception {
        var result = mvc.perform(post("/inventory")).andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("foodForm", "foodName", "quantityAmount", "quantityUnit", "storageType"))
                .andReturn();
        String html = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(html).contains("novalidate", "음식명을 입력해줘.", "수량을 입력해줘.", "단위를 입력해줘.", "아앗 필수 정보라구!");
        for (String field : new String[]{"foodName", "quantityAmount", "quantityUnit", "storageType"}) {
            assertThat(html).contains("id=\"" + field + "-error\"");
        }
        assertThat(html).doesNotContain("입력 내용을 확인해 줘.", "class=\"error-summary\"");
        assertThat(html).contains("aria-invalid=\"true\"", "quantityHelp quantityAmount-error", "class=\"invalid\"");
        assertThat(service.findActive()).isEmpty();
    }

    @Test
    void fieldAndBusinessErrorsAreReportedTogetherWithoutDuplicates() throws Exception {
        var result = mvc.perform(post("/inventory").param("quantityAmount","0").param("quantityUnit","팩")
                .param("purchasedAt","2026-09-14")).andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("foodForm","foodName","quantityAmount","storageType","purchasedAt"))
                .andReturn();
        var binding = (org.springframework.validation.BindingResult) result.getModelAndView().getModel().get("org.springframework.validation.BindingResult.foodForm");
        assertThat(binding.getFieldErrors("foodName")).hasSize(1);
        assertThat(binding.getFieldErrors("quantityAmount")).hasSize(1);
        assertThat(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("value=\"팩\"", "value=\"2026-09-14\"", "open");
    }

    @Test
    void deliveryStorageDefaultStillWorksWithOtherValidationErrors() throws Exception {
        var result = mvc.perform(post("/inventory").param("sourceType","DELIVERY_LEFTOVER"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("foodForm","foodName"))
                .andReturn();
        var binding = (org.springframework.validation.BindingResult) result.getModelAndView().getModel().get("org.springframework.validation.BindingResult.foodForm");
        assertThat(binding.hasFieldErrors("storageType")).isFalse();
    }
    @ParameterizedTest
    @CsvSource(value={"2026-09-01,2026-09-20,0", "2026-09-01,2026-09-13,0", "2026-09-01,2026-09-12,1", "2026-09-01,NULL,1", "NULL,2026-09-12,1", "2026-09-20,NULL,0", "NULL,NULL,0"},nullValues="NULL")
    void useByTakesPriorityOverSellByWarnings(String sellBy,String useBy,int warningCount) throws Exception {
        var request=post("/inventory").param("foodName","기한 확인").param("storageType","FRIDGE").param("quantityAmount","1").param("quantityUnit","개");
        if(sellBy!=null)request.param("sellByAt",sellBy);
        if(useBy!=null)request.param("expiredAt",useBy);
        mvc.perform(request).andExpect(status().is3xxRedirection());
        String html=mvc.perform(get("/inventory")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(html.split("class=\"expiry-icon\"",-1).length-1).isEqualTo(warningCount);
        assertThat(html).doesNotContain("<small");
        if(sellBy!=null)assertThat(html).contains(sellBy.replace('-','.'));
        long id=service.findActive().getFirst().foodId();
        String detail=mvc.perform(get("/inventory/"+id)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        if(useBy!=null) assertThat(detail).doesNotContain("유통기한 경과");
        assertThat(detail.split("class=\"error-summary detail-expiry-alert\"",-1).length-1).isEqualTo(warningCount);
        assertThat(detail).doesNotContain("<dd class=\"small expired\"");
        if(warningCount==1) {
            String warning=(useBy==null ? "유통기한" : "소비기한")+" 경과됐어. 확인이 필요해!";
            assertThat(detail).contains(warning);
            assertThat(detail.indexOf(warning)).isLessThan(detail.indexOf("<section class=\"form-section\">"));
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            "NULL,NULL,NULL,0",
            "2026-06-14,NULL,NULL,0",
            "2026-06-13,NULL,NULL,0",
            "2026-06-12,NULL,NULL,1",
            "2026-06-12,2026-09-01,2026-09-20,1",
            "2026-06-12,2026-09-01,2026-09-12,2",
            "2026-06-12,2026-09-01,NULL,2"
    }, nullValues = "NULL")
    void openingWarningsAgreeAcrossHomeListAndDetail(String opened, String sellBy, String useBy,
                                                      int warnings) throws Exception {
        var request = post("/inventory").param("foodName", "개봉 확인")
                .param("storageType", "FRIDGE").param("quantityAmount", "1").param("quantityUnit", "개");
        if (opened != null) request.param("openedAt", opened);
        if (sellBy != null) request.param("sellByAt", sellBy);
        if (useBy != null) request.param("expiredAt", useBy);
        mvc.perform(request).andExpect(status().is3xxRedirection());
        var food = service.findActive().getFirst();
        var list = mvc.perform(get("/inventory")).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(list.split("class=\"expiry-icon\"", -1).length - 1).isEqualTo(warnings);
        assertThat(list).doesNotContain("기한 미입력");
        if (opened == null) assertThat(list).doesNotContain("<span>개봉일</span>");
        else assertThat(list).contains("<span>개봉일</span>", opened.replace('-', '.'));
        if (sellBy == null) assertThat(list).doesNotContain("<span>유통기한</span>");
        if (useBy == null) assertThat(list).doesNotContain("<span>소비기한</span>");
        var detail = mvc.perform(get("/inventory/" + food.foodId())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(detail.split("class=\"error-summary detail-expiry-alert\"", -1).length - 1).isEqualTo(warnings);
        assertThat(detail.split("class=\"detail-warning\"", -1).length - 1).isEqualTo(warnings);
        assertThat(detail.split("class=\"expiry-icon\"", -1).length - 1).isEqualTo(warnings);
        if (food.openedOverdue(LocalDate.of(2026, 9, 13))) {
            assertThat(detail).contains("개봉 후 3개월이 지났어. 확인이 필요해!",
                    "class=\"detail-warning\"><dt>개봉일<span class=\"expiry-icon\"");
            assertThat(detail.indexOf("개봉 후 3개월이 지났어.")).isLessThan(detail.indexOf("<section class=\"form-section\">"));
        }
        var home = mvc.perform(get("/")).andExpect(status().isOk()).andReturn();
        assertThat((java.util.List<FoodItem>) home.getModelAndView().getModel().get("attentionFoods"))
                .hasSize(warnings == 0 ? 0 : 1);
        var html = home.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        if (warnings > 0) assertThat(html).contains("확인이 필요한 음식");
        if (food.openedOverdue(LocalDate.of(2026, 9, 13))) assertThat(html).contains("개봉 후 +");
        if (useBy != null) assertThat(html).doesNotContain("유통기한 +");
        assertThat(service.findActive().getFirst().expiredAt())
                .isEqualTo(useBy == null ? null : LocalDate.parse(useBy));
    }

    @ParameterizedTest
    @CsvSource({"2026-01-31,2026-04-30,false", "2026-01-31,2026-05-01,true",
            "2025-11-30,2026-02-28,false", "2025-11-30,2026-03-01,true",
            "2023-11-30,2024-02-29,false", "2023-11-30,2024-03-01,true"})
    void openingReminderUsesCalendarMonths(String opened, String today, boolean expected) {
        var food = new FoodItem(1L, "음식", StorageType.FRIDGE, null, null, null, null,
                LocalDate.parse(opened), null, FoodSourceType.ETC, FreezeType.NONE, FoodStatus.ACTIVE,
                null, null, null, null, null, null, null, null);
        assertThat(food.openedOverdue(LocalDate.parse(today))).isEqualTo(expected);
    }

    @Test
    void overviewCoversAllDateCombinationsWithoutContradictoryEmptyCard() throws Exception {
        mvc.perform(post("/inventory").param("foodName", "분기 검증 음식")
                .param("storageType", "FRIDGE").param("quantityAmount", "1").param("quantityUnit", "개"))
                .andExpect(status().is3xxRedirection());
        long id = service.findActive().getFirst().foodId();
        String[] deadlines = {null, "2026-09-12", "2026-09-13", "2026-09-14"};
        String[] openings = {null, "2026-06-12", "2026-06-13", "2026-06-14"};
        for (String useBy : deadlines) {
            for (String sellBy : deadlines) {
                for (String opened : openings) {
                    jdbc.update("UPDATE food_item SET expired_at=CAST(? AS date), sell_by_at=CAST(? AS date), opened_at=CAST(? AS date) WHERE food_id=?",
                            useBy, sellBy, opened, id);
                    boolean usePast = "2026-09-12".equals(useBy);
                    boolean sellPast = useBy == null && "2026-09-12".equals(sellBy);
                    boolean useToday = "2026-09-13".equals(useBy);
                    boolean sellToday = useBy == null && "2026-09-13".equals(sellBy);
                    boolean openPast = "2026-06-12".equals(opened);
                    boolean visible = usePast || sellPast || useToday || sellToday || openPast;
                    var result = mvc.perform(get("/")).andExpect(status().isOk()).andReturn();
                    var html = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
                    String scenario = "useBy=" + useBy + ", sellBy=" + sellBy + ", opened=" + opened;
                    assertThat((java.util.List<FoodItem>) result.getModelAndView().getModel().get("overviewFoods"))
                            .as(scenario).hasSize(visible ? 1 : 0);
                    assertThat(html.contains("지금 확인이 필요한 음식은 없어.")).as(scenario).isEqualTo(!visible);
                    assertThat(html.contains("소비기한 +")).as(scenario).isEqualTo(usePast);
                    assertThat(html.contains("유통기한 +")).as(scenario).isEqualTo(sellPast);
                    assertThat(html.contains("소비기한 오늘까지")).as(scenario).isEqualTo(useToday);
                    assertThat(html.contains("유통기한 오늘까지")).as(scenario).isEqualTo(sellToday);
                    assertThat(html.contains("개봉 후 +")).as(scenario).isEqualTo(openPast);
                    assertThat(html.split("class=\"alert-card overview-card\"", -1).length - 1)
                            .as(scenario).isEqualTo(visible ? 1 : 0);
                    if (visible) assertThat(html.indexOf("한눈에 보기")).as(scenario)
                            .isLessThan(html.indexOf("class=\"alert-card overview-card\""));
                }
            }
        }
        jdbc.update("UPDATE food_item SET status='CONSUMED', expired_at='2026-09-01', opened_at='2026-01-01' WHERE food_id=?", id);
        var model = mvc.perform(get("/")).andExpect(status().isOk()).andReturn().getModelAndView().getModel();
        assertThat((java.util.List<FoodItem>) model.get("overviewFoods")).isEmpty();
    }

}
