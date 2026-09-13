package com.euiseon.friger.smoke;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.euiseon.friger.common.type.FoodActionType;
import com.euiseon.friger.common.type.FoodSourceType;
import com.euiseon.friger.common.type.FoodStatus;
import com.euiseon.friger.common.type.FreezeType;
import com.euiseon.friger.common.type.StorageType;
import com.euiseon.friger.history.entity.FoodHistory;
import com.euiseon.friger.inventory.entity.FoodItem;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Docker is required. Missing Docker is a failure, never a silent skip. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Transactional
@Import(DatabaseSmokeTest.MapperConfiguration.class)
class DatabaseSmokeTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.11")
            .withDatabaseName("frizer_test")
            .withUsername("frizer_test")
            .withPassword("frizer-test-only");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @MapperScan(basePackages = {"com.euiseon.friger.smoke", "com.euiseon.friger.inventory.dao",
            "com.euiseon.friger.history.dao"})
    static class MapperConfiguration {
    }

    @Autowired SmokeMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired SqlSessionFactory sqlSessionFactory;
    @Autowired Clock clock;

    @Test
    void contextPostgresMigrationAndXmlMapperAreReady() {
        assertThat(mapper.selectOne()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT version()", String.class)).startsWith("PostgreSQL 17.");
        assertThat(jdbc.queryForObject("SHOW TIME ZONE", String.class)).isEqualTo("Asia/Seoul");
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("6");
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                 WHERE table_schema = 'public' AND table_name IN ('food_item', 'food_history')
                """, String.class)).containsExactlyInAnyOrder("food_item", "food_history");
        assertThat(sqlSessionFactory.getConfiguration().hasStatement(
                SmokeMapper.class.getName() + ".findFood")).isTrue();
        assertThat(sqlSessionFactory.getConfiguration().isMapUnderscoreToCamelCase()).isTrue();
        assertThat(sqlSessionFactory.getConfiguration().isArgNameBasedConstructorAutoMapping()).isTrue();
    }

    @Test
    void requiredIndexesArePresent() {
        assertThat(jdbc.queryForList("""
                SELECT indexname FROM pg_indexes
                 WHERE schemaname = 'public' AND indexname LIKE 'ix_food_%'
                """, String.class)).containsExactlyInAnyOrder(
                "ix_food_item_active_expired", "ix_food_item_active_frozen", "ix_food_history_created");
    }

    static Stream<Arguments> storageCombinations() {
        List<Arguments> cases = new ArrayList<>();
        for (StorageType storage : StorageType.values()) {
            for (FreezeType freeze : FreezeType.values()) {
                for (LocalDate frozenAt : new LocalDate[] {null, LocalDate.of(2026, 9, 1)}) {
                    for (FoodStatus status : FoodStatus.values()) {
                        boolean allowed = storage == StorageType.FREEZER
                                ? freeze != FreezeType.NONE
                                : freeze == FreezeType.NONE && frozenAt == null;
                        cases.add(Arguments.of(storage.name(), freeze.name(), frozenAt, status.name(), allowed));
                    }
                }
            }
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{0}/{1}/date={2}/status={3}: allowed={4}")
    @MethodSource("storageCombinations")
    void enforcesStoragePolicyIncludingUnknownDatesAndTerminalStates(
            String storage, String freeze, LocalDate frozenAt, String status, boolean allowed) {
        if (allowed) {
            long id = mapper.insertFood("냉동 재고", storage, freeze, frozenAt, status);
            FoodItem saved = mapper.findFood(id);
            assertThat(saved.storageType().name()).isEqualTo(storage);
            assertThat(saved.freezeType().name()).isEqualTo(freeze);
            assertThat(saved.frozenAt()).isEqualTo(frozenAt);
            assertThat(saved.status().name()).isEqualTo(status);
        } else {
            assertThatThrownBy(() -> mapper.insertFood("냉동 재고", storage, freeze, frozenAt, status))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void duplicateNamesAndNullableOptionalFieldsAreAllowedWithDefaults() {
        long first = mapper.insertDefaults("두부");
        long second = mapper.insertDefaults("두부");
        assertThat(second).isNotEqualTo(first);
        FoodItem saved = mapper.findFood(first);
        assertThat(saved.foodName()).isEqualTo("두부");
        assertThat(saved.sourceType()).isEqualTo(FoodSourceType.ETC);
        assertThat(saved.freezeType()).isEqualTo(FreezeType.NONE);
        assertThat(saved.status()).isEqualTo(FoodStatus.ACTIVE);
        assertThat(saved.quantityText()).isNull();
        assertThat(saved.expiredAt()).isNull();
        assertThat(saved.purchasedAt()).isNull();
        assertThat(saved.openedAt()).isNull();
        assertThat(saved.frozenAt()).isNull();
        assertThat(saved.createdAt()).isNotNull();
        assertThat(saved.updatedAt()).isNotNull();
    }

    @ParameterizedTest
    @EnumSource(FoodSourceType.class)
    void mapsEnumNamesDatesAndTimestampInstants(FoodSourceType source) {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-09-13T00:30:00.123456+09:00");
        FoodItem input = new FoodItem(null, "곱도리탕", StorageType.FREEZER, "반찬", "1끼",
                LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 11),
                LocalDate.of(2026, 9, 12), source, FreezeType.HOME_FROZEN, FoodStatus.ACTIVE,
                "남은 음식", timestamp, timestamp, "300g", null, LocalDate.of(2026, 9, 18), null, null);
        long id = mapper.insertMappedFood(input);
        FoodItem saved = mapper.findFood(id);
        assertThat(saved).usingRecursiveComparison()
                .ignoringFields("foodId", "createdAt", "updatedAt").isEqualTo(input);
        assertThat(saved.foodId()).isEqualTo(id);
        assertThat(saved.createdAt().toInstant()).isEqualTo(timestamp.toInstant());
        assertThat(saved.updatedAt().toInstant()).isEqualTo(timestamp.toInstant());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "Food,INVALID,NONE,ACTIVE",
            "Food,FRIDGE,INVALID,ACTIVE",
            "Food,FRIDGE,NONE,INVALID",
            "NULL,FRIDGE,NONE,ACTIVE",
            "Food,NULL,NONE,ACTIVE",
            "Food,FRIDGE,NULL,ACTIVE",
            "Food,FRIDGE,NONE,NULL",
            "'',FRIDGE,NONE,ACTIVE"
    }, nullValues = "NULL")
    void rejectsInvalidEnumsAndMissingRequiredValues(String name, String storage, String freeze, String status) {
        assertThatThrownBy(() -> mapper.insertFood(name, storage, freeze, null, status))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsUnknownSourceType() {
        // A deliberately malformed fixture bypasses Java's enum restriction.
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO food_item (food_name, storage_type, source_type)
                VALUES ('Food', 'FRIDGE', 'INVALID')
                """)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "CREATE,NULL,FRIDGE", "CREATE,NULL,FREEZER", "CREATE,NULL,ROOM",
            "FREEZE,FRIDGE,FREEZER", "FREEZE,ROOM,FREEZER",
            "MOVE,FREEZER,FRIDGE", "MOVE,FREEZER,ROOM", "MOVE,FRIDGE,ROOM", "MOVE,ROOM,FRIDGE",
            "CONSUME,FREEZER,FREEZER", "DISCARD,FRIDGE,FRIDGE"
    }, nullValues = "NULL")
    void mapsHistoryAndAcceptsDefinedTransitions(String action, String previous, String next) {
        long foodId = mapper.insertDefaults("두부");
        long historyId = mapper.insertHistory(foodId, action, previous, next);
        FoodHistory saved = mapper.findHistory(historyId);
        assertThat(saved.historyId()).isEqualTo(historyId);
        assertThat(saved.foodId()).isEqualTo(foodId);
        assertThat(saved.actionType()).isEqualTo(FoodActionType.valueOf(action));
        assertThat(saved.previousStorageType()).isEqualTo(previous == null ? null : StorageType.valueOf(previous));
        assertThat(saved.newStorageType()).isEqualTo(StorageType.valueOf(next));
        assertThat(saved.quantityText()).isEqualTo("1끼");
        assertThat(saved.memo()).isEqualTo("행동 메모");
        assertThat(saved.createdAt()).isNotNull();
    }

    @ParameterizedTest
    @CsvSource(value = {
            "INVALID,NULL,FRIDGE", "CREATE,FRIDGE,FRIDGE", "CREATE,NULL,NULL",
            "CREATE,NULL,INVALID", "MOVE,INVALID,FRIDGE", "MOVE,FRIDGE,FRIDGE",
            "MOVE,FRIDGE,FREEZER", "MOVE,NULL,FRIDGE", "FREEZE,FREEZER,FREEZER",
            "FREEZE,NULL,FREEZER", "CONSUME,NULL,FRIDGE", "DISCARD,FRIDGE,ROOM",
            "NULL,NULL,FRIDGE"
    }, nullValues = "NULL")
    void rejectsInvalidHistoryTransitions(String action, String previous, String next) {
        long foodId = mapper.insertDefaults("두부");
        assertThatThrownBy(() -> mapper.insertHistory(foodId, action, previous, next))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsOrphanHistory() {
        assertThatThrownBy(() -> mapper.insertHistory(-1L, "CREATE", null, "FRIDGE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void foreignKeyPreventsDeletingAnItemWithHistory() {
        long foodId = mapper.insertDefaults("두부");
        mapper.insertHistory(foodId, "CREATE", null, "FRIDGE");
        assertThatThrownBy(() -> mapper.deleteFood(foodId)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
