package com.euiseon.friger.inventory;

import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;

/** Uses only a throwaway container, including a real V24 -> latest upgrade with data. */
@Testcontainers
class FoodCategoryMigrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6")
            .withDatabaseName("frizer_category_upgrade_test");
    static JdbcTemplate jdbc;
    static List<Map<String,Object>> mastersBefore, itemsBefore, historyBefore, receiptsBefore;

    @BeforeAll
    static void upgrade() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword())
                .target("24").load().migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword()));
        jdbc.update("INSERT INTO food_master(user_id,food_name,category) VALUES (1,'미분류 두부',NULL),(1,'이전 분류 음식','내 분류'),(1,'빈 그룹',NULL)");
        jdbc.update("INSERT INTO food_item(user_id,master_id,storage_type,quantity_text) VALUES (1,1,'FRIDGE','1팩'),(1,2,'ROOM','반 봉지')");
        jdbc.update("INSERT INTO food_history(user_id,food_id,action_type,new_storage_type,changes_text,recorded_food_name) VALUES (1,2,'CREATE','ROOM','분류: 내 분류','이전 분류 음식')");
        jdbc.update("INSERT INTO app_user(role) VALUES ('USER')");
        jdbc.update("INSERT INTO food_master(user_id,food_name,category,version_no) VALUES (2,'다른 사용자 음식','다른 분류',7)");
        jdbc.update("INSERT INTO food_item(user_id,master_id,storage_type,status,quantity_amount,quantity_unit,quantity_text) VALUES (2,4,'ROOM','DEPLETED',0,'개','0개')");
        jdbc.update("INSERT INTO food_history(user_id,food_id,action_type,new_storage_type,changes_text,recorded_food_name) VALUES (2,3,'CREATE','ROOM','분류: 다른 분류','다른 사용자 음식')");
        jdbc.update("INSERT INTO food_registration_receipt(user_id,request_id,request_payload,food_id) VALUES (1,'a2ad68fb-4b7c-40bb-93ea-a3c3a3319b4f','{\"category\":\"내 분류\"}',2)");
        mastersBefore = jdbc.queryForList("SELECT * FROM food_master ORDER BY master_id");
        itemsBefore = jdbc.queryForList("SELECT * FROM food_item ORDER BY food_id");
        historyBefore = jdbc.queryForList("SELECT * FROM food_history ORDER BY history_id");
        receiptsBefore = jdbc.queryForList("SELECT * FROM food_registration_receipt ORDER BY user_id,request_id");
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword()).load().migrate();
    }

    @Test
    void preservesAllExistingValuesAndDoesNotInferClassification() {
        var after = jdbc.queryForList("SELECT * FROM food_master WHERE master_id<=4 ORDER BY master_id");
        after.forEach(row -> {
            assertThat(row.remove("category_major_code")).isNull();
            assertThat(row.remove("category_minor_code")).isNull();
        });
        assertThat(after).isEqualTo(mastersBefore);
        assertThat(jdbc.queryForList("SELECT * FROM food_item ORDER BY food_id")).isEqualTo(itemsBefore);
        assertThat(jdbc.queryForList("SELECT * FROM food_history ORDER BY history_id")).isEqualTo(historyBefore);
        assertThat(jdbc.queryForList("SELECT * FROM food_registration_receipt ORDER BY user_id,request_id")).isEqualTo(receiptsBefore);
        // V11 writes a registration snapshot. Ensure this comparison includes real JSON, not just NULLs.
        assertThat(jdbc.queryForObject("SELECT after_snapshot->>'category' FROM food_history WHERE food_id=3",String.class))
                .isEqualTo("다른 분류");
    }

    @ParameterizedTest
    @CsvSource(value={"soy,soy_tofu", "beverage,NULL", "NULL,NULL"},nullValues="NULL")
    void allowsCompleteOrLegacySelections(String major, String minor) {
        assertThat(jdbc.update("INSERT INTO food_master(user_id,food_name,category_major_code,category_minor_code) VALUES (1,'검증',?,?)", major,minor)).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource(value={"soy,NULL", "soy,grain_bread", "beverage,soy_tofu", "NULL,soy_tofu", "unknown,NULL", "soy,unknown"},nullValues="NULL")
    void rejectsInvalidStructuredSelectionEvenOutsideApplication(String major, String minor) {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO food_master(user_id,food_name,category_major_code,category_minor_code) VALUES (1,'잘못된 분류',?,?)",major,minor))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void validatesChangesAsWellAsInsertions() {
        long id=jdbc.queryForObject("INSERT INTO food_master(user_id,food_name,category_major_code,category_minor_code) VALUES (1,'수정 검증','soy','soy_tofu') RETURNING master_id",Long.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE food_master SET category_major_code='grain' WHERE master_id=?",id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE food_master SET category_minor_code=NULL WHERE master_id=?",id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT category_major_code FROM food_master WHERE master_id=?",String.class,id)).isEqualTo("soy");
    }

    @Test
    void canMoveBetweenCompleteParentChildAndLeafSelections() {
        long id=jdbc.queryForObject("INSERT INTO food_master(user_id,food_name) VALUES (1,'전환 검증') RETURNING master_id",Long.class);
        assertThat(jdbc.update("UPDATE food_master SET category_major_code='soy',category_minor_code='soy_tofu' WHERE master_id=?",id)).isEqualTo(1);
        assertThat(jdbc.update("UPDATE food_master SET category_major_code='beverage',category_minor_code=NULL WHERE master_id=?",id)).isEqualTo(1);
        assertThat(jdbc.update("UPDATE food_master SET category_major_code='grain',category_minor_code='grain_bread' WHERE master_id=?",id)).isEqualTo(1);
    }

    @Test
    void cannotDeleteReferencedCatalogEntries() {
        jdbc.update("INSERT INTO food_master(user_id,food_name,category_major_code,category_minor_code) VALUES (1,'참조 검증','soy','soy_tofu')");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM food_category_minor WHERE code='soy_tofu'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("INSERT INTO food_master(user_id,food_name,category_major_code) VALUES (1,'대분류 참조 검증','beverage')");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM food_category_major WHERE code='beverage'"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
