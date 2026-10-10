package com.euiseon.friger.inventory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import com.euiseon.friger.inventory.dto.FoodCreateForm;
import com.euiseon.friger.inventory.service.FoodMasterService;
import com.euiseon.friger.inventory.service.FoodRegistrationService;
import com.euiseon.friger.inventory.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class FoodCategoryUiIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6")
            .withDatabaseName("frizer_category_ui_test");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired InventoryService inventory;
    @Autowired FoodMasterService masters;
    @Autowired FoodRegistrationService registrations;

    @BeforeEach void reset() {
        jdbc.update("DELETE FROM food_history");
        jdbc.update("DELETE FROM food_item");
        jdbc.update("DELETE FROM food_master");
        jdbc.update("DELETE FROM food_registration_receipt");
        jdbc.update("DELETE FROM food_category_history");
        jdbc.update("UPDATE food_category_minor SET active=true");
        jdbc.update("UPDATE food_category_major SET active=true");
    }

    private MockHttpServletRequestBuilder create(UUID token) {
        return post("/inventory").param("registrationRequestId", token.toString())
                .param("foodName", "두부").param("storageType", "FRIDGE")
                .param("quantityAmount", "2").param("quantityUnit", "모").param("memo", "남겨둘 메모");
    }
    private long legacy() {
        return jdbc.queryForObject("WITH m AS (INSERT INTO food_master(user_id,food_name,category) VALUES(1,'기존 두부','이전 분류') RETURNING master_id) "
                + "INSERT INTO food_item(user_id,master_id,storage_type,quantity_amount,quantity_unit,quantity_text) "
                + "VALUES(1,(SELECT master_id FROM m),'FRIDGE',2,'모','2모') RETURNING food_id", Long.class);
    }
    private void export(String name, MvcResult result) throws Exception {
        Path folder = Path.of("build/reports/category-ui"); Files.createDirectories(folder);
        Files.writeString(folder.resolve(name + ".html"), result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
    @SuppressWarnings("unchecked") private Map<String,String> errors(MvcResult result) {
        return (Map<String,String>) result.getModelAndView().getModel().get("categoryErrors");
    }

    @Test void missingAndMismatchedCategoryKeepInputsAndNeverWrite() throws Exception {
        var token = UUID.randomUUID();
        var missing = mvc.perform(create(token)).andExpect(status().isOk()).andReturn();
        assertThat(errors(missing)).containsKey("categoryMajorCode");
        assertThat(missing.getModelAndView().getModel()).containsEntry("registrationRequestId", token);
        var mismatch = mvc.perform(create(token).param("categoryMajorCode", "soy").param("categoryMinorCode", "grain_bread"))
                .andExpect(status().isOk()).andReturn();
        assertThat(errors(mismatch)).containsKey("categoryMinorCode");
        assertThat(mismatch.getModelAndView().getModel()).containsEntry("categoryMinorCode", "grain_bread");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_registration_receipt", Integer.class)).isZero();
        export("invalid", mismatch);
    }

    @Test void createRequiresCompleteCategoryAndReplaysWithoutSecondItem() throws Exception {
        var token = UUID.randomUUID();
        for (int i=0; i<2; i++) mvc.perform(create(token).param("categoryMajorCode", "soy").param("categoryMinorCode", "soy_tofu"))
                .andExpect(redirectedUrl("/inventory"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT category FROM food_master", String.class)).isEqualTo("콩·두부 › 두부");
        mvc.perform(create(token).param("categoryMajorCode", "kimchi")).andExpect(status().isOk()).andExpect(model().hasErrors());
    }

    @Test void quantityErrorPreservesChosenCodes() throws Exception {
        var request = post("/inventory").param("registrationRequestId", UUID.randomUUID().toString())
                .param("foodName", "두부").param("storageType", "FRIDGE").param("quantityAmount", "0").param("quantityUnit", "모")
                .param("categoryMajorCode", "soy").param("categoryMinorCode", "soy_tofu");
        var result = mvc.perform(request).andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("foodForm", "quantityAmount"))
                .andExpect(model().attribute("categoryMajorCode", "soy"))
                .andExpect(model().attribute("categoryMinorCode", "soy_tofu")).andReturn();
        export("quantity-error", result);
    }

    @Test void refreshWorksWithoutJavascriptAndDoesNotConsumeToken() throws Exception {
        var token = UUID.randomUUID();
        mvc.perform(create(token).param("categoryAction", "refresh").param("categoryMajorCode", "soy")
                .param("categoryMinorCode", "soy_soft_tofu"))
                .andExpect(model().attribute("categoryExample", "예) 순두부, 연두부 등"));
        mvc.perform(create(token).param("categoryAction", "refresh").param("categoryMajorCode", "egg")
                .param("categoryMinorCode", "egg_chicken"))
                .andExpect(model().attribute("categoryExample", "예) 달걀, 생달걀, 깐 달걀 등"));
        var result = mvc.perform(create(token).param("categoryAction", "refresh")
                .param("categoryMajorCode", "soy").param("categoryMinorCode", "grain_bread"))
                .andExpect(status().isOk()).andExpect(model().attribute("categoryMinorCode", ""))
                .andExpect(model().attribute("registrationRequestId", token)).andReturn();
        assertThat(((FoodCreateForm)result.getModelAndView().getModel().get("foodForm")).memo()).isEqualTo("남겨둘 메모");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_registration_receipt", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_master", Integer.class)).isZero();
        export("refresh", result);
        mvc.perform(create(token).param("categoryMajorCode", "soy").param("categoryMinorCode", "soy_tofu"))
                .andExpect(status().is3xxRedirection());
    }

    @Test void additionalPurchaseCompletesLegacyGroupAndInheritsAfterward() throws Exception {
        long id = legacy(), master = masters.masterId(id);
        var token = UUID.randomUUID();
        mvc.perform(create(token).param("registrationMode", "existing").param("masterId", ""+master).param("masterVersion", "0"))
                .andExpect(status().isOk());
        mvc.perform(create(token).param("registrationMode", "existing").param("masterId", ""+master).param("masterVersion", "0")
                .param("categoryMajorCode", "soy").param("categoryMinorCode", "soy_tofu"))
                .andExpect(redirectedUrl("/foods/"+master));
        assertThat(inventory.findById(id).category()).isEqualTo("콩·두부 › 두부");
        mvc.perform(create(UUID.randomUUID()).param("registrationMode", "existing").param("masterId", ""+master)
                .param("masterVersion", ""+masters.find(master).versionNo()).param("categoryMajorCode", "forged"))
                .andExpect(redirectedUrl("/foods/"+master));
        assertThat(masters.items(master)).hasSize(3);
        export("inherited", mvc.perform(get("/inventory/new").param("masterId", ""+master)).andReturn());
    }

    @Test void completedAdditionalRequestSurvivesRemovedTarget() throws Exception {
        long master = masters.masterId(legacy()); var token = UUID.randomUUID();
        mvc.perform(create(token).param("registrationMode", "existing").param("masterId", ""+master).param("masterVersion", "0")
                .param("categoryMajorCode", "kimchi")).andExpect(status().is3xxRedirection());
        jdbc.update("DELETE FROM food_history"); jdbc.update("DELETE FROM food_item"); jdbc.update("DELETE FROM food_master");
        mvc.perform(create(token).param("registrationMode", "existing").param("masterId", ""+master).param("masterVersion", "0")
                .param("categoryMajorCode", "kimchi")).andExpect(redirectedUrl("/inventory"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item", Integer.class)).isZero();
    }

    @Test void oldCompletedRequestCanReplayButFreshUnclassifiedRequestCannot() throws Exception {
        var token = UUID.randomUUID();
        var result = mvc.perform(create(token).param("categoryAction", "refresh")).andReturn();
        var form = (FoodCreateForm)result.getModelAndView().getModel().get("foodForm");
        registrations.create(form, token);
        mvc.perform(create(token)).andExpect(redirectedUrl("/inventory"));
        mvc.perform(create(UUID.randomUUID())).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item", Integer.class)).isEqualTo(1);
    }

    @Test void editRefreshPreservesVersionAndInputsAndRetiredSelectionRemainsAvailable() throws Exception {
        var token = UUID.randomUUID();
        mvc.perform(create(token).param("categoryMajorCode", "soy").param("categoryMinorCode", "soy_tofu"));
        var item = inventory.findActive().getFirst();
        jdbc.update("UPDATE food_category_minor SET active=false WHERE code='soy_tofu'");
        var page = mvc.perform(get("/inventory/"+item.foodId()+"/edit")).andExpect(status().isOk()).andReturn();
        assertThat(page.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("value=\"soy_tofu\" selected");
        export("edit", page);
        var result = mvc.perform(post("/inventory/"+item.foodId()+"/edit").param("expectedUpdatedAt", item.updatedAt().toString())
                .param("categoryAction", "refresh").param("categoryMajorCode", "soy").param("foodName", "수정 초안"))
                .andExpect(status().isOk()).andExpect(model().attribute("expectedUpdatedAt", item.updatedAt())).andReturn();
        assertThat(((FoodCreateForm)result.getModelAndView().getModel().get("foodForm")).foodName()).isEqualTo("수정 초안");
        assertThat(inventory.findById(item.foodId())).isEqualTo(item);
        mvc.perform(post("/inventory/"+item.foodId()+"/edit").param("expectedUpdatedAt", item.updatedAt().toString())
                .param("foodName", item.foodName()).param("storageType", "FRIDGE").param("quantityAmount", "2").param("quantityUnit", "모")
                .param("categoryMajorCode", "soy").param("categoryMinorCode", "soy_tofu")).andExpect(status().is3xxRedirection());
    }

    @Test void newAndLegacyPagesExposeRequiredPickerInOriginalFieldPosition() throws Exception {
        long master = masters.masterId(legacy());
        var page = mvc.perform(get("/inventory/new")).andExpect(status().isOk()).andReturn();
        String html = page.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html.indexOf("id=\"categoryPicker\"")).isGreaterThan(html.indexOf("id=\"sourceMemo\""));
        assertThat(html).contains("id=\"categoryMajorCode\"", "name=\"categoryMajorCode\" required").doesNotContain("선택 해제");
        export("new", page);
        export("legacy", mvc.perform(get("/inventory/new").param("masterId", ""+master)).andExpect(status().isOk()).andReturn());
    }
}
