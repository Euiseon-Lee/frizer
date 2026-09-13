package com.euiseon.friger.inventory;

import java.time.Clock;
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
        mvc.perform(post("/inventory").param("foodName", "경과 음식").param("quantityText", "1개")
                .param("storageType", "ROOM").param("expiredAt", "2026-09-12")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/inventory").param("foodName", "오늘 음식").param("quantityText", "2팩")
                .param("storageType", "FRIDGE").param("expiredAt", "2026-09-13")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/inventory").param("foodName", "기한 미입력").param("quantityText", "1병")
                .param("storageType", "FRIDGE").param("sellByAt", "2026-09-01")).andExpect(status().is3xxRedirection());
        var home = mvc.perform(get("/")).andExpect(status().isOk()).andReturn().getModelAndView().getModel();
        assertThat((java.util.List<FoodItem>) home.get("expiredFoods")).extracting(FoodItem::foodName).containsExactly("경과 음식");
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
                .param("quantityText", "1모")).andExpect(status().is3xxRedirection())
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
                null, null, null, null, null, null, false, null, null, null, null)))
                .isInstanceOf(InvalidFoodException.class);
        assertThat(service.findActive()).isEmpty();
    }

    @Test
    void futureDatesAreRejectedByServerAndInputIsPreserved() throws Exception {
        for (String field : new String[]{"purchasedAt", "openedAt", "frozenAt"}) {
            mvc.perform(post("/inventory").param("foodName", "냉동 만두").param("storageType", "FREEZER")
                    .param("quantityText", "1팩").param(field, "2026-09-14")).andExpect(status().isOk())
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
                .param("storageType", "FRIDGE").param("quantityText", "1개").param("expiredAt", "2026-09-01"))
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
        return new FoodCreateForm("테스트 음식", storage, null, "1끼", null, null, null,
                frozenAt, source, freeze, freezeToday, null, null, null, null);
    }

    @Test
    void quantityIsRequiredButCapacityIsOptional() throws Exception {
        for (String quantity : new String[]{"", "   "}) {
            mvc.perform(post("/inventory").param("foodName", "밀키트").param("storageType", "FRIDGE")
                    .param("quantityText", quantity)).andExpect(status().isOk())
                    .andExpect(model().attributeHasFieldErrors("foodForm", "quantityText"));
        }
        assertThat(service.findActive()).isEmpty();
        long id = service.create(form(StorageType.FRIDGE, null, null, null, false));
        assertThat(service.findById(id).capacityText()).isNull();
    }

    @Test
    void capacityAndParentsSourceRoundTripToDetail() throws Exception {
        mvc.perform(post("/inventory").param("foodName", "부모님 반찬").param("quantityText", "2통")
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
        mvc.perform(post("/inventory").param("foodName", "선물 소스").param("quantityText", "1병")
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
            mvc.perform(post("/inventory").param("foodName", "소스").param("quantityText", "1병")
                    .param("storageType", "FRIDGE").param("sourceType", source)
                    .param("sourceMemo", "이전 입력"))
                    .andExpect(status().is3xxRedirection());
        }
        assertThat(service.findActive()).allSatisfy(food -> assertThat(food.sourceMemo()).isNull());
    }

    @Test
    void sourceMemoLengthIsValidatedAndInputPreserved() throws Exception {
        mvc.perform(post("/inventory").param("foodName", "소스").param("quantityText", "1병")
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
                .param("quantityText", "1개").param("storageType", "FRIDGE");
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
        mvc.perform(post("/inventory").param("foodName", "소스").param("quantityText", "1병")
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
        mvc.perform(post("/inventory").param("foodName", "소스").param("quantityText", "1병")
                .param("storageType", "FRIDGE").param("sellByAt", "not-a-date").param("expiredAt", "2026-09-21"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("foodForm", "sellByAt"))
                .andExpect(content().string(containsString("2026-09-21")));
        assertThat(service.findActive()).isEmpty();
    }
}
