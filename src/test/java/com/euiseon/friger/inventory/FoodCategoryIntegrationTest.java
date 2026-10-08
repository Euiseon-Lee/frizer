package com.euiseon.friger.inventory;

import com.euiseon.friger.inventory.dao.FoodCategoryDao;
import com.euiseon.friger.inventory.exception.InvalidFoodException;
import com.euiseon.friger.inventory.service.FoodCategoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
@Transactional
class FoodCategoryIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6")
            .withDatabaseName("frizer_category_test");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired FoodCategoryService service;
    @Autowired FoodCategoryDao dao;
    @Autowired JdbcTemplate jdbc;

    @Test
    void catalogMatchesAgreedOrderAndContainsNoFakeMinorForLeafMajors() {
        var choices = service.choices();
        assertThat(choices).extracting(FoodCategoryService.Choice::label).containsExactly(
                "곡물류", "육류", "수산물", "콩·두부", "알류", "채소", "과일", "견과·씨앗", "유제품·대체품",
                "양념·소스", "김치", "절임", "젓갈", "요리류", "간식·디저트", "음료", "차", "주류", "기타 식품");
        assertThat(dao.minors()).hasSize(76);
        assertThat(choices.stream().filter(c -> !c.requiresMinor())).allSatisfy(c -> assertThat(c.minors()).isEmpty());
        assertThat(choices.stream().filter(FoodCategoryService.Choice::requiresMinor))
                .allSatisfy(c -> assertThat(c.minors()).isNotEmpty());
        assertThat(choices.getFirst().minors()).extracting(FoodCategoryDao.Minor::label)
                .containsExactly("곡류", "가루", "면", "떡", "빵");
    }

    @Test
    void validatesParentChildAndUsesTheSelectedMinorExample() {
        var selection = service.requireSelection("soy", "soy_tofu");
        assertThat(selection.displayLabel()).isEqualTo("콩·두부 › 두부");
        assertThat(selection.example()).isEqualTo("부침두부·찌개두부");
        assertThat(selection.majorCode()).isEqualTo("soy");
        assertThat(selection.minorCode()).isEqualTo("soy_tofu");
    }

    @Test
    void leafMajorIsCompleteWithoutMinor() {
        var selection = service.requireSelection(" beverage ", " ");
        assertThat(selection.displayLabel()).isEqualTo("음료");
        assertThat(selection.minorCode()).isNull();
        assertThat(selection.example()).contains("원두");
    }

    @ParameterizedTest
    @CsvSource(value = {
            "NULL,NULL,categoryMajorCode", "NULL,soy_tofu,categoryMajorCode",
            "unknown,NULL,categoryMajorCode", "콩·두부,두부,categoryMajorCode",
            "soy,NULL,categoryMinorCode", "soy,grain_bread,categoryMinorCode",
            "soy,unknown,categoryMinorCode", "beverage,soy_tofu,categoryMinorCode"
    }, nullValues = "NULL")
    void rejectsMissingUnknownOrMismatchedSelections(String major, String minor, String field) {
        assertThatThrownBy(() -> service.requireSelection(major, minor))
                .isInstanceOfSatisfying(InvalidFoodException.class, e -> assertThat(e.errors()).containsKey(field));
    }

    @Test
    void retiredOptionsAreHiddenAndCannotBeChosenButRemainResolvableInCatalog() {
        jdbc.update("UPDATE food_category_minor SET active=false WHERE code='soy_tofu'");
        assertThat(service.choices().stream().filter(c -> c.code().equals("soy")).findFirst().orElseThrow().minors())
                .extracting(FoodCategoryDao.Minor::code).doesNotContain("soy_tofu");
        assertThatThrownBy(() -> service.requireSelection("soy", "soy_tofu")).isInstanceOf(InvalidFoodException.class);
        assertThat(dao.minors()).anySatisfy(c -> {
            assertThat(c.code()).isEqualTo("soy_tofu");
            assertThat(c.label()).isEqualTo("두부");
            assertThat(c.active()).isFalse();
        });
    }

    @Test
    void retiringMajorAlsoHidesItsChildrenAndRejectsNewSelections() {
        jdbc.update("UPDATE food_category_major SET active=false WHERE code='soy'");
        assertThat(service.choices()).extracting(FoodCategoryService.Choice::code).doesNotContain("soy");
        assertThatThrownBy(() -> service.requireSelection("soy", "soy_tofu")).isInstanceOf(InvalidFoodException.class);
    }

    @Test
    void aMajorWithAllChildrenRetiredIsNotPresentedAsAValidLeaf() {
        jdbc.update("UPDATE food_category_minor SET active=false WHERE major_code='soy'");
        assertThat(service.choices()).extracting(FoodCategoryService.Choice::code).doesNotContain("soy");
        assertThatThrownBy(() -> service.requireSelection("soy", null)).isInstanceOf(InvalidFoodException.class);
    }

    @Test
    void retiringClassificationDoesNotBreakExistingReferencesOrUnrelatedEdits() {
        long id=jdbc.queryForObject("INSERT INTO food_master(user_id,food_name,category_major_code,category_minor_code) VALUES (1,'기존 음식','soy','soy_tofu') RETURNING master_id",Long.class);
        jdbc.update("UPDATE food_category_minor SET active=false WHERE code='soy_tofu'");
        jdbc.update("UPDATE food_category_major SET active=false WHERE code='soy'");
        assertThat(jdbc.update("UPDATE food_master SET food_name='기존 음식 이름 정정' WHERE master_id=?",id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT category_minor_code FROM food_master WHERE master_id=?",String.class,id)).isEqualTo("soy_tofu");
        assertThatThrownBy(() -> service.requireSelection("soy", "soy_tofu")).isInstanceOf(InvalidFoodException.class);
    }

    @Test
    void everyPublishedChoicePassesTheSameServerValidation() {
        for (var choice : service.choices()) {
            if (!choice.requiresMinor()) assertThat(service.requireSelection(choice.code(), null).majorLabel()).isEqualTo(choice.label());
            for (var minor : choice.minors())
                assertThat(service.requireSelection(choice.code(), minor.code()).minorLabel()).isEqualTo(minor.label());
        }
    }
}
